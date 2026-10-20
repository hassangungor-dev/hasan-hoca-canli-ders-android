package com.hasanhoca.canliders;

import android.os.Bundle;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

        Intent meetingIntent = new Intent(this, NativeMeetingActivity.class);
        meetingIntent.putExtra("roomCode", "HasanHoca-" + safeRoom);
        meetingIntent.putExtra("displayName", TextUtils.isEmpty(displayName)
                ? (teacherMode ? "Hasan Hoca" : "Öğrenci") : displayName);
        meetingIntent.putExtra("teacherMode", teacherMode);
        startActivity(meetingIntent);
    }
}
