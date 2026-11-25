package com.masterconnector.app;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Duty चालू रहते फ़ोन लॉक होने पर भी हर 2 मिनट location जाँच।
 * duty शुरू होने की जगह से 30 km से ज़्यादा दूर → वर्कर के फ़ोन पर alert, और Firebase (duty दस्तावेज़) में
 * awayKm + जगह लिखी जाती है, जो Admin के होम पेज पर ⚠️ के साथ दिखती है।
 */
public class DutyService extends Service implements LocationListener {
    private static final String API_KEY = "AIzaSyApw_GttyXeEmHFvKlJjoQFI11KuL4ahxI";
    private static final String PROJECT = "master-connector-f2a0d";
    private static final double LIMIT_KM = 30.0;
    private static final long EVERY_MS = 2 * 60_000L;
    private static final String CH_RUN = "duty_run", CH_ALERT = "duty_alert";

    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private LocationManager lm;
    private SharedPreferences prefs;
    private String dutyId = "", refresh = "", idToken = "";
    private long tokenExp = 0, lastSent = 0;
    private double startLat = Double.NaN, startLng = Double.NaN;
    private boolean wasAway = false, startSaved = true;

    public static void start(Context c, String id, String lat, String lng, String refreshToken) {
        SharedPreferences p = c.getSharedPreferences("duty", MODE_PRIVATE);
        if (!id.equals(p.getString("id", ""))) {
            p.edit().clear().putString("id", id).putString("lat", lat == null ? "" : lat).putString("lng", lng == null ? "" : lng).apply();
        }
        if (refreshToken != null && !refreshToken.isEmpty()) p.edit().putString("rt", refreshToken).apply();
        Intent i = new Intent(c, DutyService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception e) { }
    }

