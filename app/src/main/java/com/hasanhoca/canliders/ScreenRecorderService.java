package com.hasanhoca.canliders;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import android.os.ParcelFileDescriptor;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ScreenRecorderService extends Service {
    public static final String ACTION_START = "com.hasanhoca.canliders.START_RECORDING";
    public static final String ACTION_STOP = "com.hasanhoca.canliders.STOP_RECORDING";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";

    private static final int NOTIFICATION_ID = 7107;
    private static final String CHANNEL_ID = "lesson_recording";

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private MediaRecorder recorder;
    private ParcelFileDescriptor outputDescriptor;
    private Uri outputUri;
    private boolean started;
    private boolean stopping;
    public static final String ACTION_STATE = "com.hasanhoca.canliders.RECORDING_STATE";
    public static boolean isRecording;
    private void publishState(boolean active) {
        isRecording = active;
        Intent state = new Intent(ACTION_STATE); state.putExtra("active",active);
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this).sendBroadcast(state);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_STOP.equals(intent.getAction())) {
            stopRecording(true);
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(intent.getAction()) && !started) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent resultData = Build.VERSION.SDK_INT >= 33
                    ? intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class)
                    : intent.getParcelableExtra(EXTRA_RESULT_DATA);
            try {
                startAsForeground();
                startRecording(resultCode, resultData);
            } catch (Exception e) {
                publishState(false); stopRecording(false);
                Toast.makeText(this,"Kayıt izni veya başlatma hatası: " + e.getMessage(),Toast.LENGTH_LONG).show();
            }
        }
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        Intent stop = new Intent(this, ScreenRecorderService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Hasan Hoca Canlı Ders")
                .setContentText("Ekran ve mikrofon kaydediliyor")
                .setOngoing(true)
                .addAction(0, "Kaydı durdur", stopPendingIntent)
                .build();

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                            | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void startRecording(int resultCode, Intent resultData) {
        try {
            if (resultData == null) throw new IllegalStateException("Ekran izni alınamadı");

            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
                metrics.widthPixels = bounds.width();
                metrics.heightPixels = bounds.height();
                metrics.densityDpi = getResources().getDisplayMetrics().densityDpi;
            } else {
                windowManager.getDefaultDisplay().getRealMetrics(metrics);
            }

            int width = metrics.widthPixels - (metrics.widthPixels % 2);
            int height = metrics.heightPixels - (metrics.heightPixels % 2);
            int maxSide = 1920;
            if (Math.max(width, height) > maxSide) {
                float scale = maxSide / (float) Math.max(width, height);
                width = ((int) (width * scale)) & ~1;
                height = ((int) (height * scale)) & ~1;
            }

            String fileName = "Hasan-Hoca-Ders-" +
                    new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".mp4";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
            values.put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/Hasan Hoca");
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
            outputUri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            if (outputUri == null) throw new IllegalStateException("Video dosyası açılamadı");
            outputDescriptor = getContentResolver().openFileDescriptor(outputUri, "w");

            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setOutputFile(outputDescriptor.getFileDescriptor());
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setVideoSize(width, height);
            recorder.setVideoFrameRate(30);
            recorder.setVideoEncodingBitRate(6_000_000);
            recorder.setAudioSamplingRate(44_100);
            recorder.setAudioEncodingBitRate(128_000);
            recorder.prepare();

            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, resultData);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    stopRecording(true);
                }
            }, new Handler(Looper.getMainLooper()));

            Surface surface = recorder.getSurface();
            virtualDisplay = projection.createVirtualDisplay(
                    "HasanHocaDersKaydi", width, height, metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface, null, null);
            recorder.start();
            started = true;
            publishState(true);
        } catch (Exception error) {
            Toast.makeText(this, "Kayıt başlatılamadı: " + error.getMessage(), Toast.LENGTH_LONG).show();
            stopRecording(false);
        }
    }

    private void stopRecording(boolean save) {
        if (stopping) return;
        stopping = true;
        if (started && recorder != null) {
            try {
                recorder.stop();
            } catch (RuntimeException ignored) {
                save = false;
            }
        }
        started = false;
        publishState(false);
        if (recorder != null) {
            recorder.reset();
            recorder.release();
            recorder = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
        try {
            if (outputDescriptor != null) outputDescriptor.close();
        } catch (Exception ignored) {
        }
        outputDescriptor = null;

        if (outputUri != null) {
            if (save) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.IS_PENDING, 0);
                getContentResolver().update(outputUri, values, null, null);
                Toast.makeText(this, "Sesli kayıt Galeri > Hasan Hoca klasörüne kaydedildi.", Toast.LENGTH_LONG).show();
            } else {
                getContentResolver().delete(outputUri, null, null);
            }
            outputUri = null;
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
        stopping = false;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Ders kaydı", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Sesli ekran kaydı bildirimi");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
