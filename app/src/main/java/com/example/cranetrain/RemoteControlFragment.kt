package com.example.cranetrain

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.gridlayout.widget.GridLayout

class RemoteControlFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return GridLayout(requireContext()).apply {
            columnCount = 3
            rowCount = 3
            useDefaultMargins = true

            // Up button
            addView(createButton("Up", "u", 0, 1))

            // Left button
            addView(createButton("Left", "l", 1, 0))

            // Right button
            addView(createButton("Right", "r", 1, 2))

            // Down button
            addView(createButton("Down", "d", 2, 1))

            // Clockwise button
            addView(createButton("Clockwise", "c", 1, 2))

            // Anti-clockwise button
            addView(createButton("Anti-clockwise", "a", 1, 0))
        }
    }

    private fun createButton(text: String, command: String, row: Int, col: Int): Button {
        return Button(context).apply {
            this.text = text
            layoutParams = GridLayout.LayoutParams().apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                setMargins(8, 8, 8, 8)
                rowSpec = GridLayout.spec(row)
                columnSpec = GridLayout.spec(col)
            }
            setOnClickListener {
                (activity as? MainActivity)?.sendCommand(command)
            }
        }
    }
} 