package com.hasanhoca.canliders.capture;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Ayrı kaydedilen H.264 görüntü ve AAC sesi kalite kaybı olmadan tek MP4'te birleştirir. */
public final class Mp4TrackMerger {
    private static final int BUFFER_SIZE = 2 * 1024 * 1024;

    private Mp4TrackMerger() {}

    public static void merge(File videoFile, File audioFile, File outputFile)
            throws IOException {
        MediaExtractor video = new MediaExtractor();
        MediaExtractor audio = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean muxerStarted = false;
        try {
            video.setDataSource(videoFile.getAbsolutePath());
            audio.setDataSource(audioFile.getAbsolutePath());
            int videoSourceTrack = findTrack(video, "video/");
            int audioSourceTrack = findTrack(audio, "audio/");
            if (videoSourceTrack < 0 || audioSourceTrack < 0) {
                throw new IOException("Kayıt dosyasında görüntü veya ses kanalı bulunamadı");
            }

            video.selectTrack(videoSourceTrack);
            audio.selectTrack(audioSourceTrack);
            muxer = new MediaMuxer(outputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int videoTargetTrack = muxer.addTrack(video.getTrackFormat(videoSourceTrack));
            int audioTargetTrack = muxer.addTrack(audio.getTrackFormat(audioSourceTrack));
            muxer.start();
            muxerStarted = true;

            copySamples(video, muxer, videoTargetTrack);
            copySamples(audio, muxer, audioTargetTrack);
        } finally {
            video.release();
            audio.release();
            if (muxer != null) {
                try {
                    if (muxerStarted) muxer.stop();
                } finally {
                    muxer.release();
                }
            }
        }
    }

    private static int findTrack(MediaExtractor extractor, String mimePrefix) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(mimePrefix)) return i;
        }
        return -1;
    }

    private static void copySamples(MediaExtractor extractor, MediaMuxer muxer,
            int targetTrack) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            buffer.clear();
            int size = extractor.readSampleData(buffer, 0);
            if (size < 0) break;
            info.offset = 0;
            info.size = size;
            info.presentationTimeUs = extractor.getSampleTime();
            info.flags = extractor.getSampleFlags();
            muxer.writeSampleData(targetTrack, buffer, info);
            extractor.advance();
        }
    }
}
