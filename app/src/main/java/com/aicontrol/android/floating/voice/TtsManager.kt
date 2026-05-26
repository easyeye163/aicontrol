package com.aicontrol.android.floating.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * TTS 语音朗读管理器
 *
 * 封装 Android TextToSpeech，用于朗读 AI 回复文本。
 * 支持中文语音，自动过滤 JSON 动作块和代码块。
 *
 * 关键设计：
 * - 使用 UtteranceProgressListener 监听播报完成事件
 * - speak() 中先 stop() 再延时 speak，避免 TTS 引擎还没停就写入新文本导致丢失
 * - stop() 后 100ms 延时确保 TTS 引擎完成清理
 *
 * 使用方式：
 *   val tts = TtsManager(context)
 *   tts.speak("你好世界")
 *   tts.shutdown() // 不再使用时调用
 */
class TtsManager(context: Context) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "TtsManager"
        /** stop() 后等待 TTS 引擎就绪的延时 */
        private const val STOP_DELAY_MS = 100L
    }

    var isReady: Boolean = false
        private set

    /** 是否正在播报 */
    var isSpeaking: Boolean = false
        private set

    @Volatile
    private var lastSpokenText: String = ""

    /** 等待播报的文本队列（stop 后延时代播） */
    private var pendingText: String? = null

    private val ttsHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS init failed: $status")
            return
        }
        var lang = "zh-CN"
        tts?.let {
            lang = com.aicontrol.android.utils.KVUtils.getTtsLanguage()
            val rate = com.aicontrol.android.utils.KVUtils.getTtsSpeechRate()
            val pitch = com.aicontrol.android.utils.KVUtils.getTtsPitch()
            val locale = try { Locale.forLanguageTag(lang) } catch (_: Exception) { Locale.CHINESE }
            val result = it.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "TTS language '$lang' not supported, falling back to Chinese")
                it.setLanguage(Locale.CHINESE)
            }
            it.setSpeechRate(rate)
            it.setPitch(pitch)
            it.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                    Log.i(TAG, "TTS start speaking: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                    Log.i(TAG, "TTS done speaking: $utteranceId")
                }

                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                    Log.w(TAG, "TTS error on: $utteranceId")
                }
            })
        }
        isReady = true
        Log.i(TAG, "TTS initialized (lang=$lang)")
        // 如果在 TTS 初始化完成之前有文本排队等待播报，现在播报它
        pendingText?.let { text ->
            Log.i(TAG, "TTS now ready, speaking pending: $text")
            pendingText = null
            doSpeak(text)
        }
    }

    /**
     * 朗读文本
     * - 自动过滤 JSON 动作块、代码块、多余空白
     * - 如果正在播报，先停止再延时播报新文本
     * - 如果文本为空则跳过
     */
    fun speak(text: String) {
        if (!isReady) {
            Log.w(TAG, "TTS not ready, queuing: $text")
            pendingText = text
            return
        }
        val sanitized = sanitizeForTts(text)
        if (sanitized.isEmpty()) return

        // 清除之前的延时代播
        ttsHandler.removeCallbacksAndMessages(null)

        if (isSpeaking) {
            // 正在播报中：先 stop，延时后再播
            Log.i(TAG, "TTS busy, stopping then queuing: $sanitized")
            internalStop()
            pendingText = sanitized
            ttsHandler.postDelayed({
                doSpeak(pendingText ?: sanitized)
                pendingText = null
            }, STOP_DELAY_MS)
        } else {
            // 空闲状态：直接播（但如果刚 stop 过，给一点缓冲）
            if (lastSpokenText.isNotEmpty()) {
                // 上一次播报刚结束，直接清除缓存播新的
                lastSpokenText = ""
            }
            doSpeak(sanitized)
        }
    }

    /**
     * 实际执行播报
     */
    private fun doSpeak(text: String) {
        if (!isReady || text.isEmpty()) {
            Log.w(TAG, "doSpeak skipped: ready=$isReady text='$text'")
            return
        }
        lastSpokenText = text
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "car_${System.currentTimeMillis()}")
        }
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "car_utterance")
        Log.i(TAG, "TTS speak result=$result text='$text'")
    }

    /**
     * 停止当前朗读（内部方法，不重置 pending）
     */
    private fun internalStop() {
        lastSpokenText = ""
        tts?.stop()
    }

    /**
     * 停止当前朗读（公开方法）
     */
    fun stop() {
        ttsHandler.removeCallbacksAndMessages(null)
        pendingText = null
        internalStop()
        isSpeaking = false
    }

    /**
     * 释放 TTS 资源
     */
    fun shutdown() {
        ttsHandler.removeCallbacksAndMessages(null)
        pendingText = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        isSpeaking = false
    }

    /**
     * 清理待朗读文本：去除 JSON 动作块、代码围栏、多余空白
     */
    private fun sanitizeForTts(text: String): String {
        return text
            .replace(Regex("```json[\\s\\S]*?```"), "")
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("\\{[^{}]*\"action\"[^{}]*\\}"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
