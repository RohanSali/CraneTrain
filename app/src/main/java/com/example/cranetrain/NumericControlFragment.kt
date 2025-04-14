package com.example.cranetrain

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment

class NumericControlFragment : Fragment() {
    private lateinit var upValue: EditText
    private lateinit var downValue: EditText
    private lateinit var leftValue: EditText
    private lateinit var rightValue: EditText
    private lateinit var clockwiseValue: EditText
    private lateinit var anticlockwiseValue: EditText

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            val view = inflater.inflate(R.layout.fragment_numeric_control, container, false)
            
            // Initialize views
            upValue = view.findViewById(R.id.upValue)
            downValue = view.findViewById(R.id.downValue)
            leftValue = view.findViewById(R.id.leftValue)
            rightValue = view.findViewById(R.id.rightValue)
            clockwiseValue = view.findViewById(R.id.clockwiseValue)
            anticlockwiseValue = view.findViewById(R.id.anticlockwiseValue)

            // Set up send buttons with original command logic
            view.findViewById<ImageButton>(R.id.upSendButton)?.setOnClickListener {
                sendCommand("u", upValue) // Up -> "u"
            }

            view.findViewById<ImageButton>(R.id.downSendButton)?.setOnClickListener {
                sendCommand("d", downValue) // Down -> "d"
            }

            view.findViewById<ImageButton>(R.id.leftSendButton)?.setOnClickListener {
                sendCommand("l", leftValue) // Left -> "l"
            }

            view.findViewById<ImageButton>(R.id.rightSendButton)?.setOnClickListener {
                sendCommand("r", rightValue) // Right -> "r"
            }

            view.findViewById<ImageButton>(R.id.clockwiseSendButton)?.setOnClickListener {
                sendCommand("c", clockwiseValue) // Clockwise -> "c"
            }

            view.findViewById<ImageButton>(R.id.anticlockwiseSendButton)?.setOnClickListener {
                sendCommand("a", anticlockwiseValue) // Anti-clockwise -> "a"
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
} 