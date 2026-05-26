package com.aicontrol.android.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.aicontrol.android.AiControlApplication
import com.aicontrol.android.R
import com.aicontrol.android.base.BaseActivity
import com.aicontrol.android.utils.KVUtils
import com.aicontrol.android.widget.CommonToolbar
import com.aicontrol.android.widget.KButton
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * LLM 配置页（自行填写 API Key、Base URL、模型名）
 * 支持导入/导出配置 JSON
 */
class LlmConfigActivity : BaseActivity() {

    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_llm_config)

        findViewById<CommonToolbar>(R.id.toolbar).apply {
            setTitle(getString(R.string.llm_config_title))
            showBackButton(true) { finish() }
            setActionIcon(R.drawable.ic_export) { showImportExportMenu() }
        }

        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val etBaseUrl = findViewById<EditText>(R.id.etBaseUrl)
        val etModelName = findViewById<EditText>(R.id.etModelName)

        etApiKey.setText(KVUtils.getLlmApiKey())
        etBaseUrl.setText(KVUtils.getLlmBaseUrl())
        etModelName.setText(KVUtils.getLlmModelName())

        findViewById<KButton>(R.id.btnSave).setOnClickListener {
            val apiKey = etApiKey.text.toString().trim()
            val baseUrl = etBaseUrl.text.toString().trim()
            val modelName = etModelName.text.toString().trim().ifEmpty { "" }

            if (apiKey.isEmpty()) {
                Toast.makeText(this, getString(R.string.llm_config_api_key_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            KVUtils.setLlmApiKey(apiKey)
            KVUtils.setLlmBaseUrl(baseUrl)
            KVUtils.setLlmModelName(modelName)

            AiControlApplication.appViewModelInstance.updateAgentConfig()
            AiControlApplication.appViewModelInstance.initAgent()
            AiControlApplication.appViewModelInstance.afterInit()
            Toast.makeText(this, getString(R.string.llm_config_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    /**
     * 显示导入/导出菜单
     */
    private fun showImportExportMenu() {
        val items = arrayOf(
            getString(R.string.llm_export_clipboard),
            getString(R.string.llm_import_clipboard)
        )
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.llm_import_export))
            .setItems(items) { _, which ->
                when (which) {
                    0 -> exportToClipboard()
                    1 -> importFromClipboard()
                }
            }
            .show()
    }

    /**
     * 导出当前 LLM 配置到剪贴板
     * 格式: {"api_key":"xxx","base_url":"xxx","model":"xxx"}
     */
    private fun exportToClipboard() {
        val config = JsonObject().apply {
            addProperty("api_key", KVUtils.getLlmApiKey())
            addProperty("base_url", KVUtils.getLlmBaseUrl())
            addProperty("model", KVUtils.getLlmModelName())
        }
        val json = gson.toJson(config)
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("llm_config", json))
        Toast.makeText(this, getString(R.string.llm_export_success), Toast.LENGTH_SHORT).show()
    }

    /**
     * 从剪贴板导入 LLM 配置
     */
    private fun importFromClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, getString(R.string.llm_import_clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }
        val json = clip.getItemAt(0)?.text?.toString()
        if (json.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.llm_import_clipboard_empty), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val apiKey = obj.get("api_key")?.asString ?: ""
            val baseUrl = obj.get("base_url")?.asString ?: ""
            val model = obj.get("model")?.asString ?: ""

            if (apiKey.isEmpty()) {
                Toast.makeText(this, getString(R.string.llm_import_invalid_format), Toast.LENGTH_SHORT).show()
                return
            }

            // 填入输入框但不立即保存，让用户确认
            findViewById<EditText>(R.id.etApiKey).setText(apiKey)
            findViewById<EditText>(R.id.etBaseUrl).setText(baseUrl)
            findViewById<EditText>(R.id.etModelName).setText(model)
            Toast.makeText(this, getString(R.string.llm_import_filled), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.llm_import_invalid_format), Toast.LENGTH_SHORT).show()
        }
    }
}
