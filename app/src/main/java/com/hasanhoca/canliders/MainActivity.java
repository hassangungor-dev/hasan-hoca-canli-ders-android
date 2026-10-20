package com.hasanhoca.canliders;

import android.os.Bundle;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private EditText roomInput;
    private EditText nameInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        roomInput = findViewById(R.id.roomInput);
        nameInput = findViewById(R.id.nameInput);
        Button joinButton = findViewById(R.id.joinButton);
        Button teacherButton = findViewById(R.id.teacherButton);
        TextView privacyNote = findViewById(R.id.privacyNote);

        joinButton.setOnClickListener(v -> joinMeeting(false));
        teacherButton.setOnClickListener(v -> joinMeeting(true));
        privacyNote.setOnClickListener(v -> Toast.makeText(this,
                "Kayıt ve ekran paylaşımı başlamadan önce Android izin penceresi gösterilir.",
                Toast.LENGTH_LONG).show());
    }

    private void joinMeeting(boolean teacherMode) {
        String rawRoom = roomInput.getText().toString().trim();
        String displayName = nameInput.getText().toString().trim();

        if (TextUtils.isEmpty(rawRoom)) {
            roomInput.setError("Ders kodunu yazın");
            roomInput.requestFocus();
            return;
        }

        String safeRoom = rawRoom.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9ğüşöçı-]", "-")
                .replaceAll("-+", "-");

        try {
            JitsiMeetConferenceOptions options = new JitsiMeetConferenceOptions.Builder()
                    .setServerURL(new URL("https://meet.jit.si"))
                    .setRoom("HasanHoca-" + safeRoom)
                    .setSubject("Hasan Hoca Canlı Ders")
                    .setAudioMuted(false)
                    .setVideoMuted(!teacherMode)
                    .setFeatureFlag("welcomepage.enabled", false)
                    .setFeatureFlag("prejoinpage.enabled", true)
                    .setFeatureFlag("android.screensharing.enabled", true)
                    .setFeatureFlag("pip.enabled", true)
                    .setFeatureFlag("pip-while-screen-sharing.enabled", true)
                    .setFeatureFlag("toolbox.enabled", true)
                    .setFeatureFlag("recording.enabled", true)
                    // meet.jit.si'nin Google doğrulamasından sonra resmi Jitsi
                    // uygulamasına gitmesini engelleyip yeniden bize dönmesini ister.
                    .setConfigOverride("deeplinking.android.appName", "Hasan Hoca Canlı Ders")
                    .setConfigOverride("deeplinking.android.appScheme", "hasanhocacanliders")
                    .setConfigOverride("deeplinking.android.appPackage", getPackageName())
                    .setConfigOverride("toolbarButtons", new String[] {
                            "microphone", "camera", "chat", "raisehand",
                            "participants-pane", "tileview", "screensharing",
                            "desktop", "overflowmenu", "hangup"
                    })
                    .setConfigOverride("displayName", TextUtils.isEmpty(displayName)
                            ? (teacherMode ? "Hasan Hoca" : "Öğrenci") : displayName)
                    .build();

            Intent meetingIntent = new Intent(this, ScreenShareActivity.class);
            meetingIntent.setAction("org.jitsi.meet.CONFERENCE");
            meetingIntent.putExtra("JitsiMeetConferenceOptions", options);
            startActivity(meetingIntent);
        } catch (MalformedURLException e) {
            Toast.makeText(this, "Toplantı sunucusu açılamadı.", Toast.LENGTH_LONG).show();
        }
    }
}
