package com.hasanhoca.canliders.capture;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import org.webrtc.audio.JavaAudioDeviceModule;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * WebRTC'nin zaten açtığı mikrofondan gelen PCM örneklerini AAC/M4A'ya yazar.
 * Yeni bir AudioRecord açmadığı için canlı ders mikrofonuyla çakışmaz.
 */
public final class AacAudioRecorder
        implements JavaAudioDeviceModule.SamplesReadyCallback, AutoCloseable {
    private static final String MIME = MediaFormat.MIMETYPE_AUDIO_AAC;
    private final MediaCodec encoder;
    private final MediaMuxer muxer;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private final int sampleRate;
    private final int channelCount;
    private int trackIndex = -1;
    private boolean muxerStarted;
    private boolean closed;
    private long submittedFrames;

    public AacAudioRecorder(File output, int sampleRate, int channelCount) throws IOException {
        this.sampleRate = sampleRate;
        this.channelCount = channelCount;
        MediaFormat format = MediaFormat.createAudioFormat(MIME, sampleRate, channelCount);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 96_000);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384);
        encoder = MediaCodec.createEncoderByType(MIME);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        muxer = new MediaMuxer(output.getAbsolutePath(),
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    @Override
    public synchronized void onWebRtcAudioRecordSamplesReady(
            JavaAudioDeviceModule.AudioSamples samples) {
        if (closed || samples.getSampleRate() != sampleRate
                || samples.getChannelCount() != channelCount) return;
        byte[] pcm = samples.getData();
        int offset = 0;
        while (offset < pcm.length) {
            int inputIndex = encoder.dequeueInputBuffer(0);
            if (inputIndex < 0) break;
            ByteBuffer input = encoder.getInputBuffer(inputIndex);
            if (input == null) break;
            input.clear();
            int length = Math.min(input.remaining(), pcm.length - offset);
            input.put(pcm, offset, length);
            long ptsUs = submittedFrames * 1_000_000L / sampleRate;
            submittedFrames += length / (2L * channelCount);
            encoder.queueInputBuffer(inputIndex, 0, length, ptsUs, 0);
            offset += length;
        }
        drain(false);
    }

    private void drain(boolean finish) {
        if (finish) {
            int inputIndex = encoder.dequeueInputBuffer(10_000);
            if (inputIndex >= 0) {
                long ptsUs = submittedFrames * 1_000_000L / sampleRate;
                encoder.queueInputBuffer(inputIndex, 0, 0, ptsUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
        }
        int emptyPolls = 0;
        while (true) {
            int outputIndex = encoder.dequeueOutputBuffer(info, finish ? 10_000 : 0);
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!finish || ++emptyPolls >= 3) return;
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                trackIndex = muxer.addTrack(encoder.getOutputFormat());
                muxer.start();
                muxerStarted = true;
            } else if (outputIndex >= 0) {
                ByteBuffer encoded = encoder.getOutputBuffer(outputIndex);
                if (encoded != null && info.size > 0 && muxerStarted) {
                    encoded.position(info.offset);
                    encoded.limit(info.offset + info.size);
                    muxer.writeSampleData(trackIndex, encoded, info);
                }
                boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
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
