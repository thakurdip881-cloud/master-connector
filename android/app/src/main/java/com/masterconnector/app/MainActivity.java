package com.masterconnector.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import org.json.JSONObject;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://thakurdip881-cloud.github.io/master-connector/";
    private static final int REQ_PERM = 7, REQ_VOICE = 8;
    private TextToSpeech tts; private boolean ttsReady = false;
    private WebView web;
    private GeolocationPermissions.Callback geoCb;
    private String geoOrigin;
    // duty शुरू हुई पर अनुमति बाद में मिली — तब service शुरू करने के लिए
    private String[] pendingDuty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.addJavascriptInterface(new Bridge(), "MCNative");
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (hasLocation()) { callback.invoke(origin, true, false); return; }
                geoOrigin = origin; geoCb = callback;
                askPermissions();
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u.toString().startsWith(APP_URL)) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                return true;
            }
        });
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(APP_URL);
        askPermissions();
        tts = new TextToSpeech(this, st -> {
            if (st == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(new Locale("hi", "IN"));
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                tts.setSpeechRate(0.95f);
            }
        });
    }

    private void voiceResult(String t) {
        web.evaluateJavascript("window.__dipVoice&&window.__dipVoice(" + JSONObject.quote(t) + ")", null);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_VOICE) return;
        ArrayList<String> r = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        voiceResult(res == RESULT_OK && r != null && !r.isEmpty() ? r.get(0) : "?");
    }

    @Override
    protected void onDestroy() { if (tts != null) tts.shutdown(); super.onDestroy(); }

    private boolean hasLocation() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void askPermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        List<String> need = new ArrayList<>();
        if (!hasLocation()) { need.add(Manifest.permission.ACCESS_FINE_LOCATION); need.add(Manifest.permission.ACCESS_COARSE_LOCATION); }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        boolean ok = hasLocation();
        if (geoCb != null) { geoCb.invoke(geoOrigin, ok, false); geoCb = null; }
        if (ok && pendingDuty != null) { String[] d = pendingDuty; pendingDuty = null; DutyService.start(this, d[0], d[1], d[2], d[3]); }
    }

    /** web ऐप (index.html) से बुलाया जाता है: window.MCNative.startDuty(...) */
    private class Bridge {
        @JavascriptInterface
        public void startDuty(String dutyId, String lat, String lng, String refreshToken) {
            runOnUiThread(() -> {
                if (!hasLocation()) { pendingDuty = new String[]{dutyId, lat, lng, refreshToken}; askPermissions(); return; }
                DutyService.start(MainActivity.this, dutyId, lat, lng, refreshToken);
            });
        }
        @JavascriptInterface
        public void stopDuty() { runOnUiThread(() -> DutyService.stop(MainActivity.this)); }
        @JavascriptInterface
        public int version() { return 3; }
        /** 🎙️ बोलकर पूछें — Google की फ़्री बोलने-से-लिखने वाली सुविधा; नतीजा window.__dipVoice(text) में */
        @JavascriptInterface
        public void listen(String lang) {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
                    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
                    i.putExtra(RecognizerIntent.EXTRA_PROMPT, "बोलिए — जैसे: कितने काम बाकी हैं?");
                    startActivityForResult(i, REQ_VOICE);
                } catch (Exception e) { voiceResult("!"); }
            });
        }
        /** 🔊 बोलकर बताना (फ़ोन की अपनी आवाज़, फ़्री) */
        @JavascriptInterface
        public void speak(String t) {
            if (tts == null || !ttsReady) return;
            tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "s" + System.currentTimeMillis());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); }

    @Override
    public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
}
