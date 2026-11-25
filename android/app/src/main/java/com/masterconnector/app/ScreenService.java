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
 * 📺 ड्यूटी जाँच: 3 मिनट तक हर 5 सेकंड वर्कर की स्क्रीन की छोटी फ़ोटो + सामने के कैमरे से चेहरा।
 * वर्कर दूसरे ऐप/साइट में लॉगिन करे तब भी चलता है। हर फ़ोटो सीधे Firebase (scrRec) में — एडमिन लाइव देखता है।
 * कम डेटा: स्क्रीन 300px चौड़ी, चेहरा 150px, JPEG कम क्वालिटी (~1 MB पूरी जाँच)।
 */
public class ScreenService extends Service {
    private static final String API_KEY = "AIzaSyApw_GttyXeEmHFvKlJjoQFI11KuL4ahxI";
    private static final String PROJECT = "master-connector-f2a0d";
    private static final String CH = "screen_check";
    private static final long EVERY_MS = 5000;
    private static final int SCR_W = 300, FACE_W = 150;

    static volatile boolean running = false;

    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private HandlerThread thread;
    private Handler h;
    private MediaProjection mp;
    private VirtualDisplay vd;
    private ImageReader scrReader, camReader;
    private Image scrHeld, camHeld;
    private CameraDevice cam;
    private CameraCaptureSession camSession;
    private int camRot = 270;
    private int scrW, scrH;

    private String dutyId = "", rec = "", refresh = "", idToken = "", uid = "";
    private long tokenExp = 0, endAt = 0;
    private int idx = 0;
    private boolean finished = false;

