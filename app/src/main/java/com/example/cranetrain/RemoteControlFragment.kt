package com.example.cranetrain

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class RemoteControlFragment : Fragment() {
    private lateinit var statusData: TextView
    private lateinit var upButton: Button
    private lateinit var downButton: Button
    private lateinit var leftButton: Button
    private lateinit var rightButton: Button
    private lateinit var clockwiseButton: Button
    private lateinit var anticlockwiseButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            val view = inflater.inflate(R.layout.fragment_remote_control, container, false)
            
            // Initialize views with null checks
            statusData = view.findViewById(R.id.statusData) ?: throw IllegalStateException("statusData not found")
            upButton = view.findViewById(R.id.upButton) ?: throw IllegalStateException("upButton not found")
            downButton = view.findViewById(R.id.downButton) ?: throw IllegalStateException("downButton not found")
            leftButton = view.findViewById(R.id.leftButton) ?: throw IllegalStateException("leftButton not found")
            rightButton = view.findViewById(R.id.rightButton) ?: throw IllegalStateException("rightButton not found")
            clockwiseButton = view.findViewById(R.id.clockwiseButton) ?: throw IllegalStateException("clockwiseButton not found")
            anticlockwiseButton = view.findViewById(R.id.anticlockwiseButton) ?: throw IllegalStateException("anticlockwiseButton not found")

            // Set up button click listeners
            upButton.setOnClickListener { sendCommand("u") }
            downButton.setOnClickListener { sendCommand("d") }
            leftButton.setOnClickListener { sendCommand("l") }
            rightButton.setOnClickListener { sendCommand("r") }
            clockwiseButton.setOnClickListener { sendCommand("c") }
            anticlockwiseButton.setOnClickListener { sendCommand("a") }

            return view
        } catch (e: Exception) {
            Log.e("RemoteControlFragment", "Error in onCreateView: ${e.message}")
            return null
        }
    }

    private fun sendCommand(command: String) {
        try {
            val mainActivity = activity as? MainActivity
            if (mainActivity == null) {
                Log.e("RemoteControlFragment", "MainActivity is null")
                return
            }
            mainActivity.sendCommand(command)
        } catch (e: Exception) {
            Log.e("RemoteControlFragment", "Error sending command: ${e.message}")
        }
    }

    fun updateStatusData(data: String) {
        try {
            if (!isAdded || view == null) return
            statusData.text = "Status Data: $data"
        } catch (e: Exception) {
            Log.e("RemoteControlFragment", "Error updating status data: ${e.message}")
        }
    }
} 