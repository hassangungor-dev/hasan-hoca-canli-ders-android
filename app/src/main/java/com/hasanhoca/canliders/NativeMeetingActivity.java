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
import com.hasanhoca.canliders.meeting.MeshMeetingClient;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.audio.AudioDeviceModule;

import java.io.IOException;
import java.util.UUID;

/** Jitsi arayüzünden bağımsız, tablet odaklı öğretmen toplantı ekranı. */
public final class NativeMeetingActivity extends AppCompatActivity {
    private static final int CAPTURE_WIDTH = 1920;
    private static final int CAPTURE_HEIGHT = 1080;
    private static final int CAPTURE_FPS = 30;

    private TextView status;
    private Button shareButton;
    private Button recordButton;
    private PeerConnectionFactory peerFactory;
    private AudioDeviceModule audioDeviceModule;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private EglBase rendererEgl;
    private SurfaceViewRenderer remoteRenderer;
    private MeshMeetingClient meetingClient;
    private SharedScreenCapture screenCapture;
    private MeetingRecordingSession recordingSession;
    private boolean recording;
    private boolean teacherMode;
    private String roomCode;

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

        roomCode = getIntent().getStringExtra("roomCode");
        teacherMode = getIntent().getBooleanExtra("teacherMode", false);
        TextView title = findViewById(R.id.meetingTitle);
        title.setText((teacherMode ? "Öğretmen odası • " : "Öğrenci odası • ")
                + (roomCode == null ? "Ders" : roomCode));
        if (!teacherMode) {
            shareButton.setVisibility(android.view.View.GONE);
            recordButton.setVisibility(android.view.View.GONE);
        }

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(getApplicationContext()).createInitializationOptions());
        audioDeviceModule = JavaAudioDeviceModule.builder(this)
                .setSampleRate(48_000)
                .setUseStereoInput(false)
                .setSamplesReadyCallback(samples -> {
                    MeetingRecordingSession active = recordingSession;
                    if (active != null) {
                        active.getAudioCallback().onWebRtcAudioRecordSamplesReady(samples);
                    }
                }).createAudioDeviceModule();
        peerFactory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory();
        audioSource = peerFactory.createAudioSource(new MediaConstraints());
        audioTrack = peerFactory.createAudioTrack("HASAN_HOCA_MIC", audioSource);
        initializeRemoteRenderer();
        status.setText("Hazır — ekranınızı paylaşabilirsiniz");

        if (!teacherMode) connectMeeting(null);

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
            connectMeeting(screenCapture.getVideoTrack());
            shareButton.setText("Paylaşımı durdur");
            recordButton.setEnabled(true);
            status.setText("Ekranınız öğrencilere paylaşılıyor");
        } catch (IOException error) {
            status.setText("Kayıt hazırlanamadı");
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initializeRemoteRenderer() {
        rendererEgl = EglBase.create();
        remoteRenderer = new SurfaceViewRenderer(this);
        remoteRenderer.init(rendererEgl.getEglBaseContext(), null);
        remoteRenderer.setEnableHardwareScaler(true);
        remoteRenderer.setMirror(false);
        android.widget.FrameLayout container = findViewById(R.id.videoContainer);
        container.addView(remoteRenderer, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void connectMeeting(VideoTrack sharedScreen) {
        if (meetingClient != null) meetingClient.close();
        meetingClient = new MeshMeetingClient(peerFactory, teacherMode, sharedScreen,
                audioTrack, new MeshMeetingClient.Listener() {
            @Override public void onReady() {
                runOnUiThread(() -> status.setText("Ders odasına bağlanıldı"));
            }
            @Override public void onRemoteVideo(String participantId, VideoTrack track) {
                runOnUiThread(() -> track.addSink(remoteRenderer));
            }
            @Override public void onParticipantLeft(String participantId) {
                runOnUiThread(() -> status.setText("Bir öğrenci dersten ayrıldı"));
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> status.setText("Bağlantı hatası: " + message));
            }
        });
        meetingClient.connect(BuildConfig.SIGNALING_URL, roomCode,
                UUID.randomUUID().toString());
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
        if (meetingClient != null) meetingClient.close();
        if (remoteRenderer != null) remoteRenderer.release();
        if (rendererEgl != null) rendererEgl.release();
        if (audioTrack != null) audioTrack.dispose();
        if (audioSource != null) audioSource.dispose();
        if (recordingSession != null) recordingSession.close();
        if (peerFactory != null) peerFactory.dispose();
        if (audioDeviceModule != null) audioDeviceModule.release();
        super.onDestroy();
    }
}
