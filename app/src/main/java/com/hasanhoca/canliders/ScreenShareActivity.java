package com.hasanhoca.canliders;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.jitsi.meet.sdk.BroadcastAction;
import org.jitsi.meet.sdk.JitsiMeetActivity;

public class ScreenShareActivity extends JitsiMeetActivity {
    private boolean sharing = false;
    private Button shareButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addShareButton();
    }

    private void addShareButton() {
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

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52));
        params.gravity = Gravity.TOP | Gravity.END;
        params.setMargins(dp(16), dp(34), dp(16), dp(16));
        addContentView(shareButton, params);

        shareButton.setOnClickListener(v -> toggleScreenShare());
    }

    private void toggleScreenShare() {
        sharing = !sharing;
        Intent intent = new Intent(BroadcastAction.Type.TOGGLE_SCREEN_SHARE.getAction());
        intent.putExtra("enabled", sharing);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        shareButton.setText(sharing ? "Paylaşımı durdur" : "Ekranı paylaş");
        shareButton.setBackgroundColor(sharing ? Color.rgb(220, 38, 38) : Color.rgb(37, 99, 235));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
