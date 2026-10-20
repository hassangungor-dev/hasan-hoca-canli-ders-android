package com.hasanhoca.canliders;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.hasanhoca.canliders.capture.MeetingRecordingSession;
import com.hasanhoca.canliders.capture.SharedScreenCapture;

import org.webrtc.PeerConnectionFactory;

import java.io.IOException;

/** Jitsi arayüzünden bağımsız, tablet odaklı öğretmen toplantı ekranı. */
public final class NativeMeetingActivity extends AppCompatActivity {
    private static final int CAPTURE_WIDTH = 1920;
    private static final int CAPTURE_HEIGHT = 1080;
    private static final int CAPTURE_FPS = 30;

    private TextView status;
    private Button shareButton;
    private Button recordButton;
    private PeerConnectionFactory peerFactory;
    private SharedScreenCapture screenCapture;
    private MeetingRecordingSession recordingSession;
    private boolean recording;

    private final ActivityResultLauncher<Intent> screenPermission =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
                    status.setText("Ekran paylaşımı izni verilmedi");
                    return;
                }
                startSharedCapture(result.getData());
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_native_meeting);
        status = findViewById(R.id.meetingStatus);
        shareButton = findViewById(R.id.shareScreenButton);
        recordButton = findViewById(R.id.recordButton);
        Button endButton = findViewById(R.id.endLessonButton);

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(getApplicationContext()).createInitializationOptions());
        peerFactory = PeerConnectionFactory.builder().createPeerConnectionFactory();
        status.setText("Hazır — ekranınızı paylaşabilirsiniz");

        shareButton.setOnClickListener(v -> requestScreenShare());
        recordButton.setOnClickListener(v -> toggleRecording());
        endButton.setOnClickListener(v -> finishLesson());
    }

    private void requestScreenShare() {
        if (screenCapture != null) {
            screenCapture.release();
            screenCapture = null;
            shareButton.setText("Ekranı paylaş");
            recordButton.setEnabled(false);
            status.setText("Ekran paylaşımı durduruldu");
            return;
        }
        MediaProjectionManager manager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        screenPermission.launch(manager.createScreenCaptureIntent());
    }

    private void startSharedCapture(Intent permissionData) {
        try {
            recordingSession = new MeetingRecordingSession(this,
                    CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS);
            screenCapture = new SharedScreenCapture(this, peerFactory, permissionData,
                    recordingSession.getVideoSink());
            screenCapture.start(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS);
            shareButton.setText("Paylaşımı durdur");
            recordButton.setEnabled(true);
            status.setText("Ekranınız öğrencilere paylaşılıyor");
        } catch (IOException error) {
            status.setText("Kayıt hazırlanamadı");
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void toggleRecording() {
        recording = !recording;
        recordButton.setText(recording ? "Kaydı bitir" : "Kaydı başlat");
        status.setText(recording ? "Ekran ve mikrofon kaydediliyor" : "Kayıt tamamlanıyor…");
        if (!recording && recordingSession != null) {
            try {
                Uri saved = recordingSession.finishAndSave();
                recordingSession = null;
                status.setText("Kayıt Galeri'ye kaydedildi");
                Toast.makeText(this, "Ders kaydedildi: " + saved,
                        Toast.LENGTH_LONG).show();
            } catch (IOException error) {
                status.setText("Kayıt kaydedilemedi");
            }
        }
    }

    private void finishLesson() {
        if (recording && recordingSession != null) toggleRecording();
        finish();
    }

    @Override
    protected void onDestroy() {
        if (screenCapture != null) screenCapture.release();
        if (recordingSession != null) recordingSession.close();
        if (peerFactory != null) peerFactory.dispose();
        super.onDestroy();
    }
}
