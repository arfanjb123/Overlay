package com.bench.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class OverlayService extends Service {
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private LinearLayout card;
    private LinearLayout bodyBox;
    private TextView dragTitle;
    private EditText qBox, notesBox;
    private TextView aiOut;
    private boolean disguised = false;
    private boolean secureFlag = true;
    private boolean focusable = true;
    private String apiKey = "", apiUrl = "", model = "gpt-4o-mini";
    private final Map<String, String> choiceStates = new LinkedHashMap<>();
    private final Map<String, Button> choiceButtons = new LinkedHashMap<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            apiKey = intent.getStringExtra("apiKey") != null ? intent.getStringExtra("apiKey") : "";
            apiUrl = intent.getStringExtra("apiUrl") != null ? intent.getStringExtra("apiUrl") : "https://api.openai.com/v1/chat/completions";
            model = intent.getStringExtra("model") != null ? intent.getStringExtra("model") : "gpt-4o-mini";
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startMinimalNotification();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
            820,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_SECURE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 40;
        params.y = 140;

        buildOverlayView();
        wm.addView(card, params);
    }

    private void startMinimalNotification() {
        String channelId = "notes_bg_sync";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(channelId, "Sync", NotificationManager.IMPORTANCE_MIN);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        Notification notif = new Notification.Builder(this, channelId)
            .setContentTitle("Notes")
            .setContentText("Ready")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .build();
        startForeground(101, notif);
    }

    private void buildOverlayView() {
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#E611161D"));
        card.setPadding(20, 16, 20, 16);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);

        dragTitle = new TextView(this);
        dragTitle.setText("Scratchpad [Drag]");
        dragTitle.setTextColor(Color.WHITE);
        dragTitle.setTextSize(13f);
        dragTitle.setPadding(6, 8, 16, 8);
        topBar.addView(dragTitle);

        Button btnSamaran = makeMiniBtn("Samaran", "#2D333B", v -> toggleSamaran());
        Button btnWipe = makeMiniBtn("Wipe", "#491D22", v -> wipeAll());
        Button btnPanic = makeMiniBtn("Panic", "#7A1C24", v -> panicHide());
        Button btnClose = makeMiniBtn("X", "#333333", v -> stopSelf());

        topBar.addView(btnSamaran);
        topBar.addView(btnWipe);
        topBar.addView(btnPanic);
        topBar.addView(btnClose);
        card.addView(topBar);

        LinearLayout stealthBar = new LinearLayout(this);
        stealthBar.setOrientation(LinearLayout.HORIZONTAL);
        Button btnSec = makeMiniBtn("Anti-Cap: ON", "#1F4E2B", v -> {
            secureFlag = !secureFlag;
            ((Button) v).setText(secureFlag ? "Anti-Cap: ON" : "Anti-Cap: OFF");
            updateWindowFlags();
        });
        Button btnFocus = makeMiniBtn("Keyboard: ON", "#1F4E2B", v -> {
            focusable = !focusable;
            ((Button) v).setText(focusable ? "Keyboard: ON" : "Keyboard: OFF");
            updateWindowFlags();
        });
        stealthBar.addView(btnSec);
        stealthBar.addView(btnFocus);
        card.addView(stealthBar);

        bodyBox = new LinearLayout(this);
        bodyBox.setOrientation(LinearLayout.VERTICAL);

        qBox = new EditText(this);
        qBox.setHint("Paste or type question...");
        qBox.setTextColor(Color.WHITE);
        qBox.setHintTextColor(Color.GRAY);
        qBox.setTextSize(12f);
        qBox.setMaxLines(4);
        bodyBox.addView(qBox);

        LinearLayout optRow = new LinearLayout(this);
        optRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String opt : new String[]{"A", "B", "C", "D", "E"}) {
            choiceStates.put(opt, "?");
            Button b = makeMiniBtn(opt + ":?", "#21262D", v -> cycleOption(opt));
            choiceButtons.put(opt, b);
            optRow.addView(b);
        }
        bodyBox.addView(optRow);

        notesBox = new EditText(this);
        notesBox.setHint("Working notes / steps...");
        notesBox.setTextColor(Color.LTGRAY);
        notesBox.setHintTextColor(Color.GRAY);
        notesBox.setTextSize(12f);
        notesBox.setMaxLines(3);
        bodyBox.addView(notesBox);

        Button btnSolve = makeMiniBtn("Solve with AI", "#1F6FEB", v -> runAiAssist());
        bodyBox.addView(btnSolve);

        ScrollView scroll = new ScrollView(this);
        aiOut = new TextView(this);
        aiOut.setText("Ready.");
        aiOut.setTextColor(Color.parseColor("#D0D7DE"));
        aiOut.setTextSize(12f);
        aiOut.setPadding(6, 10, 6, 10);
        scroll.addView(aiOut);
        bodyBox.addView(scroll, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 240
        ));

        card.addView(bodyBox);
        enableDrag(dragTitle);
    }

    private Button makeMiniBtn(String label, String hexBg, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(10f);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(hexBg));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(4, 4, 4, 4);
        b.setLayoutParams(lp);
        b.setPadding(12, 6, 12, 6);
        b.setOnClickListener(listener);
        return b;
    }

    private void enableDrag(View handle) {
        handle.setOnTouchListener(new View.OnTouchListener() {
            int initX, initY;
            float touchX, touchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initX = params.x;
                        initY = params.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initX + (int) (event.getRawX() - touchX);
                        params.y = initY + (int) (event.getRawY() - touchY);
                        wm.updateViewLayout(card, params);
                        return true;
                }
                return false;
            }
        });
    }

    private void updateWindowFlags() {
        int flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        if (secureFlag) flags |= WindowManager.LayoutParams.FLAG_SECURE;
        if (!focusable) flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.flags = flags;
        wm.updateViewLayout(card, params);
    }

    private void cycleOption(String opt) {
        String cur = choiceStates.get(opt);
        String next = "?".equals(cur) ? "OK" : ("OK".equals(cur) ? "X" : "?");
        choiceStates.put(opt, next);
        Button b = choiceButtons.get(opt);
        if (b != null) {
            b.setText(opt + ":" + next);
            if ("OK".equals(next)) b.setBackgroundColor(Color.parseColor("#1F4E2B"));
            else if ("X".equals(next)) b.setBackgroundColor(Color.parseColor("#491D22"));
            else b.setBackgroundColor(Color.parseColor("#21262D"));
        }
    }

    private void toggleSamaran() {
        disguised = !disguised;
        if (disguised) {
            dragTitle.setText("Quick Memo");
            dragTitle.setTextColor(Color.DKGRAY);
            card.setBackgroundColor(Color.parseColor("#CCF2F2F2"));
            qBox.setTextColor(Color.DKGRAY);
            notesBox.setTextColor(Color.DKGRAY);
            aiOut.setTextColor(Color.DKGRAY);
        } else {
            dragTitle.setText("Scratchpad [Drag]");
            dragTitle.setTextColor(Color.WHITE);
            card.setBackgroundColor(Color.parseColor("#E611161D"));
            qBox.setTextColor(Color.WHITE);
            notesBox.setTextColor(Color.LTGRAY);
            aiOut.setTextColor(Color.parseColor("#D0D7DE"));
        }
    }

    private void panicHide() {
        card.setVisibility(View.GONE);
        mainHandler.postDelayed(() -> {
            if (card != null) card.setVisibility(View.VISIBLE);
        }, 8000);
    }

    private void wipeAll() {
        qBox.setText("");
        notesBox.setText("");
        aiOut.setText("");
        for (String opt : choiceStates.keySet()) {
            choiceStates.put(opt, "?");
            Button b = choiceButtons.get(opt);
            if (b != null) {
                b.setText(opt + ":?");
                b.setBackgroundColor(Color.parseColor("#21262D"));
            }
        }
    }

    private void runAiAssist() {
        String q = qBox.getText().toString().trim();
        String notes = notesBox.getText().toString().trim();
        if (q.isEmpty() && notes.isEmpty()) {
            aiOut.setText("No question text.");
            return;
        }
        if (apiKey.isEmpty()) {
            aiOut.setText("Missing API Key.");
            return;
        }

        StringBuilder opts = new StringBuilder();
        for (Map.Entry<String, String> e : choiceStates.entrySet()) {
            opts.append(e.getKey()).append("=").append(e.getValue()).append(" ");
        }
        final String prompt = "Question:\n" + q + "\nOptions: " + opts + "\nNotes:\n" + notes;
        aiOut.setText("Querying...");

        new Thread(() -> {
            try {
                JSONObject sysMsg = new JSONObject()
                    .put("role", "system")
                    .put("content", "Provide concise step-by-step reasoning and the final answer.");
                JSONObject usrMsg = new JSONObject()
                    .put("role", "user")
                    .put("content", prompt);
                JSONObject body = new JSONObject()
                    .put("model", model)
                    .put("temperature", 0.2)
                    .put("messages", new JSONArray().put(sysMsg).put(usrMsg));

                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(30000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                BufferedReader br = new BufferedReader(new InputStreamReader(
                    code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream(),
                    StandardCharsets.UTF_8
                ));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);

                if (code >= 200 && code < 300) {
                    JSONObject resp = new JSONObject(sb.toString());
                    String reply = resp.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim();
                    mainHandler.post(() -> aiOut.setText(reply));
                } else {
                    mainHandler.post(() -> aiOut.setText("HTTP " + code + ": " + sb));
                }
            } catch (Exception ex) {
                mainHandler.post(() -> aiOut.setText("Error: " + ex.getMessage()));
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wm != null && card != null) wm.removeView(card);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
