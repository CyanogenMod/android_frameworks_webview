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
import android.util.Log;
import android.view.HardwareCanvas;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
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

import org.chromium.android_webview.AwBrowserContext;
import org.chromium.android_webview.AwContents;
import org.chromium.content.browser.LoadUrlParams;
import org.chromium.net.NetworkChangeNotifier;

import java.io.BufferedWriter;
import java.io.File;
import java.lang.annotation.Annotation;
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

    public WebViewChromium(WebView webView, WebView.PrivateAccess webViewPrivate,
            AwBrowserContext browserContext) {
        mWebView = webView;
        mWebViewPrivate = webViewPrivate;
        mHitTestResult = new WebView.HitTestResult();
        mBrowserContext = browserContext;
    }

    static void completeWindowCreation(WebView parent, WebView child) {
        AwContents parentContents = ((WebViewChromium) parent.getWebViewProvider()).mAwContents;
        AwContents childContents = ((WebViewChromium) child.getWebViewProvider()).mAwContents;
        parentContents.supplyContentsForPopup(childContents);
    }

    // WebViewProvider methods --------------------------------------------------------------------

    @Override
    public void init(Map<String, Object> javaScriptInterfaces, boolean privateBrowsing) {
        // TODO: BUG=6790250 javaScriptInterfaces were only ever used by DumpRenderTree and should
        // probably be implemented as a hidden hook in WebViewClassic.
        if (privateBrowsing) {
            throw new IllegalArgumentException(
                    "Private browsing is not supported in this version of the WebView.");
        }

        final boolean isAccessFromFileURLsGrantedByDefault =
                mWebView.getContext().getApplicationInfo().targetSdkVersion <
                Build.VERSION_CODES.JELLY_BEAN;
        mContentsClientAdapter = new WebViewContentsClientAdapter(mWebView);
        mAwContents = new AwContents(mBrowserContext, mWebView, new InternalAccessAdapter(),
                mContentsClientAdapter, isAccessFromFileURLsGrantedByDefault);
    }

    @Override
    public void setHorizontalScrollbarOverlay(boolean overlay) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void setVerticalScrollbarOverlay(boolean overlay) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public boolean overlayHorizontalScrollbar() {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public boolean overlayVerticalScrollbar() {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public int getVisibleTitleHeight() {
        // This is deprecated in WebView and should always return 0.
        return 0;
    }

    @Override
    public SslCertificate getCertificate() {
        return mAwContents.getCertificate();
    }

    @Override
    public void setCertificate(SslCertificate certificate) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void savePassword(String host, String username, String password) {
        // This is a deprecated API: intentional no-op.
    }

    @Override
    public void setHttpAuthUsernamePassword(String host, String realm, String username,
                                            String password) {
        mAwContents.setHttpAuthUsernamePassword(host, realm, username, password);
    }

    @Override
    public String[] getHttpAuthUsernamePassword(String host, String realm) {
        return mAwContents.getHttpAuthUsernamePassword(host, realm);
    }

    @Override
    public void destroy() {
        mAwContents.destroy();
        if (mGLfunctor != null) {
            mGLfunctor.destroy();
            mGLfunctor = null;
        }
    }

    @Override
    public void setNetworkAvailable(boolean networkUp) {
        NetworkChangeNotifier.forceConnectivityState(networkUp);
    }

    @Override
    public WebBackForwardList saveState(Bundle outState) {
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
        if (inState == null) return null;
        if (!mAwContents.restoreState(inState)) return null;
        return copyBackForwardList();
    }

    @Override
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        // TODO: We may actually want to do some sanity checks here (like filter about://chrome).
        LoadUrlParams params = new LoadUrlParams(url);
        if (additionalHttpHeaders != null) params.setExtraHeaders(additionalHttpHeaders);
        mAwContents.loadUrl(params);
    }

    @Override
    public void loadUrl(String url) {
        loadUrl(url, null);
    }

    @Override
    public void postUrl(String url, byte[] postData) {
        mAwContents.loadUrl(LoadUrlParams.createLoadHttpPostParams(
                url, postData));
    }

    private static boolean isBase64Encoded(String encoding) {
        return "base64".equals(encoding);
    }

    @Override
    public void loadData(String data, String mimeType, String encoding) {
        mAwContents.loadUrl(LoadUrlParams.createLoadDataParams(
                  data, mimeType, isBase64Encoded(encoding)));
    }

    @Override
    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding,
                                    String historyUrl) {
        if (baseUrl == null || baseUrl.length() == 0) baseUrl = "about:blank";
        boolean isBase64 = isBase64Encoded(encoding);
        // For backwards compatibility with WebViewClassic, we use the value of |encoding|
        // as the charset, as long as it's not "base64".
        mAwContents.loadUrl(LoadUrlParams.createLoadDataParamsWithBaseUrl(
                data, mimeType, isBase64, baseUrl, historyUrl, isBase64 ? null : encoding));
    }

    @Override
    public void saveWebArchive(String filename) {
        saveWebArchive(filename, false, null);
    }

    @Override
    public void saveWebArchive(String basename, boolean autoname, ValueCallback<String> callback) {
        mAwContents.saveWebArchive(basename, autoname, callback);
    }

    @Override
    public void stopLoading() {
        mAwContents.stopLoading();
    }

    @Override
    public void reload() {
        mAwContents.reload();
    }

    @Override
    public boolean canGoBack() {
        return mAwContents.canGoBack();
    }

    @Override
    public void goBack() {
        mAwContents.goBack();
    }

    @Override
    public boolean canGoForward() {
        return mAwContents.canGoForward();
    }

    @Override
    public void goForward() {
        mAwContents.goForward();
    }

    @Override
    public boolean canGoBackOrForward(int steps) {
        return mAwContents.canGoBackOrForward(steps);
    }

    @Override
    public void goBackOrForward(int steps) {
        mAwContents.goBackOrForward(steps);
    }

    @Override
    public boolean isPrivateBrowsingEnabled() {
        // Not supported in this WebView implementation.
        return false;
    }

    @Override
    public boolean pageUp(boolean top) {
        return mAwContents.pageUp(top);
    }

    @Override
    public boolean pageDown(boolean bottom) {
        return mAwContents.pageDown(bottom);
    }

    @Override
    public void clearView() {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public Picture capturePicture() {
        return mAwContents.capturePicture();
    }

    @Override
    public float getScale() {
        return mAwContents.getScale();
    }

    @Override
    public void setInitialScale(int scaleInPercent) {
        mAwContents.getSettings().setInitialPageScale(scaleInPercent);
    }

    @Override
    public void invokeZoomPicker() {
        mAwContents.invokeZoomPicker();
    }

    @Override
    public WebView.HitTestResult getHitTestResult() {
        AwContents.HitTestData data = mAwContents.getLastHitTestResult();
        mHitTestResult.setType(data.hitTestResultType);
        mHitTestResult.setExtra(data.hitTestResultExtraData);
        return mHitTestResult;
    }

    @Override
    public void requestFocusNodeHref(Message hrefMsg) {
        mAwContents.requestFocusNodeHref(hrefMsg);
    }

    @Override
    public void requestImageRef(Message msg) {
        mAwContents.requestImageRef(msg);
    }

    @Override
    public String getUrl() {
        String url =  mAwContents.getUrl();
        if (url == null || url.trim().isEmpty()) return null;
        return url;
    }

    @Override
    public String getOriginalUrl() {
        String url =  mAwContents.getOriginalUrl();
        if (url == null || url.trim().isEmpty()) return null;
        return url;
    }

    @Override
    public String getTitle() {
        return mAwContents.getTitle();
    }

    @Override
    public Bitmap getFavicon() {
        return mAwContents.getFavicon();
    }

    @Override
    public String getTouchIconUrl() {
        // Intentional no-op: hidden method on WebView.
        return null;
    }

    @Override
    public int getProgress() {
        return mAwContents.getMostRecentProgress();
    }

    @Override
    public int getContentHeight() {
        return mAwContents.getContentHeightCss();
    }

    @Override
    public int getContentWidth() {
        return mAwContents.getContentWidthCss();
    }

    @Override
    public void pauseTimers() {
        mAwContents.pauseTimers();
    }

    @Override
    public void resumeTimers() {
        mAwContents.resumeTimers();
    }

    @Override
    public void onPause() {
        mAwContents.onPause();
    }

    @Override
    public void onResume() {
        mAwContents.onResume();
    }

    @Override
    public boolean isPaused() {
        return mAwContents.isPaused();
    }

    @Override
    public void freeMemory() {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void clearCache(boolean includeDiskFiles) {
        mAwContents.clearCache(includeDiskFiles);
    }

    @Override
    public void clearFormData() {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void clearHistory() {
        mAwContents.clearHistory();
    }

    @Override
    public void clearSslPreferences() {
        mAwContents.clearSslPreferences();
    }

    @Override
    public WebBackForwardList copyBackForwardList() {
        return new WebBackForwardListChromium(
                mAwContents.getNavigationHistory());
    }

    @Override
    public void setFindListener(WebView.FindListener listener) {
        mContentsClientAdapter.setFindListener(listener);
    }

    @Override
    public void findNext(boolean forwards) {
        mAwContents.findNext(forwards);
    }

    @Override
    public int findAll(String searchString) {
        return mAwContents.findAllSync(searchString);
    }

    @Override
    public void findAllAsync(String searchString) {
        mAwContents.findAllAsync(searchString);
    }

    @Override
    public boolean showFindDialog(String text, boolean showIme) {
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
        clearMatches();
    }

    @Override
    public void clearMatches() {
        mAwContents.clearMatches();
    }

    @Override
    public void documentHasImages(Message response) {
        mAwContents.documentHasImages(response);
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        mContentsClientAdapter.setWebViewClient(client);
    }

    @Override
    public void setDownloadListener(DownloadListener listener) {
        mContentsClientAdapter.setDownloadListener(listener);
    }

    @Override
    public void setWebChromeClient(WebChromeClient client) {
        mContentsClientAdapter.setWebChromeClient(client);
    }

    @Override
    public void setPictureListener(WebView.PictureListener listener) {
        mContentsClientAdapter.setPictureListener(listener);
        mAwContents.enableOnNewPicture(listener != null,
                mWebView.getContext().getApplicationInfo().targetSdkVersion >=
                Build.VERSION_CODES.JELLY_BEAN_MR2);
    }

    @Override
    public void addJavascriptInterface(Object obj, String interfaceName) {
        Class<? extends Annotation> requiredAnnotation = null;
        if (mWebView.getContext().getApplicationInfo().targetSdkVersion >=
                Build.VERSION_CODES.JELLY_BEAN_MR1) {
           requiredAnnotation = JavascriptInterface.class;
        }
        mAwContents.addPossiblyUnsafeJavascriptInterface(obj, interfaceName, requiredAnnotation);
    }

    @Override
    public void removeJavascriptInterface(String interfaceName) {
        mAwContents.removeJavascriptInterface(interfaceName);
    }

    @Override
    public WebSettings getSettings() {
        if (mWebSettings == null) {
            mWebSettings = new ContentSettingsAdapter(mAwContents.getContentSettings(),
                    mAwContents.getSettings());
        }
        return mWebSettings;
    }

    @Override
    public void setMapTrackballToArrowKeys(boolean setMap) {
        // This is a deprecated API: intentional no-op.
    }

    @Override
    public void flingScroll(int vx, int vy) {
        mAwContents.flingScroll(vx, vy);
    }

    @Override
    public View getZoomControls() {
        // This was deprecated in 2009 and hidden in JB MR1, so just provide the minimum needed
        // to stop very out-dated applications from crashing.
        Log.w(TAG, "WebView doesn't support getZoomControls");
        return mAwContents.getContentSettings().supportZoom() ?
            new View(mWebView.getContext()) : null;
    }

    @Override
    public boolean canZoomIn() {
        return mAwContents.canZoomIn();
    }

    @Override
    public boolean canZoomOut() {
        return mAwContents.canZoomOut();
    }

    @Override
    public boolean zoomIn() {
        return mAwContents.zoomIn();
    }

    @Override
    public boolean zoomOut() {
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
    public WebViewProvider.ViewDelegate getViewDelegate() {
        return this;
    }

    @Override
    public WebViewProvider.ScrollDelegate getScrollDelegate() {
        return this;
    }


    // WebViewProvider.ViewDelegate implementation ------------------------------------------------

    @Override
    public boolean shouldDelayChildPressedState() {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        mAwContents.onInitializeAccessibilityNodeInfo(info);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        mAwContents.onInitializeAccessibilityEvent(event);
    }

    // TODO: Update WebView to mimic ContentView implementation for the
    // real View#performAccessibilityAction(int, Bundle).  This method has different behavior.
    // See ContentViewCore#performAccessibilityAction(int, Bundle) for more details.
    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        return mAwContents.performAccessibilityAction(action, arguments);
    }

    @Override
    public void setOverScrollMode(int mode) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void setScrollBarStyle(int style) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void onDrawVerticalScrollBar(Canvas canvas, Drawable scrollBar,
                                        int l, int t, int r, int b) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void onWindowVisibilityChanged(int visibility) {
        mAwContents.onWindowVisibilityChanged(visibility);
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (canvas.isHardwareAccelerated() && mAwContents.onPrepareDrawGL(canvas)) {
            if (mGLfunctor == null) {
                mGLfunctor = new DrawGLFunctor(mAwContents.getAwDrawGLViewContext());
            }
            mGLfunctor.requestDrawGL((HardwareCanvas) canvas, mWebView.getViewRootImpl());
        } else {
            mAwContents.onDraw(canvas);
        }
    }

    @Override
    public void setLayoutParams(ViewGroup.LayoutParams layoutParams) {
        // TODO: This is the minimum implementation for HTMLViewer
        // bringup. Likely will need to go up to ContentViewCore for
        // a complete implementation.
        mWebViewPrivate.super_setLayoutParams(layoutParams);
    }

    @Override
    public boolean performLongClick() {
        return mWebViewPrivate.super_performLongClick();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        mAwContents.onConfigurationChanged(newConfig);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return mAwContents.onCreateInputConnection(outAttrs);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mAwContents.onKeyUp(keyCode, event);
    }

    @Override
    public void onAttachedToWindow() {
        mAwContents.onAttachedToWindow();
    }

    @Override
    public void onDetachedFromWindow() {
        mAwContents.onDetachedFromWindow();
        if (mGLfunctor != null) {
            mGLfunctor.detach();
        }
    }

    @Override
    public void onVisibilityChanged(View changedView, int visibility) {
        // The AwContents will find out the container view visibility before the first draw so we
        // can safely ignore onVisibilityChanged callbacks that happen before init().
        if (mAwContents != null) {
            mAwContents.onVisibilityChanged(changedView, visibility);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        mAwContents.onWindowFocusChanged(hasWindowFocus);
    }

    @Override
    public void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        mAwContents.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    @Override
    public boolean setFrame(int left, int top, int right, int bottom) {
        // TODO(joth): This is the minimum implementation for initial
        // bringup. Likely will need to go up to AwContents for a complete
        // implementation, e.g. setting the compositor visible region (to
        // avoid painting tiles that are offscreen due to the view's position).
        return mWebViewPrivate.super_setFrame(left, top, right, bottom);
    }

    @Override
    public void onSizeChanged(int w, int h, int ow, int oh) {
        mAwContents.onSizeChanged(w, h, ow, oh);
    }

    @Override
    public void onScrollChanged(int l, int t, int oldl, int oldt) {
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return mAwContents.dispatchKeyEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return mAwContents.onTouchEvent(ev);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return mAwContents.onHoverEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return mAwContents.onGenericMotionEvent(event);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        // Trackball event not handled, which eventually gets converted to DPAD keyevents
        return false;
    }

    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        mAwContents.requestFocus();
        return mWebViewPrivate.super_requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mAwContents.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
        UnimplementedWebViewApi.invoke();
        return false;
    }

    @Override
    public void setBackgroundColor(int color) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void setLayerType(int layerType, Paint paint) {
        UnimplementedWebViewApi.invoke();
    }

    @Override
    public void preDispatchDraw(Canvas canvas) {
        // TODO(leandrogracia): remove this method from WebViewProvider if we think
        // we won't need it again.
    }

    // WebViewProvider.ScrollDelegate implementation ----------------------------------------------

    @Override
    public int computeHorizontalScrollRange() {
        return mAwContents.computeHorizontalScrollRange();
    }

    @Override
    public int computeHorizontalScrollOffset() {
        return mAwContents.computeHorizontalScrollOffset();
    }

    @Override
    public int computeVerticalScrollRange() {
        return mAwContents.computeVerticalScrollRange();
    }

    @Override
    public int computeVerticalScrollOffset() {
        return mAwContents.computeVerticalScrollOffset();
    }

    @Override
    public int computeVerticalScrollExtent() {
        return mAwContents.computeVerticalScrollExtent();
    }

    @Override
    public void computeScroll() {
        // BUG=http://b/6029133
        // Too much log spam: UnimplementedWebViewApi.invoke();
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
        public void onScrollChanged(int l, int t, int oldl, int oldt) {
            mWebViewPrivate.setScrollXRaw(l);
            mWebViewPrivate.setScrollYRaw(t);
            mWebViewPrivate.onScrollChanged(l, t, oldl, oldt);
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
        public void setMeasuredDimension(int measuredWidth, int measuredHeight) {
            mWebViewPrivate.setMeasuredDimension(measuredWidth, measuredHeight);
        }

        // TODO(michaelbai) : Add @Override once this method added in
        // AwContents.InternalAcessDelegate.
        public boolean requestDrawGL(Canvas canvas) {
            return mGLfunctor.requestDrawGL((HardwareCanvas)canvas, mWebView.getViewRootImpl());
        }
    }
}
