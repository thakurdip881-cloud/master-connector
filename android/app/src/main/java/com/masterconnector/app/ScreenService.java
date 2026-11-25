package com.masterconnector.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
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
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Surface;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 🖥️ फ़ोन की स्क्रीन जाँच (ड्यूटी में) — एक बार "Start now", फिर ड्यूटी खत्म होने तक चुपचाप।
 * आम तौर पर हर 10 मिनट एक छोटी स्क्रीन फ़ोटो (screens/{uid})।
 * एडमिन "स्क्रीन/लाइव" दबाए (screenReq/{uid}.until) → 1 घंटे तक: पहले 2 मिनट हर 5 सेकंड, फिर हर 30 सेकंड।
 * कम डेटा: 300px चौड़ी JPEG, कैमरा यहाँ नहीं खुलता (चेहरा web लाइव से)।
 */
public class ScreenService extends Service {
    private static final String API_KEY = "AIzaSyApw_GttyXeEmHFvKlJjoQFI11KuL4ahxI";
    private static final String PROJECT = "master-connector-f2a0d";
    private static final String CH = "screen_check";
    private static final int SCR_W = 300;

    static volatile boolean running = false;
    private static volatile ScreenService me;

    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private HandlerThread thread;
    private Handler h;
    private MediaProjection mp;
    private VirtualDisplay vd;
    private ImageReader scrReader;
    private Image scrHeld;
    private int scrW, scrH;
    private String refresh = "", idToken = "", uid = "";
    private long tokenExp = 0, until = 0, reqStart = 0, lastShot = 0, lastPoll = 0, seenUntil = 0;
    private boolean finished = false;

