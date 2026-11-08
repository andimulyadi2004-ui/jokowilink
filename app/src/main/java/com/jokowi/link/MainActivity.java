package com.jokowi.link;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final int BG = 0xFF15131C;

    boolean tv, diTerminal, memulai;
    float f = 1f, dens, fontSp = 12f;
    volatile Session sesi;
    volatile ChannelShell shell;
    volatile OutputStream shOut;
    volatile int lastC = 80, lastR = 24;
    final ExecutorService tulisan = Executors.newSingleThreadExecutor();
    final Handler ui = new Handler(Looper.getMainLooper());

    EditText etHost, etPort, etUser, etPass;
    TextView status;
    Button btnConnect, btnCtrl;
    TermView tvw;

    int dp(float v) { return (int) (v * f * dens); }

    float px() {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSp * f, getResources().getDisplayMetrics());
    }

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

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dens = getResources().getDisplayMetrics().density;
        UiModeManager um = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        tv = um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        f = tv ? 1.35f : 1f;
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        tampilLogin();
    }

    // ---------- layar login ----------
    void tampilLogin() {
        diTerminal = false;
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
        sub.setText("Terminal ke Termux pemilik. cd, nano, python, semuanya jalan di sana.");
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
        kiri.addView(status);
        setStatus("Belum tersambung", 0xFF9A93AA);

        btnConnect = tombol("Sambungkan", 0xFF4CC9F0, v -> sambung());
        kiri.addView(btnConnect);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.addView(kiri);
        setContentView(sv);
    }

    void sambung() {
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
                JSch j = new JSch();
                Session s = j.getSession(user, host, port);
                s.setPassword(pass);
                s.setConfig("StrictHostKeyChecking", "no");
                s.setServerAliveInterval(15000);
                s.connect(8000);
                sesi = s;
                ui.post(this::tampilTerminal);
            } catch (Exception e) {
                ui.post(() -> setStatus("Gagal: " + e.getMessage(), 0xFFFF6B8A));
            }
        }).start();
    }

    // ---------- layar terminal ----------
    void tampilTerminal() {
        diTerminal = true;
        memulai = false;
        shell = null;
        shOut = null;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        tvw = new TermView(this, px());
        tvw.ujung = new TermView.Ujung() {
            @Override public void kirim(byte[] b) { tulis(b); }
            @Override public void ukuran(int c, int r) { onUkuran(c, r); }
        };
        tvw.ctrlDipakai = this::perbaruiCtrl;
        root.addView(tvw, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(barisTombol(), new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        tvw.requestFocus();
        tvw.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && tvw != null) imm.showSoftInput(tvw, 0);
        }, 350);
    }

    void onUkuran(int c, int r) {
        lastC = c;
        lastR = r;
        if (shell == null) {
            if (!memulai) {
                memulai = true;
                mulaiShell(c, r);
            }
        } else {
            final ChannelShell s = shell;
            new Thread(() -> { try { s.setPtySize(c, r, 0, 0); } catch (Exception ignored) {} }).start();
        }
    }

    void mulaiShell(final int c, final int r) {
        final TermView t = tvw;
        final Session s = sesi;
        new Thread(() -> {
            try {
                ChannelShell ch = (ChannelShell) s.openChannel("shell");
                ch.setPtyType("xterm", c, r, 0, 0);
                InputStream in = ch.getInputStream();
                OutputStream os = ch.getOutputStream();
                ch.connect(8000);
                shOut = os;
                shell = ch;
                if (lastC != c || lastR != r) ch.setPtySize(lastC, lastR, 0, 0);
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) >= 0) t.feed(buf, 0, n);
            } catch (Exception e) {
                t.feedText("\r\n[error: " + e.getMessage() + "]\r\n");
            }
            t.feedText("\r\n[sesi ditutup. Tekan Back untuk kembali]\r\n");
        }).start();
    }

    void tulis(final byte[] b) {
        tulisan.execute(() -> {
            try {
                OutputStream o = shOut;
                if (o != null) {
                    o.write(b);
                    o.flush();
                }
            } catch (Exception ignored) {}
        });
    }

    void perbaruiCtrl() {
        if (btnCtrl == null || tvw == null) return;
        btnCtrl.setBackground(kotak(tvw.ctrl ? 0xFFFFC857 : 0xFF3A3547, 10));
        btnCtrl.setTextColor(tvw.ctrl ? 0xFF1A1620 : Color.WHITE);
    }

    Button tambah(LinearLayout row, String t, View.OnClickListener o) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14 * f);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setBackground(kotak(0xFF3A3547, 10));
        b.setOnClickListener(o);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(42));
        p.setMargins(dp(3), dp(6), dp(3), dp(6));
        b.setLayoutParams(p);
        row.addView(b);
        return b;
    }

    HorizontalScrollView barisTombol() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setBackgroundColor(0xFF1E1A27);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), 0, dp(4), 0);
        hs.addView(row);

        tambah(row, "Esc", v -> tvw.kunciByte(27));
        tambah(row, "Tab", v -> tvw.kunciByte(9));
        btnCtrl = tambah(row, "Ctrl", v -> { tvw.ctrl = !tvw.ctrl; perbaruiCtrl(); });
        tambah(row, "^C", v -> tvw.kunciByte(3));
        tambah(row, "←", v -> tvw.panah('D'));
        tambah(row, "↑", v -> tvw.panah('A'));
        tambah(row, "↓", v -> tvw.panah('B'));
        tambah(row, "→", v -> tvw.panah('C'));
        tambah(row, "Home", v -> tvw.panah('H'));
        tambah(row, "End", v -> tvw.panah('F'));
        tambah(row, "PgUp", v -> tvw.kunci("\u001B[5~"));
        tambah(row, "PgDn", v -> tvw.kunci("\u001B[6~"));
        tambah(row, "|", v -> tvw.kunci("|"));
        tambah(row, "~", v -> tvw.kunci("~"));
        tambah(row, "/", v -> tvw.kunci("/"));
        tambah(row, "-", v -> tvw.kunci("-"));
        tambah(row, "Tempel", v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData d = cm == null ? null : cm.getPrimaryClip();
            if (d != null && d.getItemCount() > 0) {
                CharSequence t = d.getItemAt(0).coerceToText(this);
                tvw.kunci(t.toString().replace('\n', '\r'));
            }
        });
        tambah(row, "A−", v -> { fontSp = Math.max(8, fontSp - 1); tvw.setTextSizePx(px()); });
        tambah(row, "A+", v -> { fontSp = Math.min(30, fontSp + 1); tvw.setTextSizePx(px()); });
        return hs;
    }

    void tutupSesi() {
        final Session s = sesi;
        final ChannelShell c = shell;
        sesi = null;
        shell = null;
        shOut = null;
        memulai = false;
        diTerminal = false;
        new Thread(() -> {
            try { if (c != null) c.disconnect(); } catch (Exception ignored) {}
            try { if (s != null) s.disconnect(); } catch (Exception ignored) {}
        }).start();
    }

    @Override public void onBackPressed() {
        if (diTerminal) {
            tutupSesi();
            tampilLogin();
        } else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        tutupSesi();
        tulisan.shutdown();
        super.onDestroy();
    }
}