    public static void stop(Context c) {
        c.getSharedPreferences("duty", MODE_PRIVATE).edit().clear().apply();
        c.stopService(new Intent(c, DutyService.class));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("duty", MODE_PRIVATE);
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(CH_RUN, "Duty चालू", NotificationManager.IMPORTANCE_LOW));
            nm.createNotificationChannel(new NotificationChannel(CH_ALERT, "Duty alert (30 km)", NotificationManager.IMPORTANCE_HIGH));
        }
    }

    private static double num(String s) { try { return (s == null || s.isEmpty()) ? Double.NaN : Double.parseDouble(s); } catch (Exception e) { return Double.NaN; } }

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        dutyId = prefs.getString("id", "");
        refresh = prefs.getString("rt", "");
        startLat = num(prefs.getString("lat", ""));
        startLng = num(prefs.getString("lng", ""));
        startSaved = !Double.isNaN(startLat);
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, runningNote(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(1, runningNote());
        } catch (Exception e) { stopSelf(); return START_NOT_STICKY; }
        if (dutyId.isEmpty() || refresh.isEmpty()) { stopSelf(); return START_NOT_STICKY; }
        try {
            lm.removeUpdates(this);
            boolean any = false;
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) { lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, EVERY_MS, 0, this, Looper.getMainLooper()); any = true; }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, EVERY_MS, 0, this, Looper.getMainLooper()); any = true; }
            if (!any) alert("📍 फ़ोन की Location बंद है", "ड्यूटी की जाँच के लिए Location चालू करें।");
        } catch (SecurityException e) { stopSelf(); return START_NOT_STICKY; }
        return START_STICKY;
    }

    @Override
    public void onLocationChanged(Location loc) {
        long now = System.currentTimeMillis();
        if (now - lastSent < EVERY_MS - 15_000) return;   // GPS और network दोनों से आए तो एक ही बार
        lastSent = now;
        double lat = loc.getLatitude(), lng = loc.getLongitude();
        boolean sendStart = false;
        if (Double.isNaN(startLat)) {
            startLat = lat; startLng = lng; sendStart = !startSaved;
            prefs.edit().putString("lat", String.valueOf(lat)).putString("lng", String.valueOf(lng)).apply();
        }
        float[] out = new float[1];
        Location.distanceBetween(startLat, startLng, lat, lng, out);
        double km = Math.round(out[0] / 100.0) / 10.0;
        boolean away = km > LIMIT_KM;
        if (away && !wasAway) alert("⚠️ आप ड्यूटी की जगह से " + km + " km दूर हैं", "एडमिन को सूचना भेज दी गई है। कृपया ड्यूटी की जगह पर लौटें।");
        wasAway = away;
        final boolean ss = sendStart;
        net.execute(() -> send(lat, lng, km, ss));
    }

    /** Firestore REST: duty/{id} में lastLat, lastLng, awayKm, lastAt */
    private void send(double lat, double lng, double km, boolean withStart) {
        try {
            if (idToken.isEmpty() || System.currentTimeMillis() > tokenExp) refreshToken();
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            JSONObject fields = new JSONObject()
                    .put("lastLat", new JSONObject().put("doubleValue", lat))
                    .put("lastLng", new JSONObject().put("doubleValue", lng))
                    .put("awayKm", new JSONObject().put("doubleValue", km))
                    .put("lastAt", new JSONObject().put("timestampValue", f.format(new Date())));
            StringBuilder q = new StringBuilder("?currentDocument.exists=true");
            for (String k : new String[]{"lastLat", "lastLng", "awayKm", "lastAt"}) q.append("&updateMask.fieldPaths=").append(k);
            if (withStart) {
                fields.put("startLat", new JSONObject().put("doubleValue", startLat)).put("startLng", new JSONObject().put("doubleValue", startLng));
                q.append("&updateMask.fieldPaths=startLat&updateMask.fieldPaths=startLng");
            }
            String url = "https://firestore.googleapis.com/v1/projects/" + PROJECT + "/databases/(default)/documents/duty/"
                    + URLEncoder.encode(dutyId, "UTF-8") + q;
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("X-HTTP-Method-Override", "PATCH");
            c.setRequestProperty("Authorization", "Bearer " + idToken);
            c.setRequestProperty("Content-Type", "application/json");
            c.setDoOutput(true);
            try (OutputStream o = c.getOutputStream()) { o.write(new JSONObject().put("fields", fields).toString().getBytes("UTF-8")); }
            int code = c.getResponseCode();
            if (code == 401) { idToken = ""; }
            // 403/404: duty खत्म हो गई या हटा दी गई (नियम अब लिखने नहीं देते) — जाँच बंद
            if (code == 403 || code == 404) { stop(this); }
            if (withStart && code == 200) startSaved = true;
            c.disconnect();
        } catch (Exception e) { /* इंटरनेट नहीं — अगली बार फिर */ }
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
        if (!rt.isEmpty() && !rt.equals(refresh)) { refresh = rt; prefs.edit().putString("rt", rt).apply(); }
        c.disconnect();
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        return b.toString("UTF-8");
    }

    @Override public void onStatusChanged(String p, int s, Bundle b) { }
    @Override public void onProviderEnabled(String p) { }
    @Override public void onProviderDisabled(String p) { }

    @Override
    public void onDestroy() {
        try { lm.removeUpdates(this); } catch (Exception e) { }
        net.shutdown();
        super.onDestroy();
    }

    private PendingIntent openApp() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification.Builder builder(String ch) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, ch) : new Notification.Builder(this);
    }

    private Notification runningNote() {
        return builder(CH_RUN).setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("ड्यूटी चालू है")
                .setContentText("हर 2 मिनट जगह जाँच · 30 km से दूर जाने पर alert")
                .setOngoing(true).setContentIntent(openApp()).build();
    }

    private void alert(String title, String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            Notification.Builder b = builder(CH_ALERT).setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                    .setAutoCancel(true).setContentIntent(openApp());
            if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH).setVibrate(new long[]{0, 400, 200, 400});
            nm.notify(2, b.build());
        } catch (Exception e) { }
    }
}
