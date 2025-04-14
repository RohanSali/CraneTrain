package com.example.cranetrain

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class RemoteControlFragment : Fragment() {
    private lateinit var btnUp: Button
    private lateinit var btnDown: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button
    private lateinit var btnClockwise: Button
    private lateinit var btnAntiClockwise: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_remote_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize buttons
        btnUp = view.findViewById(R.id.btnUp)
        btnDown = view.findViewById(R.id.btnDown)
        btnLeft = view.findViewById(R.id.btnLeft)
        btnRight = view.findViewById(R.id.btnRight)
        btnClockwise = view.findViewById(R.id.btnClockwise)
        btnAntiClockwise = view.findViewById(R.id.btnAntiClockwise)

        // Set up click listeners
        btnUp.setOnClickListener { sendCommand("u") }
        btnDown.setOnClickListener { sendCommand("d") }
        btnLeft.setOnClickListener { sendCommand("l") }
        btnRight.setOnClickListener { sendCommand("r") }
        btnClockwise.setOnClickListener { sendCommand("c") }
        btnAntiClockwise.setOnClickListener { sendCommand("a") }
    }

    private fun sendCommand(command: String) {
        (activity as? MainActivity)?.sendCommand(command)
    }
} 