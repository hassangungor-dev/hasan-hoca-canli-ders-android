package com.hasanhoca.canliders.capture;

import android.content.Context;
import android.content.Intent;

import org.webrtc.EglBase;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

/**
 * Tek MediaProjection oturumundan üretilen görüntüyü hem WebRTC bağlantısına
 * hem de kayıt kodlayıcısına verir. Böylece Android'de iki ayrı ekran yakalama
 * oturumunun birbirini durdurması önlenir.
 */
public final class SharedScreenCapture {
    private final EglBase eglBase;
    private final SurfaceTextureHelper textureHelper;
    private final ScreenCapturerAndroid capturer;
    private final VideoSource videoSource;
    private final VideoTrack videoTrack;

    public SharedScreenCapture(
            Context context,
            PeerConnectionFactory factory,
            Intent projectionPermission,
            VideoSink recordingSink) {
        eglBase = EglBase.create();
        textureHelper = SurfaceTextureHelper.create(
                "HasanHocaScreenCapture", eglBase.getEglBaseContext());
        videoSource = factory.createVideoSource(true);
        capturer = new ScreenCapturerAndroid(projectionPermission,
                new android.media.projection.MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        stop();
                    }
                });
        capturer.initialize(textureHelper, context.getApplicationContext(),
                videoSource.getCapturerObserver());
        videoTrack = factory.createVideoTrack("HASAN_HOCA_SCREEN", videoSource);
        if (recordingSink != null) {
            // WebRTC aynı track'i öğrencilere gönderirken bu sink aynı kareleri
            // MP4 kodlayıcısına aktarır; ikinci MediaProjection açılmaz.
            videoTrack.addSink(recordingSink);
        }
    }

    public VideoTrack getVideoTrack() {
        return videoTrack;
    }

    public void start(int width, int height, int fps) {
        capturer.startCapture(width, height, fps);
    }

    public void stop() {
        capturer.stopCapture();
    }

    public void release() {
        stop();
        capturer.dispose();
        videoTrack.dispose();
        videoSource.dispose();
        textureHelper.dispose();
        eglBase.release();
    }
}
