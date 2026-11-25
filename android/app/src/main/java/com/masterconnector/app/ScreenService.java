package com.masterconnector.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Base64;
import android.util.DisplayMetrics;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 🖥️ ड्यूटी में स्क्रीन जाँच (वर्कर ने "Allow" दबाया हो तभी)।
 * हर 10 मिनट स्क्रीन की छोटी फ़ोटो → Firebase screens/{uid}।
 * मालिक "स्क्रीन + चेहरा देखें" दबाएँ (screenReq/{uid}.until) → 2 मिनट तक हर 5 सेकंड।
 * कम डेटा: 360px चौड़ी JPEG; मालिक का अनुरोध हर 30 सेकंड छोटे GET से देखा जाता है।
 */
public class ScreenService extends Service {
    private static final String API_KEY = "AIzaSyApw_GttyXeEmHFvKlJjoQFI11KuL4ahxI";
    private static final String PROJECT = "master-connector-f2a0d";
    private static final long EVERY_MS = 10 * 60_000L, LIVE_MS = 5_000L, POLL_MS = 30_000L;
    private static final String CH = "screen_run";
    static volatile boolean running = false;

    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private MediaProjection mp;
    private VirtualDisplay vd;
    private ImageReader ir;
    private HandlerThread ht;
    private Handler h;
    private volatile Bitmap last;
    private long lastConv = 0, lastShot = 0, lastPoll = 0;
    private volatile long liveUntil = 0;
    private int w, hgt;
    private String uid = "", refresh = "", idToken = "";
    private long tokenExp = 0;
    private SharedPreferences prefs;

