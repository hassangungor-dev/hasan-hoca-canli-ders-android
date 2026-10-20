package com.hasanhoca.canliders.capture;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import org.webrtc.VideoSink;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Bir ders kaydının görüntü, ses, birleştirme ve Galeri'ye taşıma yaşam döngüsü. */
public final class MeetingRecordingSession implements AutoCloseable {
    private final Context context;
    private final File videoTemp;
    private final File audioTemp;
    private final File mergedTemp;
    private final Mp4VideoSink videoRecorder;
    private final AacAudioRecorder audioRecorder;
    private boolean closed;

    public MeetingRecordingSession(Context context, int width, int height, int fps)
            throws IOException {
        this.context = context.getApplicationContext();
        File directory = new File(context.getCacheDir(), "lesson-recordings");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Geçici kayıt klasörü oluşturulamadı");
        }
        String id = Long.toString(System.currentTimeMillis());
        videoTemp = new File(directory, id + "-video.mp4");
        audioTemp = new File(directory, id + "-audio.m4a");
        mergedTemp = new File(directory, id + "-ders.mp4");
        videoRecorder = new Mp4VideoSink(videoTemp, width, height, fps, 6_000_000);
        audioRecorder = new AacAudioRecorder(audioTemp, 48_000, 1);
    }

    public VideoSink getVideoSink() {
        return videoRecorder;
    }

    public JavaAudioDeviceModule.SamplesReadyCallback getAudioCallback() {
        return audioRecorder;
    }

    /** Kaydı kapatır, tek MP4 yapar ve Galeri'ye eklenen videonun adresini döndürür. */
    public synchronized Uri finishAndSave() throws IOException {
        if (closed) throw new IllegalStateException("Kayıt daha önce kapatıldı");
        closed = true;
        videoRecorder.close();
        audioRecorder.close();
        Mp4TrackMerger.merge(videoTemp, audioTemp, mergedTemp);

        String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault())
                .format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, "Hasan-Hoca-Ders-" + date + ".mp4");
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH,
                Environment.DIRECTORY_MOVIES + "/Hasan Hoca Canlı Ders");
        values.put(MediaStore.Video.Media.IS_PENDING, 1);
        ContentResolver resolver = context.getContentResolver();
        Uri uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Galeri kayıt dosyası oluşturulamadı");
        try (FileInputStream input = new FileInputStream(mergedTemp);
             OutputStream output = resolver.openOutputStream(uri)) {
            if (output == null) throw new IOException("Galeri kayıt dosyası açılamadı");
            byte[] buffer = new byte[128 * 1024];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        }
        values.clear();
        values.put(MediaStore.Video.Media.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
        deleteTemporaryFiles();
        return uri;
    }

    private void deleteTemporaryFiles() {
        videoTemp.delete();
        audioTemp.delete();
        mergedTemp.delete();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        videoRecorder.close();
        audioRecorder.close();
        deleteTemporaryFiles();
    }
}
