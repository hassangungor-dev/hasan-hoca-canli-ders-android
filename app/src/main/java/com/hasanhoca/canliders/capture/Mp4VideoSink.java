package com.hasanhoca.canliders.capture;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/** VideoTrack üzerindeki ekran karelerini ikinci ekran yakalama açmadan MP4'e yazar. */
public final class Mp4VideoSink implements VideoSink, AutoCloseable {
    private static final String MIME = MediaFormat.MIMETYPE_VIDEO_AVC;

    private final int width;
    private final int height;
    private final MediaCodec encoder;
    private final MediaMuxer muxer;
    private final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
    private int trackIndex = -1;
    private boolean muxerStarted;
    private boolean closed;
    private long firstTimestampNs = -1L;

    public Mp4VideoSink(File output, int width, int height, int fps, int bitRate)
            throws IOException {
        this.width = width;
        this.height = height;

        MediaFormat format = MediaFormat.createVideoFormat(MIME, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);

        encoder = MediaCodec.createEncoderByType(MIME);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        muxer = new MediaMuxer(output.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    @Override
    public synchronized void onFrame(VideoFrame frame) {
        if (closed) return;
        VideoFrame.I420Buffer i420 = frame.getBuffer().toI420();
        try {
            if (i420.getWidth() != width || i420.getHeight() != height) return;
            int inputIndex = encoder.dequeueInputBuffer(0);
            if (inputIndex < 0) {
                drain(false);
                return;
            }
            ByteBuffer input = encoder.getInputBuffer(inputIndex);
            if (input == null) return;
            input.clear();
            copyPlane(i420.getDataY(), i420.getStrideY(), width, height, input);
            copyPlane(i420.getDataU(), i420.getStrideU(), width / 2, height / 2, input);
            copyPlane(i420.getDataV(), i420.getStrideV(), width / 2, height / 2, input);
            if (firstTimestampNs < 0) firstTimestampNs = frame.getTimestampNs();
            long ptsUs = Math.max(0, (frame.getTimestampNs() - firstTimestampNs) / 1_000L);
            encoder.queueInputBuffer(inputIndex, 0, width * height * 3 / 2, ptsUs, 0);
            drain(false);
        } finally {
            i420.release();
        }
    }

    private static void copyPlane(ByteBuffer source, int stride, int planeWidth,
            int planeHeight, ByteBuffer target) {
        ByteBuffer src = source.duplicate();
        for (int row = 0; row < planeHeight; row++) {
            src.position(row * stride);
            src.limit(row * stride + planeWidth);
            target.put(src);
            src.limit(source.capacity());
        }
    }

    private void drain(boolean endOfStream) {
        if (endOfStream) {
            int inputIndex = encoder.dequeueInputBuffer(10_000);
            if (inputIndex >= 0) {
                encoder.queueInputBuffer(inputIndex, 0, 0,
                        firstTimestampNs < 0 ? 0 : System.nanoTime() / 1_000L,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
        }
        int emptyPolls = 0;
        while (true) {
            int outputIndex = encoder.dequeueOutputBuffer(bufferInfo, endOfStream ? 10_000 : 0);
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) return;
                if (++emptyPolls >= 3) return;
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxerStarted) throw new IllegalStateException("Video biçimi iki kez değişti");
                trackIndex = muxer.addTrack(encoder.getOutputFormat());
                muxer.start();
                muxerStarted = true;
            } else if (outputIndex >= 0) {
                ByteBuffer encoded = encoder.getOutputBuffer(outputIndex);
                if (encoded != null && bufferInfo.size > 0 && muxerStarted) {
                    encoded.position(bufferInfo.offset);
                    encoded.limit(bufferInfo.offset + bufferInfo.size);
                    muxer.writeSampleData(trackIndex, encoded, bufferInfo);
                }
                boolean eos = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                encoder.releaseOutputBuffer(outputIndex, false);
                if (eos) return;
            }
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        try {
            drain(true);
        } finally {
            encoder.stop();
            encoder.release();
            if (muxerStarted) muxer.stop();
            muxer.release();
        }
    }
}
