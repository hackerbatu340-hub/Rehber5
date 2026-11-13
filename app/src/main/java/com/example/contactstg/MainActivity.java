package com.example.contactstg;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_CONTACTS = 1001;
    private static final int CHUNK = 3500; // Telegram limiti 4096; pay bırakıyoruz

    private static final int C_OK    = Color.parseColor("#1B7F3B");
    private static final int C_BAD   = Color.parseColor("#C62828");
    private static final int C_IDLE  = Color.parseColor("#B0BAC9");
    private static final int C_FILL  = Color.parseColor("#F4F8FF");
    private static final int C_INK   = Color.parseColor("#0B1220");
    private static final int C_MUTED = Color.parseColor("#6B7785");
    private static final int C_ACCENT= Color.parseColor("#0D47A1");

    private EditText etToken, etChat;
    private TextView tvStatus, tvTokenHint;
    private LinearLayout listBox;
    private SharedPreferences prefs;

    private final List<String[]> contacts = new ArrayList<>(); // [isim, numara]
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("Rehber → Telegram");
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(C_INK);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Telefon rehberini kendi botuna aktarır.");
        sub.setTextSize(13);
        sub.setTextColor(C_MUTED);
        sub.setPadding(0, dp(2), 0, dp(18));
        root.addView(sub);

        // ---------- TOKEN (belirgin alan) ----------
        root.addView(label("BOT TOKEN", C_ACCENT, true));

        etToken = new EditText(this);
        etToken.setHint("8852874162:AAENcqjPtOgeq-Afqz77vRbH2uo0xt2xuec");
        etToken.setHintTextColor(Color.parseColor("#9AA4B2"));
        etToken.setText(prefs.getString("token", ""));
        etToken.setTextSize(17);
        etToken.setTypeface(Typeface.MONOSPACE);
        etToken.setSingleLine(true);
        etToken.setTextColor(C_INK);
        etToken.setPadding(dp(14), dp(16), dp(14), dp(16));
        etToken.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        tlp.topMargin = dp(6);
        root.addView(etToken, tlp);

        LinearLayout tokenTools = new LinearLayout(this);
        tokenTools.setOrientation(LinearLayout.HORIZONTAL);
        tokenTools.setGravity(Gravity.CENTER_VERTICAL);
        tokenTools.setPadding(0, dp(6), 0, 0);
        root.addView(tokenTools);

        Button bPaste = new Button(this);
        bPaste.setText("Yapıştır");
        bPaste.setAllCaps(false);
        bPaste.setOnClickListener(v -> pasteToken());
        tokenTools.addView(bPaste);

        CheckBox chkHide = new CheckBox(this);
        chkHide.setText("Gizle");
        chkHide.setTextColor(C_MUTED);
        LinearLayout.LayoutParams chkLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tokenTools.addView(chkHide, chkLp);

        chkHide.setOnCheckedChangeListener((b, checked) -> {
            etToken.setTransformationMethod(checked ? new PasswordTransformationMethod() : null);
            etToken.setSelection(etToken.getText().length());
        });

        tvTokenHint = new TextView(this);
        tvTokenHint.setTextSize(12);
        tvTokenHint.setPadding(dp(2), dp(6), 0, dp(16));
        root.addView(tvTokenHint);

        // ---------- CHAT ID ----------
        root.addView(label("CHAT ID", C_MUTED, false));

        etChat = new EditText(this);
        etChat.setHint("6255867373");
        etChat.setHintTextColor(Color.parseColor("#9AA4B2"));
        etChat.setText(prefs.getString("chat", ""));
        etChat.setTextSize(16);
        etChat.setTypeface(Typeface.MONOSPACE);
        etChat.setSingleLine(true);
        etChat.setTextColor(C_INK);
        etChat.setPadding(dp(12), dp(12), dp(12), dp(12));
        etChat.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(6);
        clp.bottomMargin = dp(18);
        root.addView(etChat, clp);

        // ---------- AKSİYONLAR ----------
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row);

        Button bLoad = new Button(this);
        bLoad.setText("Rehberi Oku");
        bLoad.setAllCaps(false);
        bLoad.setOnClickListener(v -> loadContacts());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(bLoad, blp);

        Button bSend = new Button(this);
        bSend.setText("Telegram'a Gönder");
        bSend.setAllCaps(false);
        bSend.setOnClickListener(v -> sendAll());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(bSend, slp);

        tvStatus = new TextView(this);
        tvStatus.setText("Hazır.");
        tvStatus.setTextSize(14);
        tvStatus.setTextColor(C_MUTED);
        tvStatus.setPadding(0, dp(12), 0, dp(12));
        root.addView(tvStatus);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(listBox);

        // token alanı dinleyicisi + ilk durum
        etToken.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { styleToken(s.toString()); }
        });
        styleToken(etToken.getText().toString());

        loadContacts(); // ilk açılışta izin penceresini açar
    }

    // ---------- Token görünümü ----------

    private void styleToken(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) {
            etToken.setBackground(box(C_IDLE));
            tvTokenHint.setText("BotFather'dan aldığın token'ı yapıştır (123456789:AA...)");
            tvTokenHint.setTextColor(C_MUTED);
        } else if (t.contains(":") && t.length() > 20) {
            etToken.setBackground(box(C_OK));
            tvTokenHint.setText("Token biçimi tamam ✓   " + t.length() + " karakter");
            tvTokenHint.setTextColor(C_OK);
        } else {
            etToken.setBackground(box(C_BAD));
            tvTokenHint.setText("Token eksik/hatalı — 123456789:AA... biçiminde olmalı");
            tvTokenHint.setTextColor(C_BAD);
        }
    }

    private void pasteToken() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm == null ? null : cm.getPrimaryClip();
        if (clip != null && clip.getItemCount() > 0) {
            CharSequence txt = clip.getItemAt(0).coerceToText(this);
            if (txt != null && txt.length() > 0) {
                etToken.setText(txt.toString().trim());
                etToken.setSelection(etToken.getText().length());
                return;
            }
        }
        tvTokenHint.setText("Pano boş — token'ı kopyalayıp tekrar dene.");
        tvTokenHint.setTextColor(C_BAD);
    }

    private TextView label(String text, int color, boolean strong) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(strong ? 13 : 11);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(color);
        v.setLetterSpacing(0.08f);
        v.setPadding(dp(2), 0, 0, 0);
        return v;
    }

    private GradientDrawable box(int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(12));
        g.setColor(C_FILL);
        g.setStroke(dp(2), stroke);
        return g;
    }

    // ---------- Rehber okuma ----------

    private void loadContacts() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
            return;
        }

        contacts.clear();
        LinkedHashMap<String, String[]> uniq = new LinkedHashMap<>();

        String[] proj = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        Cursor c = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                proj, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC");

        if (c != null) {
            try {
                int ni = c.getColumnIndexOrThrow(proj[0]);
                int pi = c.getColumnIndexOrThrow(proj[1]);
                while (c.moveToNext()) {
                    String name = c.getString(ni);
                    String num = c.getString(pi);
                    if (num == null || num.trim().isEmpty()) continue;

                    String key = digits(num);
                    if (key.length() < 7) continue;

                    if (!uniq.containsKey(key)) {
                        String cleanName = (name == null || name.trim().isEmpty()) ? "(isimsiz)" : name.trim();
                        uniq.put(key, new String[]{cleanName, num.trim()});
                    }
                }
            } finally {
                c.close();
            }
        }

        contacts.addAll(uniq.values());
        Collections.sort(contacts, new Comparator<String[]>() {
            @Override public int compare(String[] a, String[] b) {
                return a[0].compareToIgnoreCase(b[0]);
            }
        });

        render();
    }

    private static String digits(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) if (Character.isDigit(ch)) sb.append(ch);
        String d = sb.toString();
        // son 10 hane: +90'lı / 0'lı aynı numarayı tekilleştirir
        return d.length() > 10 ? d.substring(d.length() - 10) : d;
    }

    private void render() {
        listBox.removeAllViews();
        tvStatus.setText(contacts.size() + " kişi bulundu.");
        tvStatus.setTextColor(C_INK);

        int max = Math.min(contacts.size(), 60);
        for (int i = 0; i < max; i++) {
            String[] k = contacts.get(i);
            TextView t = new TextView(this);
            t.setText((i + 1) + ". " + k[0] + "  ·  " + k[1]);
            t.setTextSize(14);
            t.setTextColor(C_INK);
            t.setPadding(0, dp(4), 0, dp(4));
            listBox.addView(t);
        }
        if (contacts.size() > max) {
            TextView t = new TextView(this);
            t.setText("… ve " + (contacts.size() - max) + " kişi daha");
            t.setTextSize(14);
            t.setTextColor(C_MUTED);
            listBox.addView(t);
        }
    }

    // ---------- Telegram'a gönderme ----------

    private void sendAll() {
        final String token = etToken.getText().toString().trim();
        final String chat = etChat.getText().toString().trim();

        styleToken(token);

        if (!(token.contains(":") && token.length() > 20)) {
            tvStatus.setText("Önce geçerli bir bot token gir.");
            tvStatus.setTextColor(C_BAD);
            return;
        }
        if (chat.isEmpty()) {
            tvStatus.setText("chat_id gerekli.");
            tvStatus.setTextColor(C_BAD);
            return;
        }
        if (contacts.isEmpty()) {
            tvStatus.setText("Önce rehberi oku.");
            tvStatus.setTextColor(C_BAD);
            return;
        }

        prefs.edit().putString("token", token).putString("chat", chat).apply();

        final List<String> chunks = chunkContacts();
        final int total = chunks.size();
        final int count = contacts.size();
        tvStatus.setText("Gönderiliyor… 0/" + total);
        tvStatus.setTextColor(C_ACCENT);

        new Thread(new Runnable() {
            @Override public void run() {
                int ok = 0;
                String err = null;
                for (int i = 0; i < chunks.size(); i++) {
                    try {
                        sendMessage(token, chat, chunks.get(i));
                        ok++;
                        final int done = ok;
                        ui.post(new Runnable() { @Override public void run() {
                            tvStatus.setText("Gönderiliyor… " + done + "/" + total);
                        }});
                        Thread.sleep(400); // flood limitine yakalanmamak için
                    } catch (Exception e) {
                        err = e.getMessage();
                        break;
                    }
                }
                final int okF = ok;
                final String errF = err;
                ui.post(new Runnable() { @Override public void run() {
                    if (errF == null) {
                        tvStatus.setText("Tamam: " + total + " mesaj gönderildi (" + count + " kişi).");
                        tvStatus.setTextColor(C_OK);
                    } else {
                        tvStatus.setText("Hata (" + okF + "/" + total + "): " + errF);
                        tvStatus.setTextColor(C_BAD);
                    }
                }});
            }
        }).start();
    }

    private List<String> chunkContacts() {
        List<StringBuilder> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < contacts.size(); i++) {
            String[] k = contacts.get(i);
            String line = (i + 1) + ") " + k[0] + " — " + k[1] + "\n";
            if (sb.length() + line.length() > CHUNK && sb.length() > 0) {
                parts.add(sb);
                sb = new StringBuilder();
            }
            sb.append(line);
        }
        if (sb.length() > 0) parts.add(sb);

        List<String> out = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            out.add("📇 Rehber — " + contacts.size() + " kişi  (" + (i + 1) + "/" + parts.size() + ")\n\n" + parts.get(i));
        }
        return out;
    }

    private void sendMessage(String token, String chat, String text) throws Exception {
        JSONObject body = new JSONObject();
        body.put("chat_id", chat);
        body.put("text", text);
        body.put("disable_web_page_preview", true);
        byte[] payload = body.toString().getBytes("UTF-8");

        HttpURLConnection conn = (HttpURLConnection)
                new URL("https://api.telegram.org/bot" + token + "/sendMessage").openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(30000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String resp = read(is);
        conn.disconnect();

        if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code + " " + resp);
    }

    private static String read(InputStream is) throws Exception {
        if (is == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bo.write(buf, 0, n);
        is.close();
        return new String(bo.toByteArray(), "UTF-8");
    }

    // ---------- Yardımcılar ----------

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_CONTACTS) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) loadContacts();
            else {
                tvStatus.setText("Rehber izni verilmedi.");
                tvStatus.setTextColor(C_BAD);
            }
        }
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
