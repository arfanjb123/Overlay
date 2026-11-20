package com.bench.overlay;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private EditText keyInput, urlInput, modelInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 60, 40, 40);

        TextView header = new TextView(this);
        header.setText("Scratchpad Overlay Setup");
        header.setTextSize(18f);
        root.addView(header);

        keyInput = new EditText(this);
        keyInput.setHint("API Key (Bearer token)");
        root.addView(keyInput);

        urlInput = new EditText(this);
        urlInput.setText("https://api.openai.com/v1/chat/completions");
        root.addView(urlInput);

        modelInput = new EditText(this);
        modelInput.setText("gpt-4o-mini");
        root.addView(modelInput);

        Button launchBtn = new Button(this);
        launchBtn.setText("Launch Floating Window");
        launchBtn.setOnClickListener(v -> startOverlay());
        root.addView(launchBtn);

        setContentView(root);
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
            return;
        }
        Intent svc = new Intent(this, OverlayService.class);
        svc.putExtra("apiKey", keyInput.getText().toString().trim());
        svc.putExtra("apiUrl", urlInput.getText().toString().trim());
        svc.putExtra("model", modelInput.getText().toString().trim());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        finish();
    }
}