    public static void start(Context c, int code, Intent data, String uid, String rt) {
        Intent i = new Intent(c, ScreenService.class).putExtra("code", code).putExtra("data", data).putExtra("uid", uid).putExtra("rt", rt);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }
    public static void stop() { ScreenService s = me; if (s != null && s.h != null) s.h.post(s::finish); }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        if (in == null || running) return START_NOT_STICKY;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(CH, "ड्यूटी स्क्रीन जाँच", NotificationManager.IMPORTANCE_MIN));
        }
        Notification n = (Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this))
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("ड्यूटी चालू")
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE))
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(4, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            else startForeground(4, n);
        } catch (Exception e) { MainActivity.screenEvent("fail"); stopSelf(); return START_NOT_STICKY; }
        running = true; me = this;
        uid = s(in.getStringExtra("uid"));
        refresh = s(in.getStringExtra("rt"));
        thread = new HandlerThread("mcScreen"); thread.start();
        h = new Handler(thread.getLooper());
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            Intent data = in.getParcelableExtra("data");
            mp = mpm.getMediaProjection(in.getIntExtra("code", Activity.RESULT_CANCELED), data);
            if (mp == null) throw new Exception("no projection");
            mp.registerCallback(new MediaProjection.Callback() { @Override public void onStop() { h.post(() -> finish()); } }, h);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            scrW = SCR_W;
            scrH = Math.max(200, Math.round(SCR_W * (float) dm.heightPixels / Math.max(1, dm.widthPixels)));
            scrReader = ImageReader.newInstance(scrW, scrH, PixelFormat.RGBA_8888, 2);
            scrReader.setOnImageAvailableListener(r -> {
                try { Image im = r.acquireLatestImage(); if (im != null) { if (scrHeld != null) scrHeld.close(); scrHeld = im; } } catch (Exception ignored) { }
            }, h);
            vd = mp.createVirtualDisplay("mcCheck", scrW, scrH, Math.max(80, dm.densityDpi * SCR_W / Math.max(1, dm.widthPixels)),
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, scrReader.getSurface(), null, h);
        } catch (Exception e) { MainActivity.screenEvent("fail"); finish(); return START_NOT_STICKY; }
        MainActivity.screenEvent("ok");
        h.postDelayed(this::tick, 3000);
        return START_NOT_STICKY;
    }

    private static String s(String v) { return v == null ? "" : v; }

    /* हर 5 सेकंड जागकर तय करना: अभी फ़ोटो भेजनी है या नहीं */
    private void tick() {
        if (finished) return;
        long t = System.currentTimeMillis();
        if (t - lastPoll > (t < until ? 30000 : 15000)) { lastPoll = t; net.execute(this::pollReq); }
        long gap;
        if (t < until) gap = (t - reqStart < 120000) ? 5000 : 30000;
        else gap = 600000;
        if (t - lastShot >= gap) {
            String scr = "";
            try { if (scrHeld != null) scr = scrJpeg(scrHeld); } catch (Exception ignored) { }
            if (!scr.isEmpty()) { lastShot = t; final String a = scr; net.execute(() -> sendShot(a)); }
        }
        h.postDelayed(this::tick, 5000);
    }

    private String scrJpeg(Image im) {
        Image.Plane p = im.getPlanes()[0];
        ByteBuffer buf = p.getBuffer(); buf.rewind();
        int ps = p.getPixelStride(), rs = p.getRowStride();
        int w = im.getWidth(), hh = im.getHeight();
        Bitmap full = Bitmap.createBitmap(rs / ps, hh, Bitmap.Config.ARGB_8888);
        full.copyPixelsFromBuffer(buf);
        Bitmap crop = Bitmap.createBitmap(full, 0, 0, Math.min(w, full.getWidth()), hh);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        crop.compress(Bitmap.CompressFormat.JPEG, 38, o);
        if (crop != full) crop.recycle();
        full.recycle();
        return "data:image/jpeg;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
    }

    private void finish() {
        if (finished) return;
        finished = true;
        try { if (vd != null) vd.release(); } catch (Exception ignored) { }
        try { if (mp != null) mp.stop(); } catch (Exception ignored) { }
        try { if (scrHeld != null) scrHeld.close(); } catch (Exception ignored) { }
        try { if (scrReader != null) scrReader.close(); } catch (Exception ignored) { }
        net.execute(() -> { MainActivity.screenEvent("end"); stopSelf(); });
    }

    @Override
    public void onDestroy() {
        running = false; me = null;
        net.shutdown();
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    /* ---------- Firestore REST ---------- */
    private static String now() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }
    private static long parseTs(String v) {
        try {
            String x = v.replaceAll("\\.(\\d{3})\\d*Z$", ".$1Z");
            if (!x.contains(".")) x = x.replace("Z", ".000Z");
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            return f.parse(x).getTime();
        } catch (Exception e) { return 0; }
    }
    private void auth() throws Exception { if (idToken.isEmpty() || System.currentTimeMillis() > tokenExp) refreshToken(); }

    private void pollReq() {
        try {
            auth();
            HttpURLConnection c = (HttpURLConnection) new URL("https://firestore.googleapis.com/v1/projects/" + PROJECT
                    + "/databases/(default)/documents/screenReq/" + URLEncoder.encode(uid, "UTF-8") + "?mask.fieldPaths=until").openConnection();
            c.setRequestProperty("Authorization", "Bearer " + idToken);
            c.setConnectTimeout(15000); c.setReadTimeout(20000);
            int code = c.getResponseCode();
            if (code == 401) { idToken = ""; c.disconnect(); return; }
            if (code != 200) { c.disconnect(); return; }
            JSONObject j = new JSONObject(read(c.getInputStream()));
            c.disconnect();
            String ts = j.optJSONObject("fields") == null ? "" : j.getJSONObject("fields").optJSONObject("until") == null ? "" : j.getJSONObject("fields").getJSONObject("until").optString("timestampValue", "");
            long u = ts.isEmpty() ? 0 : parseTs(ts);
            if (u != seenUntil) {
                seenUntil = u;
                if (u > System.currentTimeMillis()) { if (System.currentTimeMillis() >= until) reqStart = System.currentTimeMillis(); until = u; lastShot = 0; }
                else until = 0;
            }
        } catch (Exception ignored) { }
    }

    private void sendShot(String img) {
        try {
            auth();
            JSONObject f = new JSONObject()
                    .put("uid", new JSONObject().put("stringValue", uid))
                    .put("img", new JSONObject().put("stringValue", img))
                    .put("src", new JSONObject().put("stringValue", "phone"))
                    .put("at", new JSONObject().put("timestampValue", now()));
            String url = "https://firestore.googleapis.com/v1/projects/" + PROJECT + "/databases/(default)/documents/screens/" + URLEncoder.encode(uid, "UTF-8");
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            c.setRequestProperty("Authorization", "Bearer " + idToken);
            c.setRequestProperty("Content-Type", "application/json");
            c.setConnectTimeout(15000); c.setReadTimeout(20000);
            c.setDoOutput(true);
            try (OutputStream o = c.getOutputStream()) { o.write(new JSONObject().put("fields", f).toString().getBytes("UTF-8")); }
            if (c.getResponseCode() == 401) idToken = "";
            c.disconnect();
        } catch (Exception ignored) { }
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
        uid = j.optString("user_id", uid);
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
}
