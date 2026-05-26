package com.aicontrol.android.ui.timeline

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aicontrol.android.R
import com.aicontrol.android.base.BaseActivity
import com.aicontrol.android.integration.FeatureIntegrationManager
import com.aicontrol.android.timeline.TaskTimeline
import com.aicontrol.android.widget.CommonToolbar
import java.text.SimpleDateFormat
import java.util.*

class TimelineActivity : BaseActivity() {

    private lateinit var rvTasks: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var adapter: TimelineAdapter
    private lateinit var taskTimeline: TaskTimeline

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timeline)

        taskTimeline = FeatureIntegrationManager.getInstance(this).taskTimeline

        initViews()
        loadTasks()
    }

    private fun initViews() {
        findViewById<CommonToolbar>(R.id.toolbar).setTitle(getString(R.string.timeline_title))

        rvTasks = findViewById(R.id.rvTasks)
        tvEmpty = findViewById(R.id.tvEmpty)

        adapter = TimelineAdapter { task ->
            val intent = Intent(this, TaskDetailActivity::class.java)
            intent.putExtra(TaskDetailActivity.EXTRA_RECORD_ID, task.id)
            startActivity(intent)
        }
        rvTasks.adapter = adapter
        rvTasks.layoutManager = LinearLayoutManager(this)
    }

    private fun loadTasks() {
        val tasks = taskTimeline.getRecentTasks(50)
        adapter.setData(tasks)
        tvEmpty.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadTasks()
    }
}