package com.github.ananbox

import android.os.Bundle
import android.view.MenuItem
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class LogViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_view)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.settings_logs_title)

        val logTextView = findViewById<TextView>(R.id.log_text_view)
        val scrollView = findViewById<ScrollView>(R.id.log_scroll_view)

        val logContent = StringBuilder()

        // Read proot.log from internal storage
        val prootLogFile = File(filesDir, "proot.log")
        if (prootLogFile.exists()) {
            logContent.append("=== proot.log ===\n")
            logContent.append(prootLogFile.readText())
            logContent.append("\n\n")
        }

        // Read system.log from rootfs/data
        val systemLogFile = File(filesDir, "rootfs/data/system.log")
        if (systemLogFile.exists()) {
            logContent.append("=== system.log ===\n")
            logContent.append(systemLogFile.readText())
            logContent.append("\n\n")
        }

        if (logContent.isEmpty()) {
            logContent.append(getString(R.string.settings_logs_empty))
        }

        logTextView.text = logContent.toString()

        // Scroll to bottom to show most recent logs
        scrollView.post {
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