    public static void start(Context c, int code, Intent data, String uid, String rt) {
        Intent i = new Intent(c, ScreenService.class).putExtra("code", code).putExtra("data", data)
                .putExtra("uid", uid).putExtra("rt", rt == null ? "" : rt);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception e) { }
    }

    public static void stop(Context c) { c.stopService(new Intent(c, ScreenService.class)); }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("duty", MODE_PRIVATE);
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(new NotificationChannel(CH, "स्क्रीन जाँच", NotificationManager.IMPORTANCE_LOW));
        }
        ht = new HandlerThread("mc-screen");
        ht.start();
        h = new Handler(ht.getLooper());
    }

    @Override
    @SuppressWarnings("deprecation")
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(3, note(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            else startForeground(3, note());
        } catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
        if (running || intent == null) return START_NOT_STICKY;
        int code = intent.getIntExtra("code", 0);
        Intent data = intent.getParcelableExtra("data");
        uid = intent.getStringExtra("uid");
        refresh = intent.getStringExtra("rt");
        if (refresh == null || refresh.isEmpty()) refresh = prefs.getString("rt", "");
        if (uid == null || uid.isEmpty() || data == null || refresh.isEmpty()) { stopSelf(); return START_NOT_STICKY; }
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mp = mpm.getMediaProjection(code, data);
            if (mp == null) { stopSelf(); return START_NOT_STICKY; }
            mp.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { stopSelf(); }
            }, h);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            w = 360;
            hgt = Math.round(360f * dm.heightPixels / Math.max(1, dm.widthPixels)) & ~1;
            if (hgt < 200) hgt = 640;
            ir = ImageReader.newInstance(w, hgt, PixelFormat.RGBA_8888, 2);
            ir.setOnImageAvailableListener(r -> {
                Image img = null;
                try {
                    img = r.acquireLatestImage();
                    if (img == null) return;
                    long now = SystemClock.uptimeMillis();
                    if (now - lastConv < 2000) return;
                    lastConv = now;
                    last = toBitmap(img);
                } catch (Exception e) {
                } finally { if (img != null) img.close(); }
            }, h);
            vd = mp.createVirtualDisplay("mc-screen", w, hgt, dm.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, ir.getSurface(), null, h);
        } catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
        running = true;
        lastShot = System.currentTimeMillis() - EVERY_MS + 15_000;   // पहली फ़ोटो 15 सेकंड बाद
        h.postDelayed(tick, 5_000);
        return START_NOT_STICKY;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long now = System.currentTimeMillis();
            if (now - lastPoll >= POLL_MS) { lastPoll = now; net.execute(ScreenService.this::poll); }
            boolean live = now < liveUntil;
            if ((live && now - lastShot >= LIVE_MS) || now - lastShot >= EVERY_MS) shoot(now);
            h.postDelayed(this, live ? 2_500 : 5_000);
        }
    };

    private Bitmap toBitmap(Image img) {
        Image.Plane p = img.getPlanes()[0];
        ByteBuffer buf = p.getBuffer();
        int ps = p.getPixelStride(), rs = p.getRowStride();
        int padW = rs / ps;
        Bitmap bm = Bitmap.createBitmap(padW, hgt, Bitmap.Config.ARGB_8888);
        bm.copyPixelsFromBuffer(buf);
        if (padW == w) return bm;
        Bitmap out = Bitmap.createBitmap(bm, 0, 0, w, hgt);
        bm.recycle();
        return out;
    }

    private void shoot(long now) {
        Bitmap b = last;
        if (b == null) return;
        lastShot = now;
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            int q = 45;
            b.compress(Bitmap.CompressFormat.JPEG, q, o);
            while (o.size() > 270_000 && q > 15) { q -= 10; o.reset(); b.compress(Bitmap.CompressFormat.JPEG, q, o); }
            final String img = "data:image/jpeg;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
            net.execute(() -> send(img));
        } catch (Exception e) { }
    }

    private static String iso(Date d) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(d);
    }

    private String docUrl(String col) throws Exception {
        return "https://firestore.googleapis.com/v1/projects/" + PROJECT + "/databases/(default)/documents/" + col + "/" + URLEncoder.encode(uid, "UTF-8");
    }

    /** screens/{uid}: uid, img, at, src */
    private void send(String img) {
        try {
            if (idToken.isEmpty() || System.currentTimeMillis() > tokenExp) refreshToken();
            JSONObject fields = new JSONObject()
                    .put("uid", new JSONObject().put("stringValue", uid))
                    .put("img", new JSONObject().put("stringValue", img))
                    .put("at", new JSONObject().put("timestampValue", iso(new Date())))
                    .put("src", new JSONObject().put("stringValue", "app"));
            String url = docUrl("screens") + "?updateMask.fieldPaths=uid&updateMask.fieldPaths=img&updateMask.fieldPaths=at&updateMask.fieldPaths=src";
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            c.setRequestProperty("Authorization", "Bearer " + idToken);
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            try (OutputStream o = c.getOutputStream()) { o.write(new JSONObject().put("fields", fields).toString().getBytes("UTF-8")); }
            int code = c.getResponseCode();
            if (code == 401) idToken = "";
            if (code == 403) h.post(this::stopSelf);   // वर्कर बंद/हटाया गया
            c.disconnect();
        } catch (Exception e) { /* इंटरनेट नहीं — अगली बार */ }
    }

    /** screenReq/{uid}.until — मालिक ने "देखें" दबाया तो live */
    private void poll() {
        try {
            if (idToken.isEmpty() || System.currentTimeMillis() > tokenExp) refreshToken();
            HttpURLConnection c = (HttpURLConnection) new URL(docUrl("screenReq") + "?mask.fieldPaths=until").openConnection();
            c.setRequestProperty("Authorization", "Bearer " + idToken);
            int code = c.getResponseCode();
            if (code == 200) {
                JSONObject f = new JSONObject(read(c.getInputStream())).optJSONObject("fields");
                String t = f == null ? "" : f.optJSONObject("until") == null ? "" : f.getJSONObject("until").optString("timestampValue", "");
                long u = 0;
                if (t.length() >= 19) {
                    SimpleDateFormat p = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                    p.setTimeZone(TimeZone.getTimeZone("UTC"));
                    u = p.parse(t.substring(0, 19)).getTime();
                }
                liveUntil = u;
            } else if (code == 401) idToken = "";
            else if (code == 404) liveUntil = 0;
            c.disconnect();
        } catch (Exception e) { }
    }

    private void refreshToken() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL("https://securetoken.googleapis.com/v1/token?key=" + API_KEY).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        c.setDoOutput(true);
        try (OutputStream o = c.getOutputStream()) { o.write(("grant_type=refresh_token&refresh_token=" + URLEncoder.encode(refresh, "UTF-8")).getBytes("UTF-8")); }
        if (c.getResponseCode() != 200) { c.disconnect(); throw new Exception("token"); }
        JSONObject j = new JSONObject(read(c.getInputStream()));
        idToken = j.getString("id_token");
        tokenExp = System.currentTimeMillis() + (Long.parseLong(j.optString("expires_in", "3600")) - 300) * 1000L;
        String rt = j.optString("refresh_token", "");
        if (!rt.isEmpty()) refresh = rt;
        c.disconnect();
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }

    private Notification note() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 1, i, PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("🖥️ ड्यूटी में स्क्रीन जाँच चालू")
                .setContentText("मालिक आपकी स्क्रीन देख सकते हैं · ड्यूटी खत्म होते ही बंद")
                .setOngoing(true).setContentIntent(pi).build();
    }

    @Override
    public void onDestroy() {
        running = false;
        try { h.removeCallbacks(tick); } catch (Exception e) { }
        try { if (vd != null) vd.release(); } catch (Exception e) { }
        try { if (ir != null) ir.close(); } catch (Exception e) { }
        try { if (mp != null) { MediaProjection m = mp; mp = null; m.stop(); } } catch (Exception e) { }
        try { ht.quitSafely(); } catch (Exception e) { }
        net.shutdown();
        super.onDestroy();
    }
}
