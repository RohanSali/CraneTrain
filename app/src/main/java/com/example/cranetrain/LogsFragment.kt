package com.example.cranetrain

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.*

class LogsFragment : Fragment() {
    private lateinit var logsTextView: TextView
    private val logs = StringBuilder()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val maxLogLines = 1000 // Maximum number of log lines to keep

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_logs, container, false)
        logsTextView = view.findViewById(R.id.logsTextView)
        
        // Set up clear logs button
        val clearLogsButton = view.findViewById<Button>(R.id.clearLogsButton)
        clearLogsButton.setOnClickListener {
            clearLogs()
        }
        
        return view
    }

    fun addLog(message: String) {
        activity?.runOnUiThread {
            val timestamp = dateFormat.format(Date())
            val newLog = "[$timestamp] $message\n"
            
            // Prepend the new log entry
            logs.insert(0, newLog)
            
            // Trim to maxLogLines if needed
            val lines = logs.toString().split("\n")
            if (lines.size > maxLogLines) {
                logs.clear()
                logs.append(lines.take(maxLogLines).joinToString("\n") + "\n")
            }
            
            logsTextView.text = logs.toString()
            
            // Auto-scroll to top since newest logs are at the top
            val scrollView = logsTextView.parent.parent as? androidx.core.widget.NestedScrollView
            scrollView?.post {
                scrollView.fullScroll(View.FOCUS_UP)
            }
        }
    }

    private fun clearLogs() {
        activity?.runOnUiThread {
            logs.clear()
            logsTextView.text = ""
        }
    }
} 