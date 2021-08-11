package org.example.ffmpeg;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Editor;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import org.bytedeco.ffmpeg.avcodec.AVCodec;
import org.bytedeco.ffmpeg.avcodec.AVCodecContext;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVInputFormat;
import org.bytedeco.ffmpeg.avformat.AVOutputFormat;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.ffmpeg.avutil.AVFrame;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.*;
import org.bytedeco.videoinput.IAMStreamConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.bytedeco.ffmpeg.global.avutil.*;
import static org.bytedeco.ffmpeg.global.avcodec.*;
import static org.bytedeco.ffmpeg.global.avformat.*;

public class Tests {

    @Test
    void name() {
        int err_index = 0;///推流过程中出现错误的次数
        //rtsp://wowzaec2demo.streamlock.net/vod/mp4:BigBuckBunny_115k.mov
        try(FFmpegFrameGrabber grabber = new FFmpegFrameGrabber("C:\\gstreamer\\1.0\\mingw_x86\\bin\\sintel_video.mkv");
            MyFFmpegFrameRecorder recorder = new MyFFmpegFrameRecorder("rtmp://127.0.0.1:11936/live/mystream", 0)){
            avutil.av_log_set_level(AV_LOG_DEBUG);
            FFmpegLogCallback.set();
            grabber.setOption("stimeout", "2000000");
            grabber.setVideoCodec(AV_CODEC_ID_H264);
            grabber.start(true);

            recorder.setInterleaved(true);
            recorder.setVideoBitrate(2500000);
            recorder.setVideoCodec(AV_CODEC_ID_H264);
            recorder.setFormat("flv");
            recorder.setPixelFormat(AV_PIX_FMT_YUV420P);
            recorder.setGopSize((int)grabber.getFrameRate() * 2);
            recorder.setFrameRate(grabber.getFrameRate());
            AVFormatContext formatContext = grabber.getFormatContext();
            System.out.printf("number of streams : %s\n", formatContext.nb_streams());
            recorder.setImageHeight(grabber.getImageHeight());
            recorder.setImageWidth(grabber.getImageWidth());
            recorder.start(formatContext);
            //清空探测时留下的帧
            grabber.flush();

            AVPacket pkt = null;
            long dts = 0;
            long pts = 0;

            System.out.println("开始推流");
            for (int no_frame_index = 0; no_frame_index < 5 || err_index < 5;) {
                pkt = grabber.grabPacket();
                if (pkt == null || pkt.size() <= 0 || pkt.data() == null) {
                    // 空包记录次数跳过
                    no_frame_index++;
                    err_index++;
                    continue;
                }
                // 获取到的pkt的dts，pts异常，将此包丢弃掉。
                if (pkt.dts() == avutil.AV_NOPTS_VALUE && pkt.pts() == avutil.AV_NOPTS_VALUE || pkt.pts() < dts) {
                    err_index++;
                    av_packet_unref(pkt);
                    continue;
                }
                // 记录上一pkt的dts，pts
                dts = pkt.dts();
                pts = pkt.pts();
                // 推数据包
                err_index += (recorder.recordPacket(pkt) ? 0 : 1);
                // 将缓存空间的引用计数-1，并将Packet中的其他字段设为初始值。如果引用计数为0，自动的释放缓存空间。
                av_packet_unref(pkt);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Test
    void openInputFile() {
        AVFormatContext inputContext = null;
        try {
            int ret;
            inputContext = new AVFormatContext(null);
            String filename = "C:\\gstreamer\\1.0\\mingw_x86\\bin\\sintel_video.mkv";
            if(avformat_open_input(inputContext, filename, null, null) != 0){
                throw new RuntimeException("cant open input file");
            }
            if(avformat_find_stream_info(inputContext, (AVDictionary) null) < 0){
                throw new RuntimeException("cant find stream info");
            }
            List<StreamContext> streamContexts = new ArrayList<>();
            for(int i=0;i<inputContext.nb_streams();i++){
                AVStream stream = inputContext.streams(i);
                AVCodec avCodec = avcodec_find_decoder(stream.codecpar().codec_id());
                AVCodecContext codecContext;
                if(avCodec == null){
                    throw new RuntimeException("cant find decoder for stream " + i);
                }
                codecContext = avcodec_alloc_context3(avCodec);
                if(codecContext == null){
                    throw new RuntimeException("cant allocate decoder context for stream " + i);
                }
                if(avcodec_parameters_to_context(codecContext, stream.codecpar()) < 0){
                    throw new RuntimeException("Failed to copy decoder parameters to input decoder context for stream " + i);
                }
                if(codecContext.codec_type() == AVMEDIA_TYPE_VIDEO || codecContext.codec_type() == AVMEDIA_TYPE_AUDIO){
                    codecContext.framerate(av_guess_frame_rate(inputContext, stream, null));
                    //open decoder
                    if(avcodec_open2(codecContext, avCodec, (AVDictionary) null) < 0){
                        throw new RuntimeException("cant open decoder for stream " + i);
                    }
                }
                AVFrame avFrame = av_frame_alloc();
                streamContexts.add(new StreamContext(codecContext, avFrame));
            }
            av_dump_format(inputContext, 0, filename, 0);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            if(inputContext != null){
                inputContext.close();
            }
        }
    }
}
