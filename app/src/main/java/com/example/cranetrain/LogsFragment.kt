package com.example.cranetrain

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.*

class LogsFragment : Fragment() {
    private lateinit var logsTextView: TextView
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val maxLogLines = 1000 // Maximum number of log lines to keep

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            val view = inflater.inflate(R.layout.fragment_logs, container, false)
            logsTextView = view.findViewById(R.id.logsTextView) ?: throw IllegalStateException("logsTextView not found")
            return view
        } catch (e: Exception) {
            Log.e("LogsFragment", "Error in onCreateView: ${e.message}")
            return null
        }
    }

    fun addLog(message: String) {
        try {
            if (!isAdded || view == null) return

            val timestamp = dateFormat.format(Date())
            val logEntry = "[$timestamp] $message\n"
            
            activity?.runOnUiThread {
                try {
                    val currentText = logsTextView.text.toString()
                    val newText = if (currentText.isEmpty()) {
                        logEntry
                    } else {
                        currentText + logEntry
                    }
                    
                    // Trim to maxLogLines if needed
                    val lines = newText.split("\n")
                    val finalText = if (lines.size > maxLogLines) {
                        lines.takeLast(maxLogLines).joinToString("\n") + "\n"
                    } else {
                        newText
                    }
                    
                    logsTextView.text = finalText
                    
                    // Scroll to bottom
                    val scrollAmount = logsTextView.layout?.getLineTop(logsTextView.lineCount) ?: 0
                    logsTextView.scrollTo(0, scrollAmount)
                } catch (e: Exception) {
                    Log.e("LogsFragment", "Error updating log text: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("LogsFragment", "Error adding log: ${e.message}")
        }
    }
} 