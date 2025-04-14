package com.example.cranetrain

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment

class NumericControlFragment : Fragment() {
    private lateinit var upValue: EditText
    private lateinit var downValue: EditText
    private lateinit var leftValue: EditText
    private lateinit var rightValue: EditText
    private lateinit var clockwiseValue: EditText
    private lateinit var anticlockwiseValue: EditText
    private lateinit var controlsData: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            val view = inflater.inflate(R.layout.fragment_numeric_control, container, false)
            
            // Initialize views with null checks
            upValue = view.findViewById(R.id.upValue) ?: throw IllegalStateException("upValue not found")
            downValue = view.findViewById(R.id.downValue) ?: throw IllegalStateException("downValue not found")
            leftValue = view.findViewById(R.id.leftValue) ?: throw IllegalStateException("leftValue not found")
            rightValue = view.findViewById(R.id.rightValue) ?: throw IllegalStateException("rightValue not found")
            clockwiseValue = view.findViewById(R.id.clockwiseValue) ?: throw IllegalStateException("clockwiseValue not found")
            anticlockwiseValue = view.findViewById(R.id.anticlockwiseValue) ?: throw IllegalStateException("anticlockwiseValue not found")
            controlsData = view.findViewById(R.id.controlsData) ?: throw IllegalStateException("controlsData not found")

            // Set up send buttons with null checks
            view.findViewById<ImageButton>(R.id.upSendButton)?.setOnClickListener {
                sendCommand("u", upValue)
            }

            view.findViewById<ImageButton>(R.id.downSendButton)?.setOnClickListener {
                sendCommand("d", downValue)
            }

            view.findViewById<ImageButton>(R.id.leftSendButton)?.setOnClickListener {
                sendCommand("l", leftValue)
            }

            view.findViewById<ImageButton>(R.id.rightSendButton)?.setOnClickListener {
                sendCommand("r", rightValue)
            }

            view.findViewById<ImageButton>(R.id.clockwiseSendButton)?.setOnClickListener {
                sendCommand("c", clockwiseValue)
            }

            view.findViewById<ImageButton>(R.id.anticlockwiseSendButton)?.setOnClickListener {
                sendCommand("a", anticlockwiseValue)
            }

            return view
        } catch (e: Exception) {
            Log.e("NumericControlFragment", "Error in onCreateView: ${e.message}")
            return null
        }
    }

    private fun sendCommand(command: String, editText: EditText) {
        try {
            val value = editText.text.toString()
            if (value.isNotEmpty()) {
                // Format: command + value (e.g., "u100" for up with value 100)
                val formattedCommand = "$command$value"
                val mainActivity = activity as? MainActivity
                if (mainActivity == null) {
                    Log.e("NumericControlFragment", "MainActivity is null")
                    return
                }
                mainActivity.sendCommand(formattedCommand)
            }
        } catch (e: Exception) {
            Log.e("NumericControlFragment", "Error sending command: ${e.message}")
        }
    }

    fun updateControlsData(data: String) {
        try {
            if (!isAdded || view == null) return
            controlsData.text = data
        } catch (e: Exception) {
            Log.e("NumericControlFragment", "Error updating controls data: ${e.message}")
        }
    }
} 