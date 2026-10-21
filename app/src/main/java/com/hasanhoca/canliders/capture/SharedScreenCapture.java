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
            Intent projectionPermission) {
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
    }

    public VideoTrack getVideoTrack() {
        return videoTrack;
    }

    public void start(int width, int height, int fps) {
        capturer.startCapture(width, height, fps);
    }

    /** Aynı ekran akışını ikinci MediaProjection açmadan kayıt kodlayıcısına verir. */
    public void addRecordingSink(VideoSink sink) {
        videoTrack.addSink(sink);
    }

    public void removeRecordingSink(VideoSink sink) {
        videoTrack.removeSink(sink);
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
