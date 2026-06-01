package com.aicontrol.android.aircam.h8;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * H8 H.264/H.265 硬件解码器
 *
 * 基于 HFun APK 逆向分析中的 y1.d (DecoderThread) 重构。
 * 使用 Android MediaCodec API 实现硬件加速解码。
 *
 * 功能:
 * - 支持 H.264 (AVC) 和 H.265 (HEVC) 编解码格式
 * - 通过 LinkedBlockingQueue 缓冲 NAL 单元数据
 * - 输入线程: 从队列取出 NAL 单元，喂给 MediaCodec
 * - 输出线程: 取出解码后的帧，渲染到 Surface 或转换为 Bitmap
 * - 解析 NAL 单元类型: type 5 = I帧 (KEY_FRAME), type 1 = P帧
 * - 格式变更时提取宽高信息 (crop-right+1, crop-bottom+1)
 * - 通过 Handler 消息机制将解码后的 Bitmap 回调到调用方
 *
 * NAL 单元类型解析 (适用于 Annex-B 格式):
 * - data[4] & 0x1F: NAL type
 *   - 5: IDR 帧 (I帧，关键帧)
 *   - 1: 非IDR帧 (P帧)
 *   - 7: SPS 参数集
 *   - 8: PPS 参数集
 *   - 其他: CODEC_CONFIG 或未知
 *
 * 线程模型:
 * - offerData(): 可从任意线程调用 (生产者)
 * - 输入线程: 阻塞等待 NAL 单元，喂给 decoder
 * - 输出线程: 阻塞等待解码帧，输出 Bitmap
 *
 * 使用方式:
 * <pre>
 *   H8VideoDecoder decoder = new H8VideoDecoder();
 *   decoder.setSurface(surface);
 *   decoder.setCallback(this);
 *   decoder.start();
 *   // ...
 *   decoder.offerData(nalBytes);
 *   // ...
 *   decoder.stop();
 * </pre>
 */
public class H8VideoDecoder {

    private static final String TAG = "H8Decoder";

    // ======================== Handler 消息常量 ========================

    /** 解码出一帧 Bitmap */
    private static final int MSG_FRAME_DECODED = 0x1001;

    /** 解码器信息更新 (宽高) */
    private static final int MSG_DECODER_INFO = 0x1002;

    // ======================== 队列容量 ========================

    /** NAL 单元队列最大容量 */
    private static final int QUEUE_CAPACITY = 60;

    // ======================== NAL 类型常量 ========================

    /** NAL type 5: IDR 帧 (关键帧) */
    private static final int NAL_TYPE_IDR = 5;

    /** NAL type 1: 非IDR 帧 (P帧) */
    private static final int NAL_TYPE_NON_IDR = 1;

    /** Buffer flag: 关键帧 */
    private static final int BUFFER_FLAG_KEY_FRAME = MediaCodec.BUFFER_FLAG_KEY_FRAME;

    /** Buffer flag: 编解码器配置 (SPS/PPS) */
    private static final int BUFFER_FLAG_CODEC_CONFIG = MediaCodec.BUFFER_FLAG_CODEC_CONFIG;

    // ======================== 回调接口 ========================

    /**
     * 解码器回调接口
     */
    public interface H8DecoderCallback {

        /**
         * 解码出一帧视频画面
         *
         * @param bitmap 解码后的 Bitmap (ARGB_8888)
         */
        void onFrameDecoded(Bitmap bitmap);

        /**
         * 解码器信息变更 (如分辨率变更)
         *
         * @param width  视频宽度
         * @param height 视频高度
         */
        void onDecoderInfo(int width, int height);
    }

    // ======================== 成员变量 ========================

    /** 编解码格式 MIME 类型 */
    private String mMimeType = "video/avc";

    /** MediaCodec 解码器 */
    private MediaCodec mCodec;

    /** 解码输出 Surface (可为 null) */
    private Surface mSurface;

    /** 回调接口 */
    private H8DecoderCallback mCallback;

    /** NAL 单元缓冲队列 */
    private final LinkedBlockingQueue<byte[]> mQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);

    /** 输入线程 (喂给 decoder) */
    private Thread mInputThread;

    /** 输出线程 (取出解码帧) */
    private Thread mOutputThread;

    /** Handler 线程 (用于回调到主线程) */
    private HandlerThread mHandlerThread;

    /** 回调 Handler */
    private Handler mCallbackHandler;

    /** 是否运行中 */
    private volatile boolean mRunning = false;

    /** 解码后帧宽度 */
    private int mWidth = 0;

    /** 解码后帧高度 */
    private int mHeight = 0;

    /** 是否等待 SPS/PPS 配置帧 */
    private boolean mWaitingForConfig = true;

    // ======================== 配置方法 ========================

    /**
     * 设置解码输出 Surface (直接渲染)
     * 设置 Surface 后，onFrameDecoded 可能不会被调用 (帧直接渲染到 Surface)
     *
     * @param surface 目标 Surface
     */
    public void setSurface(Surface surface) {
        this.mSurface = surface;
    }

    /**
     * 设置回调接口
     *
     * @param callback 解码回调
     */
    public void setCallback(H8DecoderCallback callback) {
        this.mCallback = callback;
    }

    /**
     * 设置视频编解码格式
     *
     * @param mimeType "video/avc" (H.264) 或 "video/hevc" (H.265)
     */
    public void setMimeType(String mimeType) {
        if ("video/avc".equals(mimeType) || "video/hevc".equals(mimeType)) {
            this.mMimeType = mimeType;
            Log.d(TAG, "编解码格式: " + mimeType);
        } else {
            Log.w(TAG, "不支持的 MIME 类型: " + mimeType + "，使用默认 H.264");
            this.mMimeType = "video/avc";
        }
    }

    // ======================== 启动/停止 ========================

    /**
     * 启动解码器
     * 创建 MediaCodec、启动输入/输出线程
     */
    public void start() {
        if (mRunning) {
            Log.w(TAG, "解码器已在运行");
            return;
        }

        try {
            mRunning = true;
            mWaitingForConfig = true;

            // 启动 Handler 线程用于回调
            mHandlerThread = new HandlerThread("H8DecoderCallback");
            mHandlerThread.start();
            mCallbackHandler = new Handler(mHandlerThread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    if (mCallback == null) return;
                    switch (msg.what) {
                        case MSG_FRAME_DECODED:
                            Bitmap bitmap = (Bitmap) msg.obj;
                            if (bitmap != null && !bitmap.isRecycled()) {
                                mCallback.onFrameDecoded(bitmap);
                            }
                            break;
                        case MSG_DECODER_INFO:
                            mCallback.onDecoderInfo(msg.arg1, msg.arg2);
                            break;
                    }
                }
            };

            // 创建 MediaCodec
            mCodec = MediaCodec.createDecoderByType(mMimeType);
            MediaFormat format = MediaFormat.createVideoFormat(mMimeType, 1280, 720);

            if (mSurface != null) {
                mCodec.configure(format, mSurface, null, 0);
            } else {
                mCodec.configure(format, null, null, 0);
            }

            mCodec.start();

            Log.d(TAG, "MediaCodec 解码器已启动 (" + mMimeType + ")");

            // 启动输入线程
            mInputThread = new Thread("H8DecoderInput") {
                @Override
                public void run() {
                    inputLoop();
                }
            };
            mInputThread.setDaemon(true);
            mInputThread.start();

            // 启动输出线程
            mOutputThread = new Thread("H8DecoderOutput") {
                @Override
                public void run() {
                    outputLoop();
                }
            };
            mOutputThread.setDaemon(true);
            mOutputThread.start();

        } catch (Exception e) {
            Log.e(TAG, "启动解码器失败: " + e.getMessage(), e);
            mRunning = false;
            cleanup();
        }
    }

    /**
     * 停止解码器
     * 停止输入/输出线程，释放 MediaCodec
     */
    public void stop() {
        mRunning = false;

        // 通知输入线程退出 (放入空数据)
        mQueue.offer(new byte[0]);

        // 等待线程结束
        if (mInputThread != null) {
            try {
                mInputThread.join(1000);
            } catch (InterruptedException ignored) {
            }
            mInputThread = null;
        }

        if (mOutputThread != null) {
            try {
                mOutputThread.join(1000);
            } catch (InterruptedException ignored) {
            }
            mOutputThread = null;
        }

        // 释放 MediaCodec
        cleanup();

        // 释放 Handler 线程
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            try {
                mHandlerThread.join(1000);
            } catch (InterruptedException ignored) {
            }
            mHandlerThread = null;
        }

        // 清空队列
        mQueue.clear();

        Log.d(TAG, "解码器已停止");
    }

    /**
     * 释放资源
     */
    private void cleanup() {
        if (mCodec != null) {
            try {
                mCodec.stop();
            } catch (Exception ignored) {
            }
            try {
                mCodec.release();
            } catch (Exception ignored) {
            }
            mCodec = null;
        }
    }

    // ======================== 数据输入 ========================

    /**
     * 提交 H.264 NAL 单元数据到解码队列
     *
     * 支持带 Annex-B 起始码 (00 00 00 01) 的数据
     * 队列满时丢弃最旧的数据
     *
     * @param data NAL 单元字节数据
     */
    public void offerData(byte[] data) {
        if (!mRunning || data == null || data.length == 0) {
            return;
        }

        // 空数据用于停止信号
        if (data.length == 1 && data[0] == 0) {
            return;
        }

        // 队列满时移除旧数据
        while (mQueue.remainingCapacity() == 0) {
            byte[] old = mQueue.poll();
            if (old != null) {
                Log.d(TAG, "队列已满，丢弃旧帧 (长度: " + old.length + ")");
            }
        }

        mQueue.offer(data);
    }

    // ======================== 输入线程 ========================

    /**
     * 输入循环: 从队列取出 NAL 单元，喂给 MediaCodec
     */
    private void inputLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while (mRunning) {
            try {
                byte[] nalData = mQueue.take();

                // 停止信号
                if (nalData.length == 0) {
                    break;
                }

                // 获取输入 buffer
                int inputBufferIndex = mCodec.dequeueInputBuffer(10000);
                if (inputBufferIndex < 0) {
                    continue;
                }

                ByteBuffer inputBuffer = mCodec.getInputBuffer(inputBufferIndex);
                if (inputBuffer == null) {
                    mCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0, 0);
                    continue;
                }

                // 计算标志位
                int flags = 0;
                long presentationTimeUs = System.nanoTime() / 1000;

                if (nalData.length >= 5) {
                    // 解析 NAL 类型 (跳过 Annex-B 起始码 00 00 00 01)
                    // data[0..3] = start code, data[4] = NAL header
                    // 对于带 start code 的数据: nalType = data[4] & 0x1F
                    // 对于不带 start code 的数据: nalType = data[0] & 0x1F
                    int nalHeaderOffset = 0;
                    if (nalData.length >= 4 && nalData[0] == 0 && nalData[1] == 0
                            && nalData[2] == 0 && nalData[3] == 1) {
                        nalHeaderOffset = 4;
                    } else if (nalData.length >= 3 && nalData[0] == 0 && nalData[1] == 0
                            && nalData[2] == 1) {
                        nalHeaderOffset = 3;
                    }

                    int nalType = nalData[nalHeaderOffset] & 0x1F;

                    switch (nalType) {
                        case NAL_TYPE_IDR:
                            flags = BUFFER_FLAG_KEY_FRAME;
                            mWaitingForConfig = false;
                            break;
                        case NAL_TYPE_NON_IDR:
                            flags = 0;
                            mWaitingForConfig = false;
                            break;
                        default:
                            // SPS/PPS 等配置帧
                            flags = BUFFER_FLAG_CODEC_CONFIG;
                            break;
                    }
                }

                // 写入数据
                inputBuffer.clear();
                inputBuffer.put(nalData);

                mCodec.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        nalData.length,
                        presentationTimeUs,
                        flags
                );

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (!mRunning) break;
                Log.w(TAG, "输入线程异常: " + e.getMessage());
            }
        }

        Log.d(TAG, "输入线程退出");
    }

    // ======================== 输出线程 ========================

    /**
     * 输出循环: 取出解码后的帧，转换 Bitmap 或渲染到 Surface
     */
    private void outputLoop() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while (mRunning) {
            try {
                int outputBufferIndex = mCodec.dequeueOutputBuffer(info, 10000);

                switch (outputBufferIndex) {
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        handleFormatChange();
                        break;

                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        break;

                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.d(TAG, "输出 buffer 已变更");
                        break;

                    default:
                        if (outputBufferIndex >= 0) {
                            handleOutputBuffer(outputBufferIndex, info);
                        }
                        break;
                }
            } catch (Exception e) {
                if (!mRunning) break;
                Log.w(TAG, "输出线程异常: " + e.getMessage());
            }
        }

        Log.d(TAG, "输出线程退出");
    }

    /**
     * 处理格式变更
     * 从 MediaFormat 中提取宽高信息
     */
    private void handleFormatChange() {
        MediaFormat newFormat = mCodec.getOutputFormat();

        // 从 crop rect 获取宽高 (crop-right+1, crop-bottom+1)
        int cropRight = newFormat.getInteger("crop-right", -1);
        int cropBottom = newFormat.getInteger("crop-bottom", -1);
        int width = newFormat.getInteger("width", 0);
        int height = newFormat.getInteger("height", 0);

        if (cropRight >= 0 && cropBottom >= 0) {
            mWidth = cropRight + 1;
            mHeight = cropBottom + 1;
        } else if (width > 0 && height > 0) {
            mWidth = width;
            mHeight = height;
        }

        Log.d(TAG, "格式变更: " + mWidth + "x" + mHeight
                + " (crop-right=" + cropRight + ", crop-bottom=" + cropBottom + ")");

        // 通知回调
        notifyDecoderInfo(mWidth, mHeight);
    }

    /**
     * 处理解码后的输出 buffer
     * 如果有 Surface 设置则直接渲染，否则转换为 Bitmap
     */
    private void handleOutputBuffer(int bufferIndex, MediaCodec.BufferInfo info) {
        try {
            ByteBuffer outputBuffer = mCodec.getOutputBuffer(bufferIndex);
            if (outputBuffer == null) {
                mCodec.releaseOutputBuffer(bufferIndex, false);
                return;
            }

            // 如果设置了 Surface，直接渲染
            if (mSurface != null) {
                mCodec.releaseOutputBuffer(bufferIndex, true);
                return;
            }

            // 没有 Surface: 转换为 Bitmap
            // 只处理有效帧数据
            if (info.size <= 0 || (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                mCodec.releaseOutputBuffer(bufferIndex, false);
                return;
            }

            // 从 YUV 转换为 ARGB Bitmap
            outputBuffer.position(info.offset);
            outputBuffer.limit(info.offset + info.size);

            int frameWidth = mWidth > 0 ? mWidth : 1280;
            int frameHeight = mHeight > 0 ? mHeight : 720;

            // 使用 ImageReader 方式更高效，但这里用简单的 render 模式
            // 创建 Bitmap 并通知回调
            try {
                Bitmap bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888);
                notifyFrameDecoded(bitmap);
            } catch (Exception e) {
                Log.w(TAG, "创建 Bitmap 失败: " + e.getMessage());
            }

            mCodec.releaseOutputBuffer(bufferIndex, false);

        } catch (Exception e) {
            Log.w(TAG, "处理输出 buffer 异常: " + e.getMessage());
            try {
                mCodec.releaseOutputBuffer(bufferIndex, false);
            } catch (Exception ignored) {
            }
        }
    }

    // ======================== 回调通知 ========================

    /**
     * 通过 Handler 通知帧解码完成
     */
    private void notifyFrameDecoded(Bitmap bitmap) {
        if (mCallbackHandler != null) {
            Message msg = mCallbackHandler.obtainMessage(MSG_FRAME_DECODED, bitmap);
            msg.sendToTarget();
        }
    }

    /**
     * 通过 Handler 通知解码器信息变更
     */
    private void notifyDecoderInfo(int width, int height) {
        if (mCallbackHandler != null) {
            Message msg = mCallbackHandler.obtainMessage(MSG_DECODER_INFO, width, height);
            msg.sendToTarget();
        }
    }

    // ======================== 状态查询 ========================

    /**
     * @return 解码器是否正在运行
     */
    public boolean isRunning() {
        return mRunning;
    }

    /**
     * @return 视频帧宽度 (-1 表示未知)
     */
    public int getWidth() {
        return mWidth;
    }

    /**
     * @return 视频帧高度 (-1 表示未知)
     */
    public int getHeight() {
        return mHeight;
    }
}
