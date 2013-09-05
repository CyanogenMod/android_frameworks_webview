/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.webview.chromium;

import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Picture;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.http.SslCertificate;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.util.Base64;
import android.util.Log;
import android.view.HardwareCanvas;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.DownloadListener;
import android.webkit.FindActionModeCallback;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewProvider;
import android.widget.TextView;

import org.chromium.android_webview.AwBrowserContext;
import org.chromium.android_webview.AwContents;
import org.chromium.base.ThreadUtils;
import org.chromium.content.browser.LoadUrlParams;
import org.chromium.net.NetworkChangeNotifier;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

/**
 * This class is the delegate to which WebViewProxy forwards all API calls.
 *
 * Most of the actual functionality is implemented by AwContents (or ContentViewCore within
 * it). This class also contains WebView-specific APIs that require the creation of other
 * adapters (otherwise org.chromium.content would depend on the webview.chromium package)
 * and a small set of no-op deprecated APIs.
 */
class WebViewChromium implements WebViewProvider,
          WebViewProvider.ScrollDelegate, WebViewProvider.ViewDelegate {

    private static final String TAG = WebViewChromium.class.getSimpleName();

    // The WebView that this WebViewChromium is the provider for.
    WebView mWebView;
    // Lets us access protected View-derived methods on the WebView instance we're backing.
    WebView.PrivateAccess mWebViewPrivate;
    // The client adapter class.
    private WebViewContentsClientAdapter mContentsClientAdapter;

    // Variables for functionality provided by this adapter ---------------------------------------
    // WebSettings adapter, lazily initialized in the getter
    private WebSettings mWebSettings;
    // The WebView wrapper for ContentViewCore and required browser compontents.
    private AwContents mAwContents;
    // Non-null if this webview is using the GL accelerated draw path.
    private DrawGLFunctor mGLfunctor;

    private AwBrowserContext mBrowserContext;

    private final WebView.HitTestResult mHitTestResult;

    private final int mAppTargetSdkVersion;

    public WebViewChromium(WebView webView, WebView.PrivateAccess webViewPrivate,
            AwBrowserContext browserContext) {
        checkThread();
        mWebView = webView;
        mWebViewPrivate = webViewPrivate;
        mHitTestResult = new WebView.HitTestResult();
        mBrowserContext = browserContext;
        mAppTargetSdkVersion = mWebView.getContext().getApplicationInfo().targetSdkVersion;
    }

    static void completeWindowCreation(WebView parent, WebView child) {
        AwContents parentContents = ((WebViewChromium) parent.getWebViewProvider()).mAwContents;
        AwContents childContents =
                child == null ? null : ((WebViewChromium) child.getWebViewProvider()).mAwContents;
        parentContents.supplyContentsForPopup(childContents);
    }

    // WebViewProvider methods --------------------------------------------------------------------

    @Override
    public void init(Map<String, Object> javaScriptInterfaces, boolean privateBrowsing) {
        // BUG=6790250 |javaScriptInterfaces| was only ever used by the obsolete DumpRenderTree
        // so is ignored. TODO: remove it from WebViewProvider.
        final boolean isAccessFromFileURLsGrantedByDefault =
                 mAppTargetSdkVersion < Build.VERSION_CODES.JELLY_BEAN;
        mContentsClientAdapter = new WebViewContentsClientAdapter(mWebView);
        mAwContents = new AwContents(mBrowserContext, mWebView, new InternalAccessAdapter(),
                mContentsClientAdapter, isAccessFromFileURLsGrantedByDefault);

        if (privateBrowsing) {
            final String msg = "Private browsing is not supported in WebView.";
            if (mAppTargetSdkVersion >= Build.VERSION_CODES.KEY_LIME_PIE) {
                throw new IllegalArgumentException(msg);
            } else {
                Log.w(TAG, msg);
                // Intentionally irreversibly disable the webview instance, so that private
                // user data cannot leak through misuse of a non-privateBrowing WebView instance.
                // Can't just null out mAwContents as we never null-check it before use.
                mAwContents.destroy();
                TextView warningLabel = new TextView(mWebView.getContext());
                warningLabel.setText(mWebView.getContext().getString(
                        com.android.internal.R.string.webviewchromium_private_browsing_warning));
                mWebView.addView(warningLabel);
            }
        }

    }

    private RuntimeException createThreadException() {
        return new IllegalStateException("Calling View methods on another thread than the UI " +
                "thread. PLEASE FILE A BUG! go/klp-webview-bug");
    }

    //  Intentionally not static, as no need to check thread on static methods
    private void checkThread() {
        if (!ThreadUtils.runningOnUiThread()) {
            final RuntimeException threadViolation = createThreadException();
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    throw threadViolation;
                }
            });
            throw createThreadException();
        }
    }

    @Override
    public void setHorizontalScrollbarOverlay(boolean overlay) {
        checkThread();
        mAwContents.setHorizontalScrollbarOverlay(overlay);
    }

    @Override
    public void setVerticalScrollbarOverlay(boolean overlay) {
        checkThread();
        mAwContents.setVerticalScrollbarOverlay(overlay);
    }

    @Override
    public boolean overlayHorizontalScrollbar() {
        checkThread();
        return mAwContents.overlayHorizontalScrollbar();
    }

    @Override
    public boolean overlayVerticalScrollbar() {
        checkThread();
        return mAwContents.overlayVerticalScrollbar();
    }

    @Override
    public int getVisibleTitleHeight() {
        // This is deprecated in WebView and should always return 0.
        return 0;
    }

    @Override
    public SslCertificate getCertificate() {
        checkThread();
        return mAwContents.getCertificate();
    }

    @Override
    public void setCertificate(SslCertificate certificate) {
        checkThread();
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void savePassword(String host, String username, String password) {
        // This is a deprecated API: intentional no-op.
    }

    @Override
    public void setHttpAuthUsernamePassword(String host, String realm, String username,
                                            String password) {
        checkThread();
        mAwContents.setHttpAuthUsernamePassword(host, realm, username, password);
    }

    @Override
    public String[] getHttpAuthUsernamePassword(String host, String realm) {
        checkThread();
        return mAwContents.getHttpAuthUsernamePassword(host, realm);
    }

    @Override
    public void destroy() {
        checkThread();
        mAwContents.destroy();
        if (mGLfunctor != null) {
            mGLfunctor.destroy();
            mGLfunctor = null;
        }
    }

    @Override
    public void setNetworkAvailable(boolean networkUp) {
        checkThread();
        // Note that this purely toggles the JS navigator.online property.
        // It does not in affect chromium or network stack state in any way.
        mAwContents.setNetworkAvailable(networkUp);
    }

    @Override
    public WebBackForwardList saveState(Bundle outState) {
        checkThread();
        if (outState == null) return null;
        if (!mAwContents.saveState(outState)) return null;
        return copyBackForwardList();
    }

    @Override
    public boolean savePicture(Bundle b, File dest) {
        // Intentional no-op: hidden method on WebView.
        return false;
    }

    @Override
    public boolean restorePicture(Bundle b, File src) {
        // Intentional no-op: hidden method on WebView.
        return false;
    }

    @Override
    public WebBackForwardList restoreState(Bundle inState) {
        checkThread();
        if (inState == null) return null;
        if (!mAwContents.restoreState(inState)) return null;
        return copyBackForwardList();
    }

    @Override
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        // TODO: We may actually want to do some sanity checks here (like filter about://chrome).

        // For backwards compatibility, apps targeting less than K will have JS URLs evaluated
        // directly and any result of the evaluation will not replace the current page content.
        // Matching Chrome behavior more closely; apps targetting >= K that load a JS URL will
        // have the result of that URL replace the content of the current page.
        final String JAVASCRIPT_SCHEME = "javascript:";
        if (mAppTargetSdkVersion < Build.VERSION_CODES.KEY_LIME_PIE &&
                url.startsWith(JAVASCRIPT_SCHEME)) {
            mAwContents.evaluateJavaScriptEvenIfNotYetNavigated(
                    url.substring(JAVASCRIPT_SCHEME.length()));
            return;
        }

        LoadUrlParams params = new LoadUrlParams(url);
        if (additionalHttpHeaders != null) params.setExtraHeaders(additionalHttpHeaders);
        loadUrlOnUiThread(params);
    }

    @Override
    public void loadUrl(String url) {
        loadUrl(url, null);
    }

    @Override
    public void postUrl(String url, byte[] postData) {
        LoadUrlParams params = LoadUrlParams.createLoadHttpPostParams(url, postData);
        Map<String,String> headers = new HashMap<String,String>();
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        params.setExtraHeaders(headers);
        loadUrlOnUiThread(params);
    }

    private static boolean isBase64Encoded(String encoding) {
        return "base64".equals(encoding);
    }

    @Override
    public void loadData(String data, String mimeType, String encoding) {
        loadUrlOnUiThread(LoadUrlParams.createLoadDataParams(
                data, mimeType, isBase64Encoded(encoding)));
    }

    @Override
    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding,
            String historyUrl) {
        LoadUrlParams loadUrlParams;

        if (baseUrl != null && baseUrl.startsWith("data:")) {
            // For backwards compatibility with WebViewClassic, we use the value of |encoding|
            // as the charset, as long as it's not "base64".
            boolean isBase64 = isBase64Encoded(encoding);
            loadUrlParams = LoadUrlParams.createLoadDataParamsWithBaseUrl(
                    data, mimeType, isBase64, baseUrl, historyUrl, isBase64 ? null : encoding);
        } else {
            if (baseUrl == null || baseUrl.length() == 0) baseUrl = "about:blank";
            // When loading data with a non-data: base URL, the classic WebView would effectively
            // "dump" that string of data into the WebView without going through regular URL
            // loading steps such as decoding URL-encoded entities. We achieve this same behavior by
            // base64 encoding the data that is passed here and then loading that as a data: URL.
            try {
                loadUrlParams = LoadUrlParams.createLoadDataParamsWithBaseUrl(
                        Base64.encodeToString(data.getBytes("utf-8"), Base64.DEFAULT), mimeType,
                        true, baseUrl, historyUrl, "utf-8");
            } catch (java.io.UnsupportedEncodingException e) {
                Log.wtf(TAG, "Unable to load data string " + data, e);
                return;
            }
        }
        loadUrlOnUiThread(loadUrlParams);
    }

    private void loadUrlOnUiThread(final LoadUrlParams loadUrlParams) {
        if (ThreadUtils.runningOnUiThread()) {
            mAwContents.loadUrl(loadUrlParams);
        } else {
            // Disallowed in WebView API for apps targetting a new SDK
            assert mAppTargetSdkVersion < Build.VERSION_CODES.JELLY_BEAN_MR2;
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAwContents.loadUrl(loadUrlParams);
                }
            });
        }
    }

    public void evaluateJavaScript(String script, ValueCallback<String> resultCallback) {
        checkThread();
        mAwContents.evaluateJavaScript(script, resultCallback);
    }

    @Override
    public void saveWebArchive(String filename) {
        checkThread();
        saveWebArchive(filename, false, null);
    }

    @Override
    public void saveWebArchive(String basename, boolean autoname, ValueCallback<String> callback) {
        checkThread();
        mAwContents.saveWebArchive(basename, autoname, callback);
    }

    @Override
    public void stopLoading() {
        checkThread();
        mAwContents.stopLoading();
    }

    @Override
    public void reload() {
        checkThread();
        mAwContents.reload();
    }

    @Override
    public boolean canGoBack() {
        checkThread();
        return mAwContents.canGoBack();
    }

    @Override
    public void goBack() {
        checkThread();
        mAwContents.goBack();
    }

    @Override
    public boolean canGoForward() {
        checkThread();
        return mAwContents.canGoForward();
    }

    @Override
    public void goForward() {
        checkThread();
        mAwContents.goForward();
    }

    @Override
    public boolean canGoBackOrForward(int steps) {
        checkThread();
        return mAwContents.canGoBackOrForward(steps);
    }

    @Override
    public void goBackOrForward(int steps) {
        checkThread();
        mAwContents.goBackOrForward(steps);
    }

    @Override
    public boolean isPrivateBrowsingEnabled() {
        // Not supported in this WebView implementation.
        return false;
    }

    @Override
    public boolean pageUp(boolean top) {
        checkThread();
        return mAwContents.pageUp(top);
    }

    @Override
    public boolean pageDown(boolean bottom) {
        checkThread();
        return mAwContents.pageDown(bottom);
    }

    @Override
    public void clearView() {
        checkThread();
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public Picture capturePicture() {
        checkThread();
        return mAwContents.capturePicture();
    }

    @Override
    public void exportToPdf(OutputStream stream, int width, int height,
            ValueCallback<Boolean> resultCallback) {
        checkThread();
        // TODO(sgurun) enable this only after upstream part lands
        //mAwContents.exportToPdf(stream, width, height, resultCallback);
    }

    @Override
    public float getScale() {
        checkThread();
        return mAwContents.getScale();
    }

    @Override
    public void setInitialScale(int scaleInPercent) {
        checkThread();
        mAwContents.getSettings().setInitialPageScale(scaleInPercent);
    }

    @Override
    public void invokeZoomPicker() {
        checkThread();
        mAwContents.invokeZoomPicker();
    }

    @Override
    public WebView.HitTestResult getHitTestResult() {
        checkThread();
        AwContents.HitTestData data = mAwContents.getLastHitTestResult();
        mHitTestResult.setType(data.hitTestResultType);
        mHitTestResult.setExtra(data.hitTestResultExtraData);
        return mHitTestResult;
    }

    @Override
    public void requestFocusNodeHref(Message hrefMsg) {
        checkThread();
        mAwContents.requestFocusNodeHref(hrefMsg);
    }

    @Override
    public void requestImageRef(Message msg) {
        checkThread();
        mAwContents.requestImageRef(msg);
    }

    @Override
    public String getUrl() {
        checkThread();
        String url =  mAwContents.getUrl();
        if (url == null || url.trim().isEmpty()) return null;
        return url;
    }

    @Override
    public String getOriginalUrl() {
        checkThread();
        String url =  mAwContents.getOriginalUrl();
        if (url == null || url.trim().isEmpty()) return null;
        return url;
    }

    @Override
    public String getTitle() {
        checkThread();
        return mAwContents.getTitle();
    }

    @Override
    public Bitmap getFavicon() {
        checkThread();
        return mAwContents.getFavicon();
    }

    @Override
    public String getTouchIconUrl() {
        // Intentional no-op: hidden method on WebView.
        return null;
    }

    @Override
    public int getProgress() {
        // No checkThread() because the value is cached java side (workaround for b/10533304).
        return mAwContents.getMostRecentProgress();
    }

    @Override
    public int getContentHeight() {
        // No checkThread() as it is mostly thread safe (workaround for b/10594869).
        return mAwContents.getContentHeightCss();
    }

    @Override
    public int getContentWidth() {
        checkThread();
        return mAwContents.getContentWidthCss();
    }

    @Override
    public void pauseTimers() {
        checkThread();
        mAwContents.pauseTimers();
    }

    @Override
    public void resumeTimers() {
        checkThread();
        mAwContents.resumeTimers();
    }

    @Override
    public void onPause() {
        checkThread();
        mAwContents.onPause();
    }

    @Override
    public void onResume() {
        checkThread();
        mAwContents.onResume();
    }

    @Override
    public boolean isPaused() {
        checkThread();
        return mAwContents.isPaused();
    }

    @Override
    public void freeMemory() {
        checkThread();
        // Intentional no-op. Memory is managed automatically by Chromium.
    }

    @Override
    public void clearCache(boolean includeDiskFiles) {
        checkThread();
        mAwContents.clearCache(includeDiskFiles);
    }

    /**
     * This is a poorly named method, but we keep it for historical reasons.
     */
    @Override
    public void clearFormData() {
        checkThread();
        mAwContents.hideAutofillPopup();
    }

    @Override
    public void clearHistory() {
        checkThread();
        mAwContents.clearHistory();
    }

    @Override
    public void clearSslPreferences() {
        checkThread();
        mAwContents.clearSslPreferences();
    }

    @Override
    public WebBackForwardList copyBackForwardList() {
        checkThread();
        return new WebBackForwardListChromium(
                mAwContents.getNavigationHistory());
    }

    @Override
    public void setFindListener(WebView.FindListener listener) {
        checkThread();
        mContentsClientAdapter.setFindListener(listener);
    }

    @Override
    public void findNext(boolean forwards) {
        checkThread();
        mAwContents.findNext(forwards);
    }

    @Override
    public int findAll(String searchString) {
        checkThread();
        mAwContents.findAllAsync(searchString);
        return 0;
    }

    @Override
    public void findAllAsync(String searchString) {
        checkThread();
        mAwContents.findAllAsync(searchString);
    }

    @Override
    public boolean showFindDialog(String text, boolean showIme) {
        checkThread();
        if (mWebView.getParent() == null) {
            return false;
        }

        FindActionModeCallback findAction = new FindActionModeCallback(mWebView.getContext());
        if (findAction == null) {
            return false;
        }

        mWebView.startActionMode(findAction);
        findAction.setWebView(mWebView);
        if (showIme) {
            findAction.showSoftInput();
        }

        if (text != null) {
            findAction.setText(text);
            findAction.findAll();
        }

        return true;
    }

    @Override
    public void notifyFindDialogDismissed() {
        checkThread();
        clearMatches();
    }

    @Override
    public void clearMatches() {
        checkThread();
        mAwContents.clearMatches();
    }

    @Override
    public void documentHasImages(Message response) {
        checkThread();
        mAwContents.documentHasImages(response);
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        checkThread();
        mContentsClientAdapter.setWebViewClient(client);
    }

    @Override
    public void setDownloadListener(DownloadListener listener) {
        checkThread();
        mContentsClientAdapter.setDownloadListener(listener);
    }

    @Override
    public void setWebChromeClient(WebChromeClient client) {
        checkThread();
        mContentsClientAdapter.setWebChromeClient(client);
    }

    @Override
    public void setPictureListener(WebView.PictureListener listener) {
        checkThread();
        mContentsClientAdapter.setPictureListener(listener);
        mAwContents.enableOnNewPicture(listener != null,
                mAppTargetSdkVersion >= Build.VERSION_CODES.JELLY_BEAN_MR2);
    }

    @Override
    public void addJavascriptInterface(Object obj, String interfaceName) {
        checkThread();
        Class<? extends Annotation> requiredAnnotation = null;
        if (mAppTargetSdkVersion >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
           requiredAnnotation = JavascriptInterface.class;
        }
        mAwContents.addPossiblyUnsafeJavascriptInterface(obj, interfaceName, requiredAnnotation);
    }

    @Override
    public void removeJavascriptInterface(String interfaceName) {
        checkThread();
        mAwContents.removeJavascriptInterface(interfaceName);
    }

    @Override
    public WebSettings getSettings() {
        checkThread();
        if (mWebSettings == null) {
            mWebSettings = new ContentSettingsAdapter(mAwContents.getSettings());
        }
        return mWebSettings;
    }

    @Override
    public void setMapTrackballToArrowKeys(boolean setMap) {
        checkThread();
        // This is a deprecated API: intentional no-op.
    }

    @Override
    public void flingScroll(int vx, int vy) {
        checkThread();
        mAwContents.flingScroll(vx, vy);
    }

    @Override
    public View getZoomControls() {
        checkThread();
        // This was deprecated in 2009 and hidden in JB MR1, so just provide the minimum needed
        // to stop very out-dated applications from crashing.
        Log.w(TAG, "WebView doesn't support getZoomControls");
        return mAwContents.getSettings().supportZoom() ? new View(mWebView.getContext()) : null;
    }

    @Override
    public boolean canZoomIn() {
        checkThread();
        return mAwContents.canZoomIn();
    }

    @Override
    public boolean canZoomOut() {
        checkThread();
        return mAwContents.canZoomOut();
    }

    @Override
    public boolean zoomIn() {
        checkThread();
        return mAwContents.zoomIn();
    }

    @Override
    public boolean zoomOut() {
        checkThread();
        return mAwContents.zoomOut();
    }

    @Override
    public void dumpViewHierarchyWithProperties(BufferedWriter out, int level) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public View findHierarchyView(String className, int hashCode) {
        UnimplementedWebViewApi.invoke();
        return null;
    }

    // WebViewProvider glue methods ---------------------------------------------------------------

    @Override
    // This needs to be kept thread safe!
    public WebViewProvider.ViewDelegate getViewDelegate() {
        return this;
    }

    @Override
    public WebViewProvider.ScrollDelegate getScrollDelegate() {
        checkThread();
        return this;
    }


    // WebViewProvider.ViewDelegate implementation ------------------------------------------------

    // TODO: remove from WebViewProvider and use default implementation from
    // ViewGroup.
    // @Override
    public boolean shouldDelayChildPressedState() {
        checkThread();
        return true;
    }

//    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        checkThread();
        return mAwContents.getAccessibilityNodeProvider();
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        checkThread();
        mAwContents.onInitializeAccessibilityNodeInfo(info);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        checkThread();
        mAwContents.onInitializeAccessibilityEvent(event);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        checkThread();
        if (mAwContents.supportsAccessibilityAction(action)) {
            return mAwContents.performAccessibilityAction(action, arguments);
        }
        return mWebViewPrivate.super_performAccessibilityAction(action, arguments);
    }

    @Override
    public void setOverScrollMode(int mode) {
        checkThread();
        // This gets called from the android.view.View c'tor that WebView inherits from. This
        // causes the method to be called when mAwContents == null.
        // It's safe to ignore these calls however since AwContents will read the current value of
        // this setting when it's created.
        if (mAwContents != null) {
            mAwContents.setOverScrollMode(mode);
        }
    }

    @Override
    public void setScrollBarStyle(int style) {
        checkThread();
        mAwContents.setScrollBarStyle(style);
    }

    @Override
    public void onDrawVerticalScrollBar(Canvas canvas, Drawable scrollBar,
                                        int l, int t, int r, int b) {
        checkThread();
        // WebViewClassic was overriding this method to handle rubberband over-scroll. Since
        // WebViewChromium doesn't support that the vanilla implementation of this method can be
        // used.
        mWebViewPrivate.super_onDrawVerticalScrollBar(canvas, scrollBar, l, t, r, b);
    }

    @Override
    public void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        checkThread();
        mAwContents.onContainerViewOverScrolled(scrollX, scrollY, clampedX, clampedY);
    }

    @Override
    public void onWindowVisibilityChanged(int visibility) {
        checkThread();
        mAwContents.onWindowVisibilityChanged(visibility);
    }

    @Override
    public void onDraw(Canvas canvas) {
        checkThread();
        mAwContents.onDraw(canvas);
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams layoutParams) {
        checkThread();
        // TODO: This is the minimum implementation for HTMLViewer
        // bringup. Likely will need to go up to ContentViewCore for
        // a complete implementation.
        mWebViewPrivate.super_setLayoutParams(layoutParams);
    }

    @Override
    public boolean performLongClick() {
        checkThread();
        return mWebViewPrivate.super_performLongClick();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        checkThread();
        mAwContents.onConfigurationChanged(newConfig);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        checkThread();
        return mAwContents.onCreateInputConnection(outAttrs);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        checkThread();
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        checkThread();
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        checkThread();
        return mAwContents.onKeyUp(keyCode, event);
    }

    @Override
    public void onAttachedToWindow() {
        checkThread();
        mAwContents.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        checkThread();
        mAwContents.onDetachedFromWindow();
        if (mGLfunctor != null) {
            mGLfunctor.detach();
        }
    }

    @Override
    public void onVisibilityChanged(View changedView, int visibility) {
        checkThread();
        // The AwContents will find out the container view visibility before the first draw so we
        // can safely ignore onVisibilityChanged callbacks that happen before init().
        if (mAwContents != null) {
            mAwContents.onVisibilityChanged(changedView, visibility);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        checkThread();
        mAwContents.onWindowFocusChanged(hasWindowFocus);
    }

    @Override
    public void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        checkThread();
        mAwContents.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    @Override
    public boolean setFrame(int left, int top, int right, int bottom) {
        // TODO(joth): This is the minimum implementation for initial
        // bringup. Likely will need to go up to AwContents for a complete
        // implementation, e.g. setting the compositor visible region (to
        // avoid painting tiles that are offscreen due to the view's position).
        checkThread();
        return mWebViewPrivate.super_setFrame(left, top, right, bottom);
    }

    @Override
    public void onSizeChanged(int w, int h, int ow, int oh) {
        checkThread();
        mAwContents.onSizeChanged(w, h, ow, oh);
    }

    @Override
    public void onScrollChanged(int l, int t, int oldl, int oldt) {
        checkThread();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        checkThread();
        return mAwContents.dispatchKeyEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        checkThread();
        return mAwContents.onTouchEvent(ev);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        checkThread();
        return mAwContents.onHoverEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        checkThread();
        return mAwContents.onGenericMotionEvent(event);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        checkThread();
        // Trackball event not handled, which eventually gets converted to DPAD keyevents
        return false;
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        checkThread();
        mAwContents.requestFocus();
        return mWebViewPrivate.super_requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        checkThread();
        mAwContents.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
        checkThread();
        return mAwContents.requestChildRectangleOnScreen(child, rect, immediate);
    }

    @Override
    public void setBackgroundColor(final int color) {
        if (ThreadUtils.runningOnUiThread()) {
            mAwContents.setBackgroundColor(color);
        } else {
            // Disallowed in WebView API for apps targetting a new SDK
            assert mAppTargetSdkVersion < Build.VERSION_CODES.JELLY_BEAN_MR2;
            ThreadUtils.postOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAwContents.setBackgroundColor(color);
                }
            });
        }
    }

    @Override
    public void setLayerType(int layerType, Paint paint) {
        checkThread();
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void preDispatchDraw(Canvas canvas) {
        checkThread();
        // TODO(leandrogracia): remove this method from WebViewProvider if we think
        // we won't need it again.
    }

    // WebViewProvider.ScrollDelegate implementation ----------------------------------------------

    @Override
    public int computeHorizontalScrollRange() {
        checkThread();
        return mAwContents.computeHorizontalScrollRange();
    }

    @Override
    public int computeHorizontalScrollOffset() {
        checkThread();
        return mAwContents.computeHorizontalScrollOffset();
    }

    @Override
    public int computeVerticalScrollRange() {
        checkThread();
        return mAwContents.computeVerticalScrollRange();
    }

    @Override
    public int computeVerticalScrollOffset() {
        checkThread();
        return mAwContents.computeVerticalScrollOffset();
    }

    @Override
    public int computeVerticalScrollExtent() {
        checkThread();
        return mAwContents.computeVerticalScrollExtent();
    }

    @Override
    public void computeScroll() {
        checkThread();
        mAwContents.computeScroll();
    }

    // AwContents.InternalAccessDelegate implementation --------------------------------------
    private class InternalAccessAdapter implements AwContents.InternalAccessDelegate {
        @Override
        public boolean drawChild(Canvas arg0, View arg1, long arg2) {
            UnimplementedWebViewApi.invoke();
            return false;
        }

        @Override
        public boolean super_onKeyUp(int arg0, KeyEvent arg1) {
            UnimplementedWebViewApi.invoke();
            return false;
        }

        @Override
        public boolean super_dispatchKeyEventPreIme(KeyEvent arg0) {
            UnimplementedWebViewApi.invoke();
            return false;
        }

        @Override
        public boolean super_dispatchKeyEvent(KeyEvent event) {
            return mWebViewPrivate.super_dispatchKeyEvent(event);
        }

        @Override
        public boolean super_onGenericMotionEvent(MotionEvent arg0) {
            UnimplementedWebViewApi.invoke();
            return false;
        }

        @Override
        public void super_onConfigurationChanged(Configuration arg0) {
            UnimplementedWebViewApi.invoke();
        }

        @Override
        public int super_getScrollBarStyle() {
            return mWebViewPrivate.super_getScrollBarStyle();
        }

        @Override
        public boolean awakenScrollBars() {
            mWebViewPrivate.awakenScrollBars(0);
            // TODO: modify the WebView.PrivateAccess to provide a return value.
            return true;
        }

        @Override
        public boolean super_awakenScrollBars(int arg0, boolean arg1) {
            // TODO: need method on WebView.PrivateAccess?
            UnimplementedWebViewApi.invoke();
            return false;
        }

        @Override
        public void onScrollChanged(int l, int t, int oldl, int oldt) {
            mWebViewPrivate.setScrollXRaw(l);
            mWebViewPrivate.setScrollYRaw(t);
            mWebViewPrivate.onScrollChanged(l, t, oldl, oldt);
        }

        @Override
        public void overScrollBy(int deltaX, int deltaY,
            int scrollX, int scrollY,
            int scrollRangeX, int scrollRangeY,
            int maxOverScrollX, int maxOverScrollY,
            boolean isTouchEvent) {
            mWebViewPrivate.overScrollBy(deltaX, deltaY, scrollX, scrollY,
                    scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent);
        }

        @Override
        public void super_scrollTo(int scrollX, int scrollY) {
            mWebViewPrivate.super_scrollTo(scrollX, scrollY);
        }

        @Override
        public void setMeasuredDimension(int measuredWidth, int measuredHeight) {
            mWebViewPrivate.setMeasuredDimension(measuredWidth, measuredHeight);
        }

        @Override
        public boolean requestDrawGL(Canvas canvas) {
            if (mGLfunctor == null) {
                mGLfunctor = new DrawGLFunctor(mAwContents.getAwDrawGLViewContext());
            }
            return mGLfunctor.requestDrawGL((HardwareCanvas)canvas, mWebView.getViewRootImpl());
        }
    }
}
