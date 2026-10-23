package com.hasanhoca.canliders;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.ScrollView;
import android.net.Uri;
import org.jitsi.meet.sdk.JitsiMeet;
import java.util.ArrayList;
import java.util.List;
import android.widget.HorizontalScrollView;
import android.app.AlertDialog;
import org.jitsi.meet.sdk.BroadcastEvent;
import java.util.Map;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
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
    private boolean recordPending;
    private Button shareButton;
    private Button recordButton;
    private MediaProjectionManager projectionManager;
    private boolean connected;
    private boolean requestShareOnJoin;
    private String localParticipantId;
    private AlertDialog participantsDialog;
    private String participantsRequestId;
    private final ArrayList<String> chatLines = new ArrayList<>();
    private TextView chatText;
    private boolean audioMuted;
    private boolean videoMuted;
    private Button microphoneButton;
    private Button cameraButton;
    private TextView lessonStatus;
    private LinearLayout header;
    private HorizontalScrollView toolbar;
    private final BroadcastReceiver meetingReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ScreenRecorderService.ACTION_STATE.equals(intent.getAction())) {
                recordPending = false;
                recording = intent.getBooleanExtra("active",false);
                recordButton.setText(recording ? "Kayıt açık" : "Daha fazla");
                return;
            }
            BroadcastEvent event = new BroadcastEvent(intent);
            Map<String, Object> data = event.getData();
            if (event.getType() == null) return;
            switch (event.getType()) {
                case CONFERENCE_JOINED:
                    connected = true;
                    lessonStatus.setText("Canlı ders • " + getIntent().getStringExtra("lessonCode") + " · 1.0.11");
                    requestParticipants();
                    if (requestShareOnJoin) { requestShareOnJoin = false; showShareMenu(); }
                    break;
                case AUDIO_MUTED_CHANGED:
                    audioMuted = Boolean.TRUE.equals(data.get("muted"));
                    microphoneButton.setText(audioMuted ? "Mikrofonu aç" : "Sessize al");
                    break;
                case VIDEO_MUTED_CHANGED:
                    Object muted = data.get("muted");
                    videoMuted = Boolean.TRUE.equals(muted)
                            || (muted instanceof Number && ((Number) muted).intValue() != 0);
                    cameraButton.setText(videoMuted ? "Kamerayı aç" : "Kamerayı kapat");
                    break;
                case PARTICIPANTS_INFO_RETRIEVED:
                    if (participantsRequestId != null && participantsRequestId.equals(data.get("requestId"))) {
                        Object raw = data.get("participantsInfo");
                        StringBuilder names = new StringBuilder();
                        if (raw instanceof List) for (Object item : (List<?>) raw) {
                            if (!(item instanceof Map)) continue;
                            Map<?, ?> person = (Map<?, ?>) item;
                            if (Boolean.TRUE.equals(person.get("isLocal"))) localParticipantId = String.valueOf(person.get("participantId"));
                            Object name = person.get("displayName");
                            if (name == null) name = person.get("name");
                            names.append(name == null ? "Katılımcı" : name).append("\n");
                        }
                        if (participantsDialog != null && participantsDialog.isShowing())
                            participantsDialog.setMessage(names.length() == 0 ? "Katılımcı bilgisi alınamadı." : names.toString());
                    }
                    break;
                case SCREEN_SHARE_TOGGLED:
                    if (localParticipantId != null && localParticipantId.equals(String.valueOf(data.get("participantId")))) {
                        sharing = Boolean.TRUE.equals(data.get("sharing"));
                        shareButton.setText(sharing ? "Paylaşımı durdur" : "Paylaş");
                    }
                    break;
                case CHAT_MESSAGE_RECEIVED:
                    chatLines.add(String.valueOf(data.get("message")));
                    if (chatText != null) chatText.setText(android.text.TextUtils.join("\n\n",chatLines));
                    break;
                case CONFERENCE_TERMINATED:
                    connected = false;
                    break;
                default: break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        JitsiMeet.setDefaultConferenceOptions(MainActivity.conferenceDefaults().build());
        super.onCreate(savedInstanceState);
        requestShareOnJoin = getIntent().getBooleanExtra("shareOnJoin",false);
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        videoMuted = !getIntent().getBooleanExtra("teacherMode", false);
        addLessonControls();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ScreenRecorderService.ACTION_STATE);
        for (BroadcastEvent.Type type : BroadcastEvent.Type.values()) filter.addAction(type.getAction());
        LocalBroadcastManager.getInstance(this).registerReceiver(meetingReceiver, filter);
    }

    private void addLessonControls() {
        // Reserve real space for native controls instead of covering the video.
        ViewGroup content = findViewById(android.R.id.content);
        View conference = content.getChildAt(0);
        content.removeView(conference);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(17, 20, 27));
        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(10), dp(20), dp(10));
        TextView title = new TextView(this);
        title.setText("HASAN HOCA  |  Canlı Ders 1.0.11");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title);
        lessonStatus = new TextView(this);
        lessonStatus.setText("Derse bağlanıyor…");
        lessonStatus.setTextColor(Color.rgb(160, 174, 192));
        header.addView(lessonStatus);
        shell.addView(header);
        shell.addView(conference, new LinearLayout.LayoutParams(-1, 0, 1));

        toolbar = new HorizontalScrollView(this);
        toolbar.setFillViewport(true);
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(10), dp(8), dp(10), dp(8));
        microphoneButton = makeButton("Sessize al", Color.rgb(45, 51, 63));
        cameraButton = makeButton(videoMuted ? "Kamerayı aç" : "Kamerayı kapat", Color.rgb(45, 51, 63));
        shareButton = makeButton("Paylaş", Color.rgb(22, 140, 77));
        Button chat = makeButton("Sohbet", Color.rgb(45, 51, 63));
        Button participants = makeButton("Katılımcılar", Color.rgb(45, 51, 63));
        recordButton = makeButton("Daha fazla", Color.rgb(45, 51, 63));
        Button leave = makeButton("Ayrıl", Color.rgb(195, 48, 56));
        Button[] buttons = {microphoneButton, cameraButton, chat, participants, shareButton, recordButton, leave};
        int[] icons = {android.R.drawable.ic_btn_speak_now, android.R.drawable.presence_video_online,
            android.R.drawable.ic_dialog_email, android.R.drawable.ic_menu_myplaces,
            android.R.drawable.ic_menu_share, android.R.drawable.ic_menu_more, android.R.drawable.ic_menu_close_clear_cancel};
        for (int n=0;n<buttons.length;n++) {
            Button button = buttons[n];
            android.graphics.drawable.Drawable icon = getResources().getDrawable(icons[n],getTheme()).mutate();
            icon.setTint(Color.WHITE); icon.setBounds(0,0,dp(22),dp(22));
            button.setCompoundDrawables(null,icon,null,null);
            button.setCompoundDrawablePadding(dp(4));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(74));
            params.setMarginEnd(dp(8));
            controls.addView(button, params);
        }
        toolbar.addView(controls);
        shell.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        content.addView(shell, new ViewGroup.LayoutParams(-1, -1));
        microphoneButton.setOnClickListener(v -> sendMuted("org.jitsi.meet.SET_AUDIO_MUTED", !audioMuted));
        cameraButton.setOnClickListener(v -> sendMuted("org.jitsi.meet.SET_VIDEO_MUTED", !videoMuted));
        chat.setOnClickListener(v -> showChat());
        participants.setOnClickListener(v -> {
            if (!requireConnection()) return;
            participantsDialog = new AlertDialog.Builder(this).setTitle("Katılımcılar")
                .setMessage("Liste alınıyor…").setPositiveButton("Kapat",null).show();
            requestParticipants();
        });
        shareButton.setOnClickListener(v -> { if (sharing) toggleScreenShare(); else showShareMenu(); });
        recordButton.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Daha fazla")
            .setItems(new String[]{recording ? "Kaydı durdur" : "Yerel kaydı başlat", "Toplantı bilgileri", "Kamerayı çevir"},(d,w) -> {
                if (w == 0) toggleRecording();
                else if (w == 1) new AlertDialog.Builder(this).setTitle("Toplantı bilgileri")
                    .setMessage("Ders kodu: " + getIntent().getStringExtra("lessonCode") + "\nAltyapı: Jitsi Meet\nUygulama: 1.0.11")
                    .setPositiveButton("Kapat",null).show();
                else sendAction("org.jitsi.meet.TOGGLE_CAMERA");
            }).show());
        leave.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Dersten ayrıl?")
                .setMessage(recording ? "Devam eden kayıt da durdurulacak." : "Canlı dersten çıkılacak.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Ayrıl", (dialog, which) -> {
                    stopLessonRecording();
                    sendAction("org.jitsi.meet.HANG_UP");
                }).show());
    }

    private void sendAction(String action) {
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(action));
    }

    private void sendMuted(String action, boolean muted) {
        if (!requireConnection()) return;
        Intent intent = new Intent(action);
        intent.putExtra("muted", muted);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void stopLessonRecording() {
        if (recording || recordPending) {
            Intent stop = new Intent(this, ScreenRecorderService.class);
            stop.setAction(ScreenRecorderService.ACTION_STOP);
            startService(stop);
            recording = false;
        }
    }

    @Override public void onPictureInPictureModeChanged(boolean inPip, android.content.res.Configuration config) {
        super.onPictureInPictureModeChanged(inPip, config);
        if (header != null) header.setVisibility(inPip ? View.GONE : View.VISIBLE);
        if (toolbar != null) toolbar.setVisibility(inPip ? View.GONE : View.VISIBLE);
    }

    @Override public void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(meetingReceiver);
        if (isFinishing()) stopLessonRecording();
        super.onDestroy();
    }

    private Button makeButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setPadding(dp(14),dp(6),dp(14),dp(6));
        button.setAllCaps(false);
        button.setElevation(dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(24));
        button.setBackground(background);
        return button;
    }

    private void toggleRecording() {
        if (recordPending) return;
        if (!requireConnection()) return;
        if (android.os.Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this,"Yerel kayıt bu sürümde Android 10 ve üzeri içindir.",Toast.LENGTH_LONG).show(); return;
        }
        if (sharing) {
            new AlertDialog.Builder(this).setTitle("Ekran paylaşımı açık")
                    .setMessage("Bu sürümde yerel kayıt ve ekran paylaşımı birlikte kullanılamaz. Önce paylaşımı durdurun.")
                    .setPositiveButton("Tamam", null).show();
            return;
        }
        if (recording) {
            Intent stopIntent = new Intent(this, ScreenRecorderService.class);
            stopIntent.setAction(ScreenRecorderService.ACTION_STOP);
            startService(stopIntent);
            recording = false;
            recordButton.setText("Daha fazla");
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
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 3201) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                Intent open = new Intent(Intent.ACTION_VIEW).setDataAndType(data.getData(),getContentResolver().getType(data.getData()))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { startActivity(open); } catch (android.content.ActivityNotFoundException e) {
                    Toast.makeText(this,"Dosyayı açacak uygulama bulunamadı.",Toast.LENGTH_LONG).show();
                }
            }
            return;
        }
        if (requestCode == REQUEST_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, ScreenRecorderService.class);
                serviceIntent.setAction(ScreenRecorderService.ACTION_START);
                serviceIntent.putExtra(ScreenRecorderService.EXTRA_RESULT_CODE, resultCode);
                serviceIntent.putExtra(ScreenRecorderService.EXTRA_RESULT_DATA, data);
                ContextCompat.startForegroundService(this, serviceIntent);
                recordPending = true;
                recordButton.setText("Kayıt başlatılıyor…");
            } else {
                Toast.makeText(this, "Ekran kaydı izni verilmedi.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (resultCode == RESULT_CANCELED && sharing) {
            sharing = false; shareButton.setText("Paylaş");
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void toggleScreenShare() {
        if (!requireConnection()) return;
        if (recording || recordPending) {
            new AlertDialog.Builder(this).setTitle("Kayıt devam ediyor")
                    .setMessage("Ekran paylaşımını başlatmak için önce yerel kaydı durdurun.")
                    .setPositiveButton("Tamam", null).show();
            return;
        }
        sharing = !sharing;
        Intent intent = new Intent("org.jitsi.meet.TOGGLE_SCREEN_SHARE");
        intent.putExtra("enabled", sharing);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
        shareButton.setText(sharing ? "Paylaşımı durdur" : "Ekranı paylaş");
        shareButton.setBackgroundColor(sharing ? Color.rgb(220, 38, 38) : Color.rgb(37, 99, 235));
    }

    private boolean requireConnection() {
        if (connected) return true;
        Toast.makeText(this,"Önce derse bağlantının tamamlanmasını bekleyin.",Toast.LENGTH_SHORT).show();
        return false;
    }

    private void requestParticipants() {
        participantsRequestId = java.util.UUID.randomUUID().toString();
        Intent i = new Intent("org.jitsi.meet.RETRIEVE_PARTICIPANTS_INFO");
        i.putExtra("requestId",participantsRequestId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(i);
    }

    private void showChat() {
        if (!requireConnection()) return;
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(12),dp(20),dp(12));
        ScrollView scroll = new ScrollView(this); chatText = new TextView(this);
        chatText.setTextColor(Color.rgb(35,42,55)); chatText.setText(android.text.TextUtils.join("\n\n",chatLines));
        scroll.addView(chatText); box.addView(scroll,new LinearLayout.LayoutParams(-1,dp(180)));
        EditText message = new EditText(this); message.setHint("Mesajınız"); box.addView(message);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Sohbet").setView(box)
            .setNegativeButton("Kapat",null).setPositiveButton("Gönder",null).create();
        dialog.setOnDismissListener(d -> chatText = null);
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = message.getText().toString().trim(); if (value.isEmpty()) return;
            Intent i = new Intent("org.jitsi.meet.SEND_CHAT_MESSAGE"); i.putExtra("message",value);
            LocalBroadcastManager.getInstance(this).sendBroadcast(i);
            message.setText("");
        })); dialog.show();
    }

    private void showShareMenu() {
        if (!requireConnection()) return;
        new AlertDialog.Builder(this).setTitle("Paylaş")
            .setItems(new String[]{"Ekran", "Fotoğraflar", "Belge", "Beyaz tahta", "Web sitesi URL’si"},(d,w) -> {
                if (w == 0) toggleScreenShare();
                else if (w == 3) {
                    new AlertDialog.Builder(this).setMessage("Tahtadaki çizimi canlı göstermek için önce Ekran paylaşımını başlatın, sonra Paylaş > Beyaz tahta’yı açın. Tahta bu cihazda çizilir.")
                        .setPositiveButton("Tahtayı aç",(a,b) -> startActivity(new Intent(this,WhiteboardActivity.class)))
                        .setNegativeButton("Vazgeç",null).show();
                } else if (w == 4) {
                    EditText url = new EditText(this); url.setHint("https://");
                    new AlertDialog.Builder(this).setTitle("Web sitesi URL’si").setView(url)
                        .setPositiveButton("Aç",(a,b) -> {
                            String value = url.getText().toString().trim();
                            if (!value.startsWith("https://") && !value.startsWith("http://")) value = "https://" + value;
                            try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(value))); }
                            catch (android.content.ActivityNotFoundException e) { Toast.makeText(this,"Tarayıcı bulunamadı.",Toast.LENGTH_SHORT).show(); }
                        }).setNegativeButton("Vazgeç",null).show();
                } else {
                    new AlertDialog.Builder(this).setMessage("Dosyayı canlı göstermek için önce Ekran paylaşımını başlatın. Dosya seçilip görüntüleyiciyle açılacak.")
                        .setPositiveButton("Dosya seç",(a,b) -> {
                            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE);
                            i.setType(w == 1 ? "image/*" : "application/pdf");
                            try { startActivityForResult(i,3201); }
                            catch (android.content.ActivityNotFoundException e) { Toast.makeText(this,"Dosya seçici bulunamadı.",Toast.LENGTH_SHORT).show(); }
                        }).setNegativeButton("Vazgeç",null).show();
                }
            }).setNegativeButton("Kapat",null).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
