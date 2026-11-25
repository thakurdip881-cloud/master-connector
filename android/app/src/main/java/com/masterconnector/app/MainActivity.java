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
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.os.Environment;
import android.util.Base64;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import org.json.JSONObject;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String APP_URL = "https://thakurdip881-cloud.github.io/master-connector/";
    private static final int REQ_PERM = 7, REQ_VOICE = 8, REQ_FILE = 9, REQ_MEDIA = 10, REQ_SCREEN = 11;
    private static java.lang.ref.WeakReference<MainActivity> self;
    private String[] pendingScreen;
    private PermissionRequest mediaReq;
    private ValueCallback<Uri[]> fileCb;
    private Uri camUri;
    private TextToSpeech tts; private boolean ttsReady = false;
    private WebView web;
    private GeolocationPermissions.Callback geoCb;
    private String geoOrigin;
    // duty शुरू हुई पर अनुमति बाद में मिली — तब service शुरू करने के लिए
    private String[] pendingDuty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        self = new java.lang.ref.WeakReference<>(this);
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
            /** सेल्फ़ी / 30 सेकंड वीडियो / ड्यूटी कैमरा जाँच — web का कैमरा-माइक */
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (hasMedia()) { grantMedia(request); return; }
                    mediaReq = request;
                    requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, REQ_MEDIA);
                });
            }
            /** फ़ोटो चुनना / खींचना (आधार, सेल्फ़ी, दस्तावेज़) */
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb; camUri = null;
                Intent pick;
                try { pick = p.createIntent(); } catch (Exception e) { pick = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"); }
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                Intent cam = null;
                if (hasCamera()) {
                    try {
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.Images.Media.DISPLAY_NAME, "mc_" + System.currentTimeMillis() + ".jpg");
                        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                        camUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                        cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, camUri);
                        cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } catch (Exception e) { cam = null; camUri = null; }
                } else if (Build.VERSION.SDK_INT >= 23) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_PERM);
                }
                Intent go;
                if (cam != null && p.isCaptureEnabled()) go = cam;
                else {
                    go = Intent.createChooser(pick, "फ़ोटो चुनें");
                    if (cam != null) go.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                }
                try { startActivityForResult(go, REQ_FILE); }
                catch (Exception e) { fileCb.onReceiveValue(null); fileCb = null; return false; }
                return true;
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
        if (req == REQ_FILE) { fileDone(res, data); return; }
        if (req == REQ_SCREEN) {
            String[] p = pendingScreen; pendingScreen = null;
            if (res == RESULT_OK && data != null && p != null) {
                try { ScreenService.start(this, res, data, p[0], p[1]); } catch (Exception e) { screenEvent("fail"); }
            } else screenEvent("no");
            return;
        }
        if (req != REQ_VOICE) return;
        ArrayList<String> r = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        voiceResult(res == RESULT_OK && r != null && !r.isEmpty() ? r.get(0) : "?");
    }

    /** 🖥️ स्क्रीन जाँच की हालत web को: ok / no / fail / end */
    static void screenEvent(String st) {
        MainActivity a = self == null ? null : self.get();
        if (a == null || a.web == null) return;
        a.runOnUiThread(() -> { try { a.web.evaluateJavascript("window.__mcScreen&&window.__mcScreen(" + JSONObject.quote(st) + ")", null); } catch (Exception ignored) { } });
    }

    @Override
    protected void onDestroy() { if (tts != null) tts.shutdown(); super.onDestroy(); }

    private void fileDone(int res, Intent data) {
        if (fileCb == null) return;
        Uri[] out = null;
        if (res == RESULT_OK) {
            if (data != null && data.getData() != null) out = new Uri[]{data.getData()};
            else if (data != null && data.getClipData() != null && data.getClipData().getItemCount() > 0) out = new Uri[]{data.getClipData().getItemAt(0).getUri()};
            else if (camUri != null) out = new Uri[]{camUri};
        }
        if (camUri != null && (out == null || !camUri.equals(out[0]))) { try { getContentResolver().delete(camUri, null, null); } catch (Exception ignored) { } }
        fileCb.onReceiveValue(out); fileCb = null; camUri = null;
    }

    private boolean hasCamera() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }
    private boolean hasMedia() {
        return hasCamera() && (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
    }
    private void grantMedia(PermissionRequest r) {
        List<String> ok = new ArrayList<>();
        for (String x : r.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(x) && hasCamera()) ok.add(x);
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(x) && (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)) ok.add(x);
        }
        if (ok.isEmpty()) r.deny(); else r.grant(ok.toArray(new String[0]));
    }

    private boolean hasLocation() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** TECNO/HiOS बैटरी बचत ड्यूटी की लोकेशन जाँच को पीछे से बंद न करे — एक ही बार पूछता है */
    private void askBattery() {
        try {
            if (Build.VERSION.SDK_INT < 23) return;
            android.content.SharedPreferences sp = getSharedPreferences("mc", MODE_PRIVATE);
            if (sp.getBoolean("batAsked", false)) return;
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(getPackageName())) return;
            sp.edit().putBoolean("batAsked", true).apply();
            startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) { }
    }

    private void askPermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        List<String> need = new ArrayList<>();
        if (!hasLocation()) { need.add(Manifest.permission.ACCESS_FINE_LOCATION); need.add(Manifest.permission.ACCESS_COARSE_LOCATION); }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERM);
        else askBattery();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_MEDIA) { if (mediaReq != null) { grantMedia(mediaReq); mediaReq = null; } return; }
        boolean ok = hasLocation();
        if (ok) askBattery();
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
        public void stopDuty() { runOnUiThread(() -> { DutyService.stop(MainActivity.this); ScreenService.stop(); }); }
        @JavascriptInterface
        public int version() { return 6; }
        /** 🖥️ ड्यूटी में स्क्रीन जाँच — फ़ोन एक बार "Start now" पूछेगा, फिर ड्यूटी खत्म तक चुपचाप */
        @JavascriptInterface
        public void startScreen(String uid, String refreshToken) {
            runOnUiThread(() -> {
                if (ScreenService.running) { screenEvent("ok"); return; }
                try {
                    android.media.projection.MediaProjectionManager mpm = (android.media.projection.MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                    Intent i = Build.VERSION.SDK_INT >= 34
                            ? mpm.createScreenCaptureIntent(android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay())
                            : mpm.createScreenCaptureIntent();
                    pendingScreen = new String[]{uid, refreshToken};
                    startActivityForResult(i, REQ_SCREEN);
                } catch (Exception e) { screenEvent("fail"); }
            });
        }
        @JavascriptInterface
        public boolean screenOn() { return ScreenService.running; }
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
        /** 📎 फ़ाइलें टैब: अपलोड की गई फ़ाइल फ़ोन के Download फ़ोल्डर में सेव करके खोलना */
        @JavascriptInterface
        public void saveFile(String name, String mime, String b64) {
            new Thread(() -> {
                try {
                    byte[] data = Base64.decode(b64, Base64.DEFAULT);
                    String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_");
                    String mt = (mime == null || mime.isEmpty()) ? "application/octet-stream" : mime;
                    Uri uri;
                    if (Build.VERSION.SDK_INT >= 29) {
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.Downloads.DISPLAY_NAME, safe);
                        cv.put(MediaStore.Downloads.MIME_TYPE, mt);
                        cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MASTER CONNECTOR");
                        uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                        try (OutputStream o = getContentResolver().openOutputStream(uri)) { o.write(data); }
                    } else {
                        File f = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), safe);
                        try (FileOutputStream o = new FileOutputStream(f)) { o.write(data); }
                        uri = null;
                    }
                    final Uri u = uri; final String m = mt;
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "⬇️ सेव हुई: Download/MASTER CONNECTOR/" + safe, Toast.LENGTH_LONG).show();
                        if (u != null) try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(u, m).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); } catch (Exception e) { }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "फ़ाइल सेव नहीं हुई", Toast.LENGTH_LONG).show());
                }
            }).start();
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
