package com.hasanhoca.canliders;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.jitsi.meet.sdk.JitsiMeetActivity;

public class ScreenShareActivity extends JitsiMeetActivity {
    private static final int REQUEST_MICROPHONE = 3101;
    private static final int REQUEST_SCREEN_CAPTURE = 3102;

    private boolean sharing = false;
    private boolean recording = false;
    private Button shareButton;
    private Button recordButton;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        addLessonControls();
    }

    private void addLessonControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);

        shareButton = makeButton("Ekranı paylaş", Color.rgb(37, 99, 235));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52));
        buttonParams.setMarginEnd(dp(8));
        controls.addView(shareButton, buttonParams);

        recordButton = makeButton("Kaydı başlat", Color.rgb(220, 38, 38));
        controls.addView(recordButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52));
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(dp(16), dp(34), dp(16), dp(16));
        addContentView(controls, params);

        shareButton.setOnClickListener(v -> toggleScreenShare());
        recordButton.setOnClickListener(v -> toggleRecording());
    }

    private Button makeButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setElevation(dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(24));
        button.setBackground(background);
        return button;
    }

    private void toggleRecording() {
        if (recording) {
            Intent stopIntent = new Intent(this, ScreenRecorderService.class);
            stopIntent.setAction(ScreenRecorderService.ACTION_STOP);
            startService(stopIntent);
            recording = false;
            recordButton.setText("Kaydı başlat");
            Toast.makeText(this, "Kayıt durduruldu. Video Galeri > Hasan Hoca klasöründe.", Toast.LENGTH_LONG).show();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            return;
        }
        requestScreenCapture();
    }

    private void requestScreenCapture() {
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_SCREEN_CAPTURE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MICROPHONE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestScreenCapture();
            } else {
                Toast.makeText(this, "Sesli kayıt için mikrofon izni gerekli.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, ScreenRecorderService.class);
                serviceIntent.setAction(ScreenRecorderService.ACTION_START);
                serviceIntent.putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, resultCode);
                serviceIntent.putExtra(ScreenRecorderService.EXTRA_RESULT_DATA, data);
                ContextCompat.startForegroundService(this, serviceIntent);
                recording = true;
                recordButton.setText("Kaydı durdur");
                Toast.makeText(this, "Sesli ekran kaydı başladı.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Ekran kaydı izni verilmedi.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
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
