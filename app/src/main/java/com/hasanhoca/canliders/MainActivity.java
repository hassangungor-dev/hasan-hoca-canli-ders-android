package com.hasanhoca.canliders;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.jitsi.meet.sdk.JitsiMeet;
import org.jitsi.meet.sdk.JitsiMeetConferenceOptions;
import java.net.URL;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private String personalCode;
    private final int blue = Color.rgb(14, 113, 235);
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); return b;
    }
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        personalCode = getPreferences(MODE_PRIVATE).getString("personalCode", null);
        if (personalCode == null) {
            personalCode = "ders-" + UUID.randomUUID().toString().substring(0, 8);
            getPreferences(MODE_PRIVATE).edit().putString("personalCode", personalCode).apply();
        }
        JitsiMeet.setDefaultConferenceOptions(conferenceDefaults().build());
        showHome();
    }
    static JitsiMeetConferenceOptions.Builder conferenceDefaults() {
        return new JitsiMeetConferenceOptions.Builder()
            .setFeatureFlag("welcomepage.enabled", false)
            .setFeatureFlag("prejoinpage.enabled", false)
            .setFeatureFlag("toolbox.enabled", false)
            .setFeatureFlag("android.screensharing.enabled", true)
            .setFeatureFlag("pip.enabled", true)
            .setFeatureFlag("pip-while-screen-sharing.enabled", true)
            .setFeatureFlag("recording.enabled", false)
            // Keep every meeting/authentication step inside our embedded SDK.
            // Otherwise meet.jit.si may try to open the standalone Jitsi app
            // and Android sends the user to Google Play when it is not installed.
            .setConfigOverride("disableDeepLinking", true)
            .setConfigOverride("deeplinking.disabled", true);
    }
    private void showHome() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(249, 250, 252)); root.setFitsSystemWindows(true);
        TextView heading = text("Hasan Hoca  ·  Ana sayfa", 22, Color.rgb(35, 42, 55));
        heading.setPadding(dp(24), dp(22), dp(24), dp(18)); root.addView(heading);
        TextView version = text("Canlı Ders 1.0.11 · Doğrudan açılış", 14, Color.rgb(95, 104, 119));
        version.setPadding(dp(24), 0, dp(24), dp(12)); root.addView(version);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout area = new LinearLayout(this); area.setOrientation(LinearLayout.VERTICAL);
        area.setGravity(Gravity.CENTER); area.setPadding(dp(18), dp(24), dp(18), dp(24));
        LinearLayout first = new LinearLayout(this); first.setGravity(Gravity.CENTER);
        first.addView(tile("Yeni toplantı", android.R.drawable.presence_video_online, Color.rgb(235, 98, 69), () -> newMeeting(false)));
        first.addView(tile("Katıl", android.R.drawable.ic_input_add, blue, this::joinDialog)); area.addView(first);
        LinearLayout second = new LinearLayout(this); second.setGravity(Gravity.CENTER);
        second.addView(tile("Planla", android.R.drawable.ic_menu_my_calendar, blue, this::planMeeting));
        second.addView(tile("Ekran paylaş", android.R.drawable.ic_menu_share, blue, () -> newMeeting(true))); area.addView(second);
        area.addView(tile("Beyaz tahta", android.R.drawable.ic_menu_edit, blue,
            () -> startActivity(new Intent(this, WhiteboardActivity.class))));
        TextView room = text("Kişisel ders kodunuz: " + personalCode, 15, Color.rgb(70, 80, 95));
        room.setGravity(Gravity.CENTER); room.setPadding(0, dp(24), 0, dp(8)); area.addView(room);
        TextView note = text("Kamera kapalıyken genellikle daha az internet kullanılır.", 13, Color.rgb(95,104,119));
        note.setGravity(Gravity.CENTER); area.addView(note);
        scroll.addView(area); root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }
    private View tile(String label, int icon, int color, Runnable click) {
        LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER); tile.setPadding(dp(12), dp(14), dp(12), dp(14));
        tile.setLayoutParams(new LinearLayout.LayoutParams(dp(138), dp(126)));
        ImageButton image = new ImageButton(this); image.setImageResource(icon); image.setColorFilter(Color.WHITE);
        image.setBackground(background(color, 20)); image.setContentDescription(label);
        tile.addView(image,new LinearLayout.LayoutParams(dp(68),dp(68)));
        TextView t = text(label, 14, Color.rgb(45,53,66)); t.setPadding(0,dp(8),0,0); tile.addView(t);
        image.setOnClickListener(v -> click.run()); tile.setOnClickListener(v -> click.run()); return tile;
    }
    private EditText input(String hint, String value) {
        EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(true); e.setText(value); return e;
    }
    private void newMeeting(boolean share) {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(22),dp(12),dp(22),dp(16));
        EditText name = input("Adınız", "Hasan Hoca"); form.addView(name);
        Switch video = new Switch(this); video.setText("Video açık"); video.setChecked(false); form.addView(video);
        Switch personal = new Switch(this); personal.setText("Kişisel ders kodunu kullan"); personal.setChecked(true); form.addView(personal);
        EditText code = input("Ders kodu", personalCode); form.addView(code);
        personal.setOnCheckedChangeListener((v, checked) -> code.setText(checked ? personalCode : "ders-" + UUID.randomUUID().toString().substring(0,8)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Bir toplantı başlatın").setView(form)
            .setNegativeButton("İptal",null).setPositiveButton("Başlat",null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
            if (launch(code, name.getText().toString(), video.isChecked(), share)) dialog.dismiss();
        })); dialog.show();
    }
    private void joinDialog() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(22),dp(12),dp(22),dp(12));
        EditText code = input("Ders kodu", ""), name = input("Adınız", ""); form.addView(code); form.addView(name);
        Switch video = new Switch(this); video.setText("Kamera açık katıl"); form.addView(video);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Toplantıya katıl").setView(form)
            .setNegativeButton("İptal",null).setPositiveButton("Katıl",null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w -> {
            if (launch(code, name.getText().toString(), video.isChecked(), false)) dialog.dismiss();
        })); dialog.show();
    }
    private boolean launch(EditText code, String name, boolean video, boolean share) {
        String room = code.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (!room.matches("[a-z0-9ğüşöçı-]{3,80}")) { code.setError("En az 3 harf/rakam; boşluk kullanmayın"); return false; }
        try {
            JitsiMeetConferenceOptions options = conferenceDefaults()
                .setServerURL(new URL("https://meet.jit.si"))
                .setRoom("HasanHoca-" + room).setSubject("Hasan Hoca Canlı Ders")
                .setAudioMuted(false).setVideoMuted(!video)
                .setConfigOverride("displayName",TextUtils.isEmpty(name.trim()) ? "Katılımcı" : name.trim())
                .build();
            Intent i = new Intent(this,ScreenShareActivity.class); i.setAction("org.jitsi.meet.CONFERENCE");
            i.putExtra("JitsiMeetConferenceOptions", options); i.putExtra("teacherMode",video);
            i.putExtra("lessonCode",room); i.putExtra("shareOnJoin",share); startActivity(i); return true;
        } catch (Exception e) { Toast.makeText(this,"Ders açılamadı: " + e.getMessage(),Toast.LENGTH_LONG).show(); return false; }
    }
    private void planMeeting() {
        Intent i = new Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE,"Hasan Hoca Canlı Ders")
            .putExtra(CalendarContract.Events.DESCRIPTION,"Ders kodu: " + personalCode);
        try { startActivity(i); } catch (android.content.ActivityNotFoundException e) {
            new AlertDialog.Builder(this).setMessage("Bu cihazda takvim uygulaması bulunamadı.").setPositiveButton("Tamam",null).show();
        }
    }
}
