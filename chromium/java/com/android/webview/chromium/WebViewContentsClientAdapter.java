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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Picture;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Browser;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.ConsoleMessage;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.chromium.android_webview.AwContentsClient;
import org.chromium.android_webview.AwHttpAuthHandler;
import org.chromium.android_webview.InterceptedRequestData;
import org.chromium.android_webview.JsPromptResultReceiver;
import org.chromium.android_webview.JsResultReceiver;
import org.chromium.content.browser.ContentView;
import org.chromium.content.browser.ContentViewClient;

import java.net.URISyntaxException;

/**
 * An adapter class that forwards the callbacks from {@link ContentViewClient}
 * to the appropriate {@link WebViewClient} or {@link WebChromeClient}.
 *
 * An instance of this class is associated with one {@link WebViewChromium}
 * instance. A WebViewChromium is a WebView implementation provider (that is
 * android.webkit.WebView delegates all functionality to it) and has exactly
 * one corresponding {@link ContentView} instance.
 *
 * A {@link ContentViewClient} may be shared between multiple {@link ContentView}s,
 * and hence multiple WebViews. Many WebViewClient methods pass the source
 * WebView as an argument. This means that we either need to pass the
 * corresponding ContentView to the corresponding ContentViewClient methods,
 * or use an instance of ContentViewClientAdapter per WebViewChromium, to
 * allow the source WebView to be injected by ContentViewClientAdapter. We
 * choose the latter, because it makes for a cleaner design.
 */
public class WebViewContentsClientAdapter extends AwContentsClient {
    private static final String TAG = "ContentViewClientAdapter";
    // The WebView instance that this adapter is serving.
    private final WebView mWebView;
    // The WebViewClient instance that was passed to WebView.setWebViewClient().
    private WebViewClient mWebViewClient;
    // The WebViewClient instance that was passed to WebView.setContentViewClient().
    private WebChromeClient mWebChromeClient;
    // The listener receiving find-in-page API results.
    private WebView.FindListener mFindListener;
    // The listener receiving notifications of screen updates.
    private WebView.PictureListener mPictureListener;

    private DownloadListener mDownloadListener;

    private Handler mUiThreadHandler;

    private static final int NEW_WEBVIEW_CREATED = 100;

    /**
     * Adapter constructor.
     *
     * @param webView the {@link WebView} instance that this adapter is serving.
     */
    WebViewContentsClientAdapter(WebView webView) {
        if (webView == null) {
            throw new IllegalArgumentException("webView can't be null");
        }

        mWebView = webView;
        setWebViewClient(null);
        setWebChromeClient(null);

        mUiThreadHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                switch(msg.what) {
                    case NEW_WEBVIEW_CREATED:
                        WebView.WebViewTransport t = (WebView.WebViewTransport) msg.obj;
                        WebView newWebView = t.getWebView();
                        if (newWebView == null) {
                            throw new IllegalArgumentException(
                                    "Must provide a new WebView for the new window.");
                        }
                        if (newWebView == mWebView) {
                            throw new IllegalArgumentException(
                                    "Parent WebView cannot host it's own popup window. Please " +
                                    "use WebSettings.setSupportMultipleWindows(false)");
                        }

                        if (newWebView.copyBackForwardList().getSize() != 0) {
                            throw new IllegalArgumentException(
                                    "New WebView for popup window must not have been previously " +
                                    "navigated.");
                        }

                        WebViewChromium.completeWindowCreation(mWebView, newWebView);
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
        };

    }

