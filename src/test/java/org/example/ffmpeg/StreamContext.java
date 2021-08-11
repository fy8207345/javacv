package org.example.ffmpeg;

import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avutil.AVFrame;

public class StreamContext {
    AVCodecContext avCodecContext;
    AVFrame avFrame;

    public StreamContext(AVCodecContext avCodecContext, AVFrame avFrame) {
        this.avCodecContext = avCodecContext;
        this.avFrame = avFrame;
    }
}
