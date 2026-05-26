package com.aicontrol.android

import com.aicontrol.android.agent.DefaultAgentService
import com.aicontrol.android.base.BaseApp
import com.aicontrol.android.channel.ChannelManager
import com.aicontrol.android.integration.FeatureIntegrationManager
import com.aicontrol.android.service.ForegroundService
import com.aicontrol.android.tool.ToolRegistry
import com.aicontrol.android.utils.KVUtils
import com.aicontrol.android.utils.XLog
import com.blankj.utilcode.util.NetworkUtils
import com.aicontrol.android.floating.FloatingCircleManager
import com.aicontrol.android.webrtc.DirectWebRTCManager

val appViewModel: AppViewModel by lazy { AiControlApplication.appViewModelInstance }
class AiControlApplication : BaseApp() {

    companion object {
        private const val TAG = "AiControlApplication"
        lateinit var instance: AiControlApplication
            private set
        lateinit var appViewModelInstance: AppViewModel

        private var crashHandlerInstalled = false
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        XLog.setDEBUG(BuildConfig.DEBUG)
        
        installCrashHandler()
        registerNetworkCallback()
        appViewModelInstance = getAppViewModelProvider()[AppViewModel::class.java]
        KVUtils.init(this)
        ToolRegistry.getInstance().registerAllTools(ToolRegistry.DeviceType.MOBILE)
        XLog.e(TAG, "AiControlApplication initialized, tools registered: ${ToolRegistry.getInstance().getAllTools().size}")

        Thread({ FeatureIntegrationManager.getInstance(this).initialize() }, "feature-init").start()

        DefaultAgentService.FILE_LOGGING_ENABLED = BuildConfig.DEBUG
        DefaultAgentService.FILE_LOGGING_CACHE_DIR = cacheDir

        appViewModelInstance.initCommon()
        if (!ForegroundService.isRunning()) {
            val started = ForegroundService.start(this)
            if (!started) {
                XLog.e(TAG, "ForegroundService start failed: notification permission not granted")
            }
        }

        Thread({
            if (KVUtils.hasLlmConfig()) {
                appViewModelInstance.initAgent()
                appViewModelInstance.afterInit()
            }
        }, "app-async-init").start()

        // CyberVerse Direct: init WebRTC (user manually starts via settings)
        DirectWebRTCManager.init(this)
    }

    private fun installCrashHandler() {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            XLog.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            
            try {
                appViewModelInstance.cancelCurrentTask()
            } catch (e: Exception) {
                XLog.e(TAG, "Failed to cancel task during crash", e)
            }
            
            try {
                FloatingCircleManager.setIdleState()
            } catch (e: Exception) {
                XLog.e(TAG, "Failed to reset floating circle during crash", e)
            }
            
            try {
                ChannelManager.sendMessage(
                    appViewModelInstance.inProgressTaskChannel ?: com.aicontrol.android.channel.Channel.FEISHU,
                    "应用发生异常: ${throwable.message?.take(100) ?: "Unknown"}",
                    appViewModelInstance.inProgressTaskMessageId
                )
            } catch (e: Exception) {
                XLog.e(TAG, "Failed to notify user during crash", e)
            }
            
            defaultHandler?.uncaughtException(thread, throwable)
        }
        XLog.i(TAG, "Global crash handler installed")
    }

    private var networkListener: NetworkUtils.OnNetworkStatusChangedListener? = null

    private fun registerNetworkCallback() {
        networkListener = object : NetworkUtils.OnNetworkStatusChangedListener {
            override fun onConnected(networkType: NetworkUtils.NetworkType?) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        if (KVUtils.hasLlmConfig()) {
                            XLog.i(TAG, "网络恢复(${networkType?.name})，检查并重连断开的通道")
                            ChannelManager.reconnectIfNeeded()
                        }
                    } catch (e: Exception) {
                        XLog.e(TAG, "Failed to reconnect channels on network restore", e)
                    }
                }, 2000)
            }

            override fun onDisconnected() {
                XLog.w(TAG, "网络断开")
            }
        }
        NetworkUtils.registerNetworkStatusChangedListener(networkListener)
    }
}