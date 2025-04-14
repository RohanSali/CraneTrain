package com.example.cranetrain

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

class NumericControlFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)

            // Add control groups
            addView(createControlGroup("Up", "u"))
            addView(createControlGroup("Down", "d"))
            addView(createControlGroup("Left", "l"))
            addView(createControlGroup("Right", "r"))
            addView(createControlGroup("Clockwise", "c"))
            addView(createControlGroup("Anti-clockwise", "a"))
        }
    }

    private fun createControlGroup(text: String, command: String): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 8, 0, 8)
            }

            // Add EditText for numeric input
            val input = EditText(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(0, 0, 8, 0)
                }
                hint = "Enter value"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
            }
            addView(input)

            // Add Button
            val button = Button(context).apply {
                this.text = text
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    val value = input.text.toString()
                    if (value.isNotEmpty()) {
                        (activity as? MainActivity)?.sendCommand(command + value)
                    }
                }
            }
            addView(button)
        }
    }
} 