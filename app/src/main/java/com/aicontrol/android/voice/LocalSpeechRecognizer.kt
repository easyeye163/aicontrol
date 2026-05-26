package com.aicontrol.android.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Android 系统自带语音识别器
 *
 * 封装 SpeechRecognizer，使用按住说话松手识别模式。
 * 无需网络 API，可离线使用（取决于系统是否下载了离线语音包）。
 * 兼容国产 ROM：优先使用 SpeechRecognizer，失败时提示用户。
 */
class LocalSpeechRecognizer(private val context: Context) {

    companion object {
        private const val TAG = "LocalSTT"
    }

    interface Listener {
        /** 语音识别开始（录音中） */
        fun onRecordingStarted() {}
        /** 录音结束，正在识别中 */
        fun onTranscribing() {}
        /** 识别成功 */
        fun onResult(text: String) {}
        /** 发生错误 */
        fun onError(message: String) {}
    }

    var listener: Listener? = null

    private var speechRecognizer: SpeechRecognizer? = null

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.i(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.i(TAG, "Beginning of speech")
            listener?.onRecordingStarted()
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.i(TAG, "End of speech")
        }

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "录音错误"
                SpeechRecognizer.ERROR_CLIENT -> "客户端错误"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "录音权限不足"
                SpeechRecognizer.ERROR_NETWORK -> "网络错误"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络超时"
                SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "识别器忙"
                SpeechRecognizer.ERROR_SERVER -> "服务器错误"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有检测到语音输入"
                else -> "未知错误($error)"
            }
            Log.w(TAG, "SpeechRecognizer error $error: $msg")
            listener?.onError(msg)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (matches.isNullOrEmpty()) {
                listener?.onError("未识别到语音内容")
                return
            }
            val text = matches[0]
            Log.i(TAG, "Result: $text")
            listener?.onResult(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // 不处理中间结果
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * 开始语音识别
     */
    fun startListening() {
        // 先检查设备是否支持语音识别
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener?.onError("设备不支持语音识别")
            return
        }

        // 销毁旧的
        destroy()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(recognitionListener)
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // 提示文字（部分 ROM 会显示弹窗）
            putExtra(RecognizerIntent.EXTRA_PROMPT, "")
        }

        try {
            speechRecognizer?.startListening(intent)
            Log.i(TAG, "startListening")
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            listener?.onError("启动语音识别失败: ${e.message}")
            destroy()
        }
    }

    /**
     * 停止语音识别
     */
    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "stopListening error", e)
        }
    }

    /**
     * 释放资源
     */
    fun destroy() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "destroy error", e)
        }
        speechRecognizer = null
    }
}