    // WebViewClassic is coded in such a way that even if a null WebViewClient is set,
    // certain actions take place.
    // We choose to replicate this behavior by using a NullWebViewClient implementation (also known
    // as the Null Object pattern) rather than duplicating the WebViewClassic approach in
    // ContentView.
    static class NullWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideKeyEvent(WebView view, KeyEvent event) {
            // TODO: Investigate more and add a test case.
            // This is a copy of what Clank does. The WebViewCore key handling code and Clank key
            // handling code differ enough that it's not trivial to figure out how keycodes are
            // being filtered.
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_MENU ||
                keyCode == KeyEvent.KEYCODE_HOME ||
                keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_CALL ||
                keyCode == KeyEvent.KEYCODE_ENDCALL ||
                keyCode == KeyEvent.KEYCODE_POWER ||
                keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_CAMERA ||
                keyCode == KeyEvent.KEYCODE_FOCUS ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_VOLUME_MUTE ||
                keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Intent intent;
            // Perform generic parsing of the URI to turn it into an Intent.
            try {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            } catch (URISyntaxException ex) {
                Log.w(TAG, "Bad URI " + url + ": " + ex.getMessage());
                return false;
            }
            // Sanitize the Intent, ensuring web pages can not bypass browser
            // security (only access to BROWSABLE activities).
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            // Pass the package name as application ID so that the intent from the
            // same application can be opened in the same tab.
            intent.putExtra(Browser.EXTRA_APPLICATION_ID,
                    view.getContext().getPackageName());
            try {
                view.getContext().startActivity(intent);
            } catch (ActivityNotFoundException ex) {
                Log.w(TAG, "No application can handle " + url);
                return false;
            }
            return true;
        }
    }

    void setWebViewClient(WebViewClient client) {
        if (client != null) {
            mWebViewClient = client;
        } else {
            mWebViewClient = new NullWebViewClient();
        }
    }

    void setWebChromeClient(WebChromeClient client) {
        if (client != null) {
            mWebChromeClient = client;
        } else {
            // WebViewClassic doesn't implement any special behavior for a null WebChromeClient.
            mWebChromeClient = new WebChromeClient();
        }
    }

    void setDownloadListener(DownloadListener listener) {
        mDownloadListener = listener;
    }

    void setFindListener(WebView.FindListener listener) {
        mFindListener = listener;
    }

    void setPictureListener(WebView.PictureListener listener) {
        mPictureListener = listener;
    }

    //--------------------------------------------------------------------------------------------
    //                        Adapter for WebContentsDelegate methods.
    //--------------------------------------------------------------------------------------------

    /**
     * @see AwContentsClient#doUpdateVisiteHistory(String, boolean)
     */
    @Override
    public void doUpdateVisitedHistory(String url, boolean isReload) {
        mWebViewClient.doUpdateVisitedHistory(mWebView, url, isReload);
    }

    /**
     * @see AwContentsClient#onProgressChanged(int)
     */
    @Override
    public void onProgressChanged(int progress) {
        mWebChromeClient.onProgressChanged(mWebView, progress);
    }

    /**
     * @see AwContentsClient#shouldInterceptRequest(java.lang.String)
     */
    @Override
    public InterceptedRequestData shouldInterceptRequest(String url) {
        WebResourceResponse response = mWebViewClient.shouldInterceptRequest(mWebView, url);
        if (response == null) return null;
        return new InterceptedRequestData(
                response.getMimeType(),
                response.getEncoding(),
                response.getData());
    }

    /**
     * @see AwContentsClient#shouldIgnoreNavigation(java.lang.String)
     */
    @Override
    public boolean shouldIgnoreNavigation(String url) {
      return mWebViewClient.shouldOverrideUrlLoading(mWebView, url);
    }

    /**
     * @see AwContentsClient#onUnhandledKeyEvent(android.view.KeyEvent)
     */
    @Override
    public void onUnhandledKeyEvent(KeyEvent event) {
        mWebViewClient.onUnhandledKeyEvent(mWebView, event);
    }

    /**
     * @see AwContentsClient#onConsoleMessage(android.webkit.ConsoleMessage)
     */
    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        return mWebChromeClient.onConsoleMessage(consoleMessage);
    }

    /**
     * @see AwContentsClient#onFindResultReceived(int,int,boolean)
     */
    @Override
    public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches,
            boolean isDoneCounting) {
        if (mFindListener == null) return;
        mFindListener.onFindResultReceived(activeMatchOrdinal, numberOfMatches, isDoneCounting);
    }

    /**
     * @See AwContentsClient#onNewPicture(Picture)
     */
    @Override
    public void onNewPicture(Picture picture) {
        if (mPictureListener == null) return;
        mPictureListener.onNewPicture(mWebView, picture);
    }

    @Override
    public void onLoadResource(String url) {
        mWebViewClient.onLoadResource(mWebView, url);
    }

    @Override
    public boolean onCreateWindow(boolean isDialog, boolean isUserGesture) {
        Message m = mUiThreadHandler.obtainMessage(
                NEW_WEBVIEW_CREATED, mWebView.new WebViewTransport());
        return mWebChromeClient.onCreateWindow(mWebView, isDialog, isUserGesture, m);
    }

    /**
     * @see AwContentsClient#onCloseWindow()
     */
    /* @Override */
    public void onCloseWindow() {
        mWebChromeClient.onCloseWindow(mWebView);
    }

    /**
     * @see AwContentsClient#onRequestFocus()
     */
    /* @Override */
    public void onRequestFocus() {
        mWebChromeClient.onRequestFocus(mWebView);
    }

    //--------------------------------------------------------------------------------------------
    //                        Trivial Chrome -> WebViewClient mappings.
    //--------------------------------------------------------------------------------------------

    /**
     * @see ContentViewClient#onPageStarted(String)
     */
    @Override
    public void onPageStarted(String url) {
        //TODO: Can't get the favicon till b/6094807 is fixed.
        mWebViewClient.onPageStarted(mWebView, url, null);
    }

    /**
     * @see ContentViewClient#onPageFinished(String)
     */
    @Override
    public void onPageFinished(String url) {
        mWebViewClient.onPageFinished(mWebView, url);
    }

    /**
     * @see ContentViewClient#onReceivedError(int,String,String)
     */
    @Override
    public void onReceivedError(int errorCode, String description, String failingUrl) {
        mWebViewClient.onReceivedError(mWebView, errorCode, description, failingUrl);
    }

    /**
     * @see ContentViewClient#onUpdateTitle(String)
     */
    @Override
    public void onUpdateTitle(String title) {
        mWebChromeClient.onReceivedTitle(mWebView, title);
    }


    /**
     * @see ContentViewClient#shouldOverrideKeyEvent(KeyEvent)
     */
    @Override
    public boolean shouldOverrideKeyEvent(KeyEvent event) {
        // TODO(joth): The expression here is a workaround for http://b/7697782 :-
        // 1. The check for system key should be made in AwContents or ContentViewCore,
        //    before shouldOverrideKeyEvent() is called at all.
        // 2. shouldOverrideKeyEvent() should be called in onKeyDown/onKeyUp, not from
        //    dispatchKeyEvent().
        return event.isSystem() ||
            mWebViewClient.shouldOverrideKeyEvent(mWebView, event);
    }


    //--------------------------------------------------------------------------------------------
    //                 More complicated mappings (including behavior choices)
    //--------------------------------------------------------------------------------------------

    /**
     * @see ContentViewClient#onTabCrash()
     */
    @Override
    public void onTabCrash() {
        // The WebViewClassic implementation used a single process, so any crash would
        // cause the application to terminate.  WebViewChromium should have the same
        // behavior as long as we run the renderer in-process. This needs to be revisited
        // if we change that decision.
        Log.e(TAG, "Renderer crash reported.");
        mWebChromeClient.onCloseWindow(mWebView);
    }

    //--------------------------------------------------------------------------------------------
    //                                     The TODO section
    //--------------------------------------------------------------------------------------------


    /**
     * @see ContentViewClient#onImeEvent()
     */
    @Override
    public void onImeEvent() {
    }

    /**
     * @see ContentViewClient#onEvaluateJavaScriptResult(int,String)
     */
    @Override
    public void onEvaluateJavaScriptResult(int id, String jsonResult) {
    }

    /**
     * @see ContentViewClient#onStartContentIntent(Context, String)
     * Callback when detecting a click on a content link.
     */
    @Override
    public void onStartContentIntent(Context context, String contentUrl) {
        mWebViewClient.shouldOverrideUrlLoading(mWebView, contentUrl);
    }

    private static class SimpleJsResultReceiver implements JsResult.ResultReceiver {
        private JsResultReceiver mChromeResultReceiver;

        public SimpleJsResultReceiver(JsResultReceiver receiver) {
            mChromeResultReceiver = receiver;
        }

        @Override
        public void onJsResultComplete(JsResult result) {
            if (result.getResult()) {
                mChromeResultReceiver.confirm();
            } else {
                mChromeResultReceiver.cancel();
            }
        }
    }

    private static class JsPromptResultReceiverAdapter implements JsResult.ResultReceiver {
        private JsPromptResultReceiver mChromeResultReceiver;
        private JsPromptResult mPromptResult;

        public JsPromptResultReceiverAdapter(JsPromptResultReceiver receiver) {
            mChromeResultReceiver = receiver;
            // We hold onto the JsPromptResult here, just to avoid the need to downcast
            // in onJsResultComplete.
            mPromptResult = new JsPromptResult(this);
        }

        public JsPromptResult getPromptResult() {
            return mPromptResult;
        }

        @Override
        public void onJsResultComplete(JsResult result) {
            if (result != mPromptResult) throw new RuntimeException("incorrect JsResult instance");
            if (mPromptResult.getResult()) {
                mChromeResultReceiver.confirm(mPromptResult.getStringResult());
            } else {
                mChromeResultReceiver.cancel();
            }
        }
    }

    @Override
    public void onGeolocationPermissionsShowPrompt(String origin,
            GeolocationPermissions.Callback callback) {
        mWebChromeClient.onGeolocationPermissionsShowPrompt(origin, callback);
    }

    @Override
    public void onGeolocationPermissionsHidePrompt() {
        mWebChromeClient.onGeolocationPermissionsHidePrompt();
    }

    @Override
    public void handleJsAlert(String url, String message, JsResultReceiver receiver) {
        JsResult res = new JsResult(new SimpleJsResultReceiver(receiver));
        mWebChromeClient.onJsAlert(mWebView, url, message, res);
        // TODO: Handle the case of the client returning false;
    }

    @Override
    public void handleJsBeforeUnload(String url, String message, JsResultReceiver receiver) {
        JsResult res = new JsResult(new SimpleJsResultReceiver(receiver));
        mWebChromeClient.onJsBeforeUnload(mWebView, url, message, res);
        // TODO: Handle the case of the client returning false;
    }

    @Override
    public void handleJsConfirm(String url, String message, JsResultReceiver receiver) {
        JsResult res = new JsResult(new SimpleJsResultReceiver(receiver));
        mWebChromeClient.onJsConfirm(mWebView, url, message, res);
        // TODO: Handle the case of the client returning false;
    }

    @Override
    public void handleJsPrompt(String url, String message, String defaultValue,
            JsPromptResultReceiver receiver) {
        JsPromptResult res = new JsPromptResultReceiverAdapter(receiver).getPromptResult();
        mWebChromeClient.onJsPrompt(mWebView, url, message, defaultValue, res);
        // TODO: Handle the case of the client returning false;
    }

    @Override
    public void onReceivedHttpAuthRequest(AwHttpAuthHandler handler, String host, String realm) {
        mWebViewClient.onReceivedHttpAuthRequest(mWebView,
                new AwHttpAuthHandlerAdapter(handler), host, realm);
    }

    @Override
    public void onFormResubmission(Message dontResend, Message resend) {
        mWebViewClient.onFormResubmission(mWebView, dontResend, resend);
    }

    @Override
    public void onDownloadStart(String url,
                                String userAgent,
                                String contentDisposition,
                                String mimeType,
                                long contentLength) {
        if (mDownloadListener != null) {
            mDownloadListener.onDownloadStart(url,
                                              userAgent,
                                              contentDisposition,
                                              mimeType,
                                              contentLength);
        }
    }

    @Override
    public void onScaleChanged(float oldScale, float newScale) {
        mWebViewClient.onScaleChanged(mWebView, oldScale, newScale);
    }

    private static class AwHttpAuthHandlerAdapter extends android.webkit.HttpAuthHandler {
        private AwHttpAuthHandler mAwHandler;

        public AwHttpAuthHandlerAdapter(AwHttpAuthHandler awHandler) {
            mAwHandler = awHandler;
        }

        @Override
        public void proceed(String username, String password) {
            if (username == null) {
                username = "";
            }

            if (password == null) {
                password = "";
            }
            mAwHandler.proceed(username, password);
        }

        @Override
        public void cancel() {
            mAwHandler.cancel();
        }

        @Override
        public boolean useHttpAuthUsernamePassword() {
            return mAwHandler.isFirstAttempt();
        }
    }
}
