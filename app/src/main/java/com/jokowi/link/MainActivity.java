package com.jokowi.link;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    static final int BG = 0xFF15131C;

    boolean tv;
    float f = 1f;
    float dens;
    volatile Session sesi;
    volatile ChannelExec kanal;
    int mode = 1;
    final String[] namaMode = {"Shell", "Python", "Node.js"};
    final String[] perintah = {"bash -s", "python -u -", "node -"};

    EditText etHost, etPort, etUser, etPass, etKode;
    TextView status, out;
    ScrollView outScroll;
    Button btnMode, btnConnect;
    final Handler ui = new Handler(Looper.getMainLooper());
    final StringBuilder buf = new StringBuilder();

    int dp(float v) { return (int) (v * f * dens); }

    StateListDrawable kotak(int warna, int r) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(warna);
        normal.setCornerRadius(dp(r));
        GradientDrawable fokus = new GradientDrawable();
        fokus.setColor(warna);
        fokus.setCornerRadius(dp(r));
        fokus.setStroke(dp(3), Color.WHITE);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, fokus);
        s.addState(new int[]{}, normal);
        return s;
    }

    EditText kolom(String hint, int tipe, String isi) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF7A7488);
        e.setTextColor(Color.WHITE);
        e.setTextSize(15 * f);
        e.setSingleLine(true);
        e.setInputType(tipe);
        e.setBackground(kotak(0xFF241F2E, 14));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setText(isi);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(8);
        e.setLayoutParams(p);
        return e;
    }

    Button tombol(String teks, int warna, View.OnClickListener o) {
        Button b = new Button(this);
        b.setText(teks);
        b.setAllCaps(false);
        b.setTextColor(0xFF1A1620);
        b.setTextSize(16 * f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(kotak(warna, 16));
        b.setOnClickListener(o);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(52));
        p.topMargin = dp(10);
        b.setLayoutParams(p);
        return b;
    }

    void setStatus(String s, int warna) {
        status.setText(s);
        status.setTextColor(warna);
    }

    void tulis(String s) {
        final String t = s.replaceAll("\u001B\\[[;?0-9]*[A-Za-z]", "").replace("\r", "");
        ui.post(() -> {
            buf.append(t);
            if (buf.length() > 40000) buf.delete(0, buf.length() - 30000);
            out.setText(buf.toString());
            outScroll.post(() -> outScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    void sambung(Runnable setelah) {
        final String host = etHost.getText().toString().trim();
        final String user = etUser.getText().toString().trim();
        final String pass = etPass.getText().toString();
        int pt = 8022;
        try { pt = Integer.parseInt(etPort.getText().toString().trim()); } catch (Exception ignored) {}
        final int port = pt;
        if (host.isEmpty() || user.isEmpty()) {
            Toast.makeText(this, "Isi IP dan username dulu", Toast.LENGTH_SHORT).show();
            return;
        }
        getSharedPreferences("p", 0).edit()
                .putString("h", host).putString("p", String.valueOf(port)).putString("u", user).apply();
        setStatus("Menyambung ke " + host + "...", 0xFFFFC857);
        new Thread(() -> {
            try {
                if (sesi != null && sesi.isConnected()) {
                    if (setelah != null) ui.post(setelah);
                    return;
                }
                JSch j = new JSch();
                Session s = j.getSession(user, host, port);
                s.setPassword(pass);
                s.setConfig("StrictHostKeyChecking", "no");
                s.setServerAliveInterval(15000);
                s.connect(8000);
                sesi = s;
                ui.post(() -> {
                    setStatus("Tersambung: " + user + "@" + host, 0xFF3DDC97);
                    btnConnect.setText("Putuskan");
                    if (setelah != null) setelah.run();
                });
            } catch (Exception e) {
                ui.post(() -> setStatus("Gagal: " + e.getMessage(), 0xFFFF6B8A));
            }
        }).start();
    }

    void putus() {
        final Session s = sesi;
        sesi = null;
        new Thread(() -> { try { if (s != null) s.disconnect(); } catch (Exception ignored) {} }).start();
        btnConnect.setText("Sambungkan");
        setStatus("Belum tersambung", 0xFF9A93AA);
    }

    void jalankan() {
        final String kode = etKode.getText().toString();
        if (kode.trim().isEmpty()) {
            Toast.makeText(this, "Tulis kodenya dulu", Toast.LENGTH_SHORT).show();
            return;
        }
        if (sesi == null || !sesi.isConnected()) {
            sambung(this::jalankan);
            return;
        }
        final int m = mode;
        tulis("\n>>> " + namaMode[m] + "\n");
        new Thread(() -> {
            try {
                ChannelExec c = (ChannelExec) sesi.openChannel("exec");
                kanal = c;
                c.setCommand(perintah[m]);
                c.setInputStream(new ByteArrayInputStream(kode.getBytes(StandardCharsets.UTF_8)));
                OutputStream o = new OutputStream() {
                    @Override public void write(int b) { write(new byte[]{(byte) b}, 0, 1); }
                    @Override public void write(byte[] b, int off, int len) {
                        tulis(new String(b, off, len, StandardCharsets.UTF_8));
                    }
                };
                c.setOutputStream(o);
                c.setErrStream(o);
                c.connect(5000);
                while (!c.isClosed()) Thread.sleep(80);
                tulis("\n[selesai, kode " + c.getExitStatus() + "]\n");
                c.disconnect();
            } catch (Exception e) {
                tulis("\n[error: " + e.getMessage() + "]\n");
            }
        }).start();
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dens = getResources().getDisplayMetrics().density;
        UiModeManager um = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        tv = um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        f = tv ? 1.35f : 1f;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        SharedPreferences sp = getSharedPreferences("p", 0);

        LinearLayout kiri = new LinearLayout(this);
        kiri.setOrientation(LinearLayout.VERTICAL);
        kiri.setPadding(dp(20), dp(tv ? 24 : 44), dp(20), dp(16));

        TextView judul = new TextView(this);
        judul.setText("Jokowi Link");
        judul.setTextSize(34 * f);
        judul.setTypeface(Typeface.DEFAULT_BOLD);
        judul.getPaint().setShader(new LinearGradient(0, 0, dp(240), 0,
                new int[]{0xFFFF7A59, 0xFFFFC857, 0xFF3DDC97, 0xFF4CC9F0},
                null, Shader.TileMode.CLAMP));
        kiri.addView(judul);

        TextView sub = new TextView(this);
        sub.setText("Ngoding lewat sini, jalannya di Termux pemilik.");
        sub.setTextColor(0xFF9A93AA);
        sub.setTextSize(14 * f);
        sub.setPadding(0, dp(4), 0, dp(12));
        kiri.addView(sub);

        etHost = kolom("IP Termux (mis. 192.168.1.5)", InputType.TYPE_CLASS_TEXT, sp.getString("h", ""));
        etPort = kolom("Port", InputType.TYPE_CLASS_NUMBER, sp.getString("p", "8022"));
        etUser = kolom("Username (ketik whoami di Termux)", InputType.TYPE_CLASS_TEXT, sp.getString("u", ""));
        etPass = kolom("Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, "");
        kiri.addView(etHost);
        kiri.addView(etPort);
        kiri.addView(etUser);
        kiri.addView(etPass);

        status = new TextView(this);
        status.setTextSize(14 * f);
        status.setPadding(0, dp(10), 0, 0);
        setStatus("Belum tersambung", 0xFF9A93AA);
        kiri.addView(status);

        btnConnect = tombol("Sambungkan", 0xFF4CC9F0, v -> {
            if (sesi != null && sesi.isConnected()) putus(); else sambung(null);
        });
        kiri.addView(btnConnect);

        btnMode = tombol("Bahasa: " + namaMode[mode], 0xFFFFC857, v -> {
            mode = (mode + 1) % 3;
            btnMode.setText("Bahasa: " + namaMode[mode]);
        });
        kiri.addView(btnMode);

        etKode = new EditText(this);
        etKode.setHint("Tulis kode di sini...");
        etKode.setHintTextColor(0xFF7A7488);
        etKode.setTextColor(Color.WHITE);
        etKode.setTypeface(Typeface.MONOSPACE);
        etKode.setTextSize(14 * f);
        etKode.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etKode.setGravity(Gravity.TOP | Gravity.START);
        etKode.setMinLines(tv ? 6 : 8);
        etKode.setBackground(kotak(0xFF241F2E, 14));
        etKode.setPadding(dp(14), dp(12), dp(14), dp(12));
        etKode.setText("print(\"Halo dari Termux!\")");
        LinearLayout.LayoutParams pk = new LinearLayout.LayoutParams(-1, -2);
        pk.topMargin = dp(12);
        etKode.setLayoutParams(pk);
        kiri.addView(etKode);

        kiri.addView(tombol("Jalankan", 0xFF3DDC97, v -> jalankan()));

        LinearLayout baris = new LinearLayout(this);
        baris.setOrientation(LinearLayout.HORIZONTAL);
        Button stop = tombol("Stop", 0xFFFF6B8A, v -> {
            if (kanal != null) {
                final ChannelExec k = kanal;
                new Thread(() -> { try { k.disconnect(); } catch (Exception ignored) {} }).start();
                tulis("\n[dihentikan]\n");
            }
        });
        Button bersih = tombol("Bersihkan", 0xFF8A8499, v -> {
            buf.setLength(0);
            out.setText("");
        });
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, dp(52), 1f);
        p1.topMargin = dp(10);
        p1.rightMargin = dp(5);
        LinearLayout.LayoutParams p2 = new LinearLayout.LayoutParams(0, dp(52), 1f);
        p2.topMargin = dp(10);
        p2.leftMargin = dp(5);
        stop.setLayoutParams(p1);
        bersih.setLayoutParams(p2);
        baris.addView(stop);
        baris.addView(bersih);
        kiri.addView(baris);

        out = new TextView(this);
        out.setTextColor(0xFF7CF29A);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextSize(13 * f);
        out.setPadding(dp(14), dp(12), dp(14), dp(12));
        out.setText("Output muncul di sini.");
        outScroll = new ScrollView(this);
        outScroll.addView(out);
        outScroll.setBackground(kotak(0xFF0B0A10, 14));
        outScroll.setFocusable(true);

        if (tv) {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setBackgroundColor(BG);
            ScrollView ks = new ScrollView(this);
            ks.setFillViewport(true);
            ks.addView(kiri);
            root.addView(ks, new LinearLayout.LayoutParams(0, -1, 1f));
            LinearLayout.LayoutParams po = new LinearLayout.LayoutParams(0, -1, 1f);
            po.setMargins(dp(8), dp(24), dp(24), dp(24));
            root.addView(outScroll, po);
            setContentView(root);
        } else {
            outScroll.setOnTouchListener((v, e) -> {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });
            LinearLayout dalam = new LinearLayout(this);
            dalam.setOrientation(LinearLayout.VERTICAL);
            dalam.addView(kiri);
            LinearLayout.LayoutParams po = new LinearLayout.LayoutParams(-1, dp(300));
            po.setMargins(dp(20), 0, dp(20), dp(24));
            dalam.addView(outScroll, po);
            ScrollView sv = new ScrollView(this);
            sv.setBackgroundColor(BG);
            sv.setFillViewport(true);
            sv.addView(dalam);
            setContentView(sv);
        }
    }

    @Override protected void onDestroy() {
        final Session s = sesi;
        new Thread(() -> { try { if (s != null) s.disconnect(); } catch (Exception ignored) {} }).start();
        super.onDestroy();
    }
}
