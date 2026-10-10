package com.watchalign.redditqc;

import android.app.Activity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceError;
import android.widget.LinearLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.json.JSONArray;

/**
 * Fallback for the 403 seen with direct HttpURLConnection requests.
 * Uses the embedded browser's own session to load the JSON document, without
 * extracting or sharing credentials. Browser access still may be blocked.
 */
final class RedditBrowser {
    private final Activity activity;
    private final WebView view;
    private final Consumer<String> log;
    private volatile String requestedPostId;
    private volatile CompletableFuture<String> waiting;

    RedditBrowser(Activity activity, LinearLayout parent, Consumer<String> log) {
        this.activity=activity;this.log=log;
        view = new WebView(activity);
        view.setVisibility(View.GONE);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1,(int)(340*activity.getResources().getDisplayMetrics().density)));
        WebSettings settings=view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(userAgent());
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true);
        view.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView browser,String url) {
                CompletableFuture<String> f=waiting;
                String wanted=requestedPostId;
                if(f==null || wanted==null || !url.contains(wanted))return;
                view.evaluateJavascript("(function(){try{return document.body?document.body.innerText.slice(0,6000000):'';}catch(e){return 'JS_ERROR '+e}})()",encoded->{
                    if(f!=waiting || f.isDone())return;
                    try {
                        String actual=new JSONArray("["+encoded+"]").getString(0).trim();
                        if(actual.startsWith("[") && actual.contains("\"kind\"")){
                            f.complete(actual);
                        } else {
                            String sample=actual.length()>180?actual.substring(0,180):actual;
                            f.completeExceptionally(new IOException("BROWSER_DENIED: Reddit browser returned "+sample));
                        }
                    }catch(Exception ex){f.completeExceptionally(new IOException("Could not read Reddit browser JSON",ex));}
                });
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest request, WebResourceError error) {
                CompletableFuture<String> f=waiting;
                if(f!=null && request.isForMainFrame() && !f.isDone()) {
                    f.completeExceptionally(new IOException("BROWSER_DENIED: "+error.getDescription()));
                }
            }
        });
        parent.addView(view);
    }
    static String userAgent() {
        return "Mozilla/5.0 (Linux; Android 17; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
    }
    void openLogin() {
        activity.runOnUiThread(()->{
            view.setVisibility(View.VISIBLE);
            view.loadUrl("https://www.reddit.com/r/RepTimeQC/");
            log.accept("Reddit browser opened. Sign in here if necessary, then run TEST FIRST REDDIT THREAD.");
        });
    }
    byte[] fetchJson(String url,int timeoutSeconds) throws IOException {
        if(Thread.currentThread()==activity.getMainLooper().getThread())
            throw new IOException("Browser fetch cannot run on main thread");
        CompletableFuture<String> future=new CompletableFuture<>();
        requestedPostId=extractId(url);
        waiting=future;
        activity.runOnUiThread(()->{
            view.setVisibility(View.VISIBLE);
            view.loadUrl(url);
        });
        try{
            String s=future.get(timeoutSeconds,TimeUnit.SECONDS);
            return s.getBytes(StandardCharsets.UTF_8);
        }catch(Exception ex){
            throw new IOException("BROWSER_DENIED: Could not retrieve Reddit JSON in embedded browser; open Reddit browser and sign in if needed. "+ex.getMessage(),ex);
        }finally{
            waiting=null;requestedPostId=null;
        }
    }
    private static String extractId(String url) {
        int p=url.indexOf("/comments/"); if(p<0)return "";
        String t=url.substring(p+10);
        int e=t.indexOf('/'); if(e<0)e=t.indexOf('.');
        if(e<0)e=t.length();return t.substring(0,e);
    }
}
