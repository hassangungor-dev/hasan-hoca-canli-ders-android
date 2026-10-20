package com.hasanhoca.canliders;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.jitsi.meet.sdk.JitsiMeetActivity;

public class ScreenShareActivity extends JitsiMeetActivity {
    private boolean sharing = false;
    private Button shareButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addLessonControls();
    }

    private void addLessonControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);

        shareButton = new Button(this);
        shareButton.setText("Ekranı paylaş");
        shareButton.setTextColor(Color.WHITE);
        shareButton.setTextSize(14);
        shareButton.setAllCaps(false);
        shareButton.setElevation(dp(8));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(37, 99, 235));
        background.setCornerRadius(dp(24));
        shareButton.setBackground(background);

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52));
        buttonParams.setMarginEnd(dp(8));
        controls.addView(shareButton, buttonParams);

        Button recordButton = new Button(this);
        recordButton.setText("Kaydı aç");
        recordButton.setTextColor(Color.WHITE);
        recordButton.setTextSize(14);
        recordButton.setAllCaps(false);
        recordButton.setElevation(dp(8));
        GradientDrawable recordBackground = new GradientDrawable();
        recordBackground.setColor(Color.rgb(220, 38, 38));
        recordBackground.setCornerRadius(dp(24));
        recordButton.setBackground(recordBackground);
        controls.addView(recordButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52));
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(dp(16), dp(34), dp(16), dp(16));
        addContentView(controls, params);

        shareButton.setOnClickListener(v -> toggleScreenShare());
        recordButton.setOnClickListener(v -> openScreenRecorder());
    }

    private void openScreenRecorder() {
        Toast.makeText(this,
                "Panelde Ekran kaydedici düğmesine dokunun; ses için Medya ve mikrofonu seçin.",
                Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Settings.ACTION_QUICK_SETTINGS_SETTINGS));
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void toggleScreenShare() {
        sharing = !sharing;
        Intent intent = new Intent("org.jitsi.meet.TOGGLE_SCREEN_SHARE");
        intent.putExtra("enabled", sharing);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        shareButton.setText(sharing ? "Paylaşımı durdur" : "Ekranı paylaş");
        shareButton.setBackgroundColor(sharing ? Color.rgb(220, 38, 38) : Color.rgb(37, 99, 235));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