    public static void start(Context c, int code, Intent data, String duty, String rec, int secs, String rt) {
        Intent i = new Intent(c, ScreenService.class)
                .putExtra("code", code).putExtra("data", data)
                .putExtra("duty", duty).putExtra("rec", rec).putExtra("secs", secs).putExtra("rt", rt);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    @Override public IBinder onBind(Intent i) { return null; }

    private boolean hasCam() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        if (in == null || running) { return START_NOT_STICKY; }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(CH, "ड्यूटी स्क्रीन जाँच", NotificationManager.IMPORTANCE_LOW));
        }
        Notification n = (Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CH) : new Notification.Builder(this))
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("📺 3 मिनट की ड्यूटी जाँच चालू")
                .setContentText("एडमिन आपकी स्क्रीन और चेहरा देख रहे हैं · 3 मिनट बाद अपने-आप बंद")
                .setOngoing(true)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE))
                .build();
        boolean withCam = hasCam();
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                int t = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION | (withCam ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA : 0);
                startForeground(3, n, t);
            } else if (Build.VERSION.SDK_INT >= 29) {
                startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else startForeground(3, n);
        } catch (Exception e) {
            try { startForeground(3, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION); withCam = false; }
            catch (Exception e2) { MainActivity.screenEvent("fail"); stopSelf(); return START_NOT_STICKY; }
        }
        running = true;
        dutyId = s(in.getStringExtra("duty"));
        rec = s(in.getStringExtra("rec"));
        refresh = s(in.getStringExtra("rt"));
        int secs = Math.max(30, Math.min(600, in.getIntExtra("secs", 180)));
        endAt = System.currentTimeMillis() + secs * 1000L;

        thread = new HandlerThread("mcScreen"); thread.start();
        h = new Handler(thread.getLooper());
        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            Intent data = in.getParcelableExtra("data");
            mp = mpm.getMediaProjection(in.getIntExtra("code", Activity.RESULT_CANCELED), data);
            if (mp == null) throw new Exception("no projection");
            mp.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { h.post(() -> finish()); }
            }, h);
            DisplayMetrics dm = getResources().getDisplayMetrics();
            scrW = SCR_W;
            scrH = Math.max(200, Math.round(SCR_W * (float) dm.heightPixels / Math.max(1, dm.widthPixels)));
            scrReader = ImageReader.newInstance(scrW, scrH, PixelFormat.RGBA_8888, 3);
            scrReader.setOnImageAvailableListener(r -> {
                try { Image im = r.acquireLatestImage(); if (im != null) { if (scrHeld != null) scrHeld.close(); scrHeld = im; } } catch (Exception ignored) { }
            }, h);
            vd = mp.createVirtualDisplay("mcCheck", scrW, scrH, Math.max(80, dm.densityDpi * SCR_W / Math.max(1, dm.widthPixels)),
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, scrReader.getSurface(), null, h);
        } catch (Exception e) {
            MainActivity.screenEvent("fail");
            finish();
            return START_NOT_STICKY;
        }
        if (withCam) h.post(this::openCamera);
        net.execute(() -> patchDuty(true));
        MainActivity.screenEvent("ok");
        h.postDelayed(this::tick, 2500);
        return START_NOT_STICKY;
    }

    private static String s(String v) { return v == null ? "" : v; }

    /* ---------- सामने का कैमरा (Camera2, छोटा YUV, कम fps) ---------- */
    @SuppressLint("MissingPermission")
    private void openCamera() {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            String id = null;
            for (String c : cm.getCameraIdList()) {
                Integer f = cm.getCameraCharacteristics(c).get(CameraCharacteristics.LENS_FACING);
                if (f != null && f == CameraCharacteristics.LENS_FACING_FRONT) { id = c; break; }
            }
            if (id == null) return;
            CameraCharacteristics ch = cm.getCameraCharacteristics(id);
            Integer so = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            camRot = so == null ? 270 : so;
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size best = new Size(320, 240);
            if (map != null) {
                Size[] all = map.getOutputSizes(ImageFormat.YUV_420_888);
                long bestD = Long.MAX_VALUE;
                if (all != null) for (Size z : all) {
                    long d = Math.abs((long) z.getWidth() * z.getHeight() - 320L * 240L);
                    if (d < bestD) { bestD = d; best = z; }
                }
            }
            camReader = ImageReader.newInstance(best.getWidth(), best.getHeight(), ImageFormat.YUV_420_888, 3);
            camReader.setOnImageAvailableListener(r -> {
                try { Image im = r.acquireLatestImage(); if (im != null) { if (camHeld != null) camHeld.close(); camHeld = im; } } catch (Exception ignored) { }
            }, h);
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice d) {
                    cam = d;
                    try {
                        final Surface sf = camReader.getSurface();
                        d.createCaptureSession(Collections.singletonList(sf), new CameraCaptureSession.StateCallback() {
                            @Override public void onConfigured(CameraCaptureSession ses) {
                                camSession = ses;
                                try {
                                    CaptureRequest.Builder b = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                    b.addTarget(sf);
                                    ses.setRepeatingRequest(b.build(), null, h);
                                } catch (Exception ignored) { }
                            }
                            @Override public void onConfigureFailed(CameraCaptureSession ses) { }
                        }, h);
                    } catch (Exception ignored) { }
                }
                @Override public void onDisconnected(CameraDevice d) { try { d.close(); } catch (Exception ignored) { } cam = null; }
                @Override public void onError(CameraDevice d, int e) { try { d.close(); } catch (Exception ignored) { } cam = null; }
            }, h);
        } catch (Exception ignored) { }
    }

    /* ---------- हर 5 सेकंड: स्क्रीन + चेहरा → Firebase ---------- */
    private void tick() {
        if (finished) return;
        String scr = "", face = "";
        try { if (scrHeld != null) scr = scrJpeg(scrHeld); } catch (Exception ignored) { }
        try { if (camHeld != null) face = camJpeg(camHeld); } catch (Exception ignored) { }
        if (!scr.isEmpty() || !face.isEmpty()) {
            final int i = idx++;
            final String a = scr, b = face;
            net.execute(() -> sendFrame(i, a, b));
        }
        if (System.currentTimeMillis() >= endAt) { finish(); return; }
        h.postDelayed(this::tick, EVERY_MS);
    }

    private String scrJpeg(Image im) {
        Image.Plane p = im.getPlanes()[0];
        ByteBuffer buf = p.getBuffer(); buf.rewind();
        int ps = p.getPixelStride(), rs = p.getRowStride();
        int w = im.getWidth(), hh = im.getHeight();
        Bitmap full = Bitmap.createBitmap(rs / ps, hh, Bitmap.Config.ARGB_8888);
        full.copyPixelsFromBuffer(buf);
        Bitmap crop = Bitmap.createBitmap(full, 0, 0, Math.min(w, full.getWidth()), hh);
        String out = b64(crop, 38);
        if (crop != full) crop.recycle();
        full.recycle();
        return out;
    }

    private String camJpeg(Image im) {
        int w = im.getWidth(), hh = im.getHeight();
        byte[] nv21 = new byte[w * hh * 3 / 2];
        Image.Plane[] pl = im.getPlanes();
        ByteBuffer y = pl[0].getBuffer(), u = pl[1].getBuffer(), v = pl[2].getBuffer();
        y.rewind(); u.rewind(); v.rewind();
        int yrs = pl[0].getRowStride(), yps = pl[0].getPixelStride();
        int pos = 0;
        for (int r = 0; r < hh; r++) for (int c = 0; c < w; c++) nv21[pos++] = y.get(r * yrs + c * yps);
        int urs = pl[1].getRowStride(), ups = pl[1].getPixelStride(), vrs = pl[2].getRowStride(), vps = pl[2].getPixelStride();
        for (int r = 0; r < hh / 2; r++) for (int c = 0; c < w / 2; c++) {
            nv21[pos++] = v.get(r * vrs + c * vps);
            nv21[pos++] = u.get(r * urs + c * ups);
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        new YuvImage(nv21, ImageFormat.NV21, w, hh, null).compressToJpeg(new Rect(0, 0, w, hh), 70, o);
        byte[] j = o.toByteArray();
        Bitmap bm = BitmapFactory.decodeByteArray(j, 0, j.length);
        if (bm == null) return "";
        Matrix m = new Matrix();
        m.postRotate(camRot);
        float k = FACE_W / (float) Math.min(bm.getWidth(), bm.getHeight());
        m.postScale(k, k);
        Bitmap rot = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
        String out = b64(rot, 45);
        if (rot != bm) rot.recycle();
        bm.recycle();
        return out;
    }

    private static String b64(Bitmap b, int q) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, q, o);
        return "data:image/jpeg;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
    }

    private void finish() {
        if (finished) return;
        finished = true;
        try { if (camSession != null) camSession.close(); } catch (Exception ignored) { }
        try { if (cam != null) cam.close(); } catch (Exception ignored) { }
        try { if (vd != null) vd.release(); } catch (Exception ignored) { }
        try { if (mp != null) mp.stop(); } catch (Exception ignored) { }
        try { if (scrHeld != null) scrHeld.close(); if (camHeld != null) camHeld.close(); } catch (Exception ignored) { }
        try { if (scrReader != null) scrReader.close(); if (camReader != null) camReader.close(); } catch (Exception ignored) { }
        net.execute(() -> { patchDuty(false); MainActivity.screenEvent("end"); stopSelf(); });
    }

    @Override
    public void onDestroy() {
        running = false;
        net.shutdown();
        if (thread != null) thread.quitSafely();
        super.onDestroy();
    }

    /* ---------- Firestore REST (DutyService जैसा) ---------- */
    private static String now() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private void sendFrame(int i, String scr, String face) {
        try {
            if (idToken.isEmpty() || System.currentTimeMillis() > tokenExp) refreshToken();
            JSONObject f = new JSONObject()
                    .put("uid", new JSONObject().put("stringValue", uid))
                    .put("duty", new JSONObject().put("stringValue", dutyId))
                    .put("rec", new JSONObject().put("stringValue", rec))
                    .put("i", new JSONObject().put("integerValue", String.valueOf(i)))
                    .put("scr", new JSONObject().put("stringValue", scr))
                    .put("face", new JSONObject().put("stringValue", face))
                    .put("at", new JSONObject().put("timestampValue", now()));
            String url = "https://firestore.googleapis.com/v1/projects/" + PROJECT + "/databases/(default)/documents/scrRec?documentId="
                    + URLEncoder.encode(rec + "_" + i, "UTF-8");
            int code = call(url, "POST", new JSONObject().put("fields", f).toString());
            if (code == 401) idToken = "";
        } catch (Exception ignored) { }
    }

    private void patchDuty(boolean live) {
        try {
            if (idToken.isEmpty() || System.currentTimeMillis() > tokenExp) refreshToken();
            JSONObject f = new JSONObject()
                    .put("scrLive", new JSONObject().put("booleanValue", live))
                    .put("scrRec", new JSONObject().put("stringValue", rec))
                    .put("scrAt", new JSONObject().put("timestampValue", now()));
            String url = "https://firestore.googleapis.com/v1/projects/" + PROJECT + "/databases/(default)/documents/duty/"
                    + URLEncoder.encode(dutyId, "UTF-8")
                    + "?currentDocument.exists=true&updateMask.fieldPaths=scrLive&updateMask.fieldPaths=scrRec&updateMask.fieldPaths=scrAt";
            call(url, "PATCH", new JSONObject().put("fields", f).toString());
        } catch (Exception ignored) { }
    }

    private int call(String url, String method, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        if ("PATCH".equals(method)) c.setRequestProperty("X-HTTP-Method-Override", "PATCH");
        c.setRequestProperty("Authorization", "Bearer " + idToken);
        c.setRequestProperty("Content-Type", "application/json");
        c.setConnectTimeout(15000); c.setReadTimeout(20000);
        c.setDoOutput(true);
        try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes("UTF-8")); }
        int code = c.getResponseCode();
        c.disconnect();
        return code;
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
