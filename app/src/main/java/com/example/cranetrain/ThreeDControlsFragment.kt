package com.example.cranetrain

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.cranetrain.databinding.Fragment3dControlsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ThreeDControlsFragment : Fragment() {
    private var _binding: Fragment3dControlsBinding? = null
    private val binding get() = _binding!!
    
    private var xValue = 0
    private var yValue = 0
    private var zValue = 0

    companion object {
        private const val TAG = "ThreeDControlsFragment"
        private const val PREF_NAME = "3d_controls_prefs"
        private const val KEY_X_VALUE = "x_value"
        private const val KEY_Y_VALUE = "y_value"
        private const val KEY_Z_VALUE = "z_value"
        private const val MIN_HORIZONTAL = 0
        private const val MAX_HORIZONTAL = 10
        private const val MIN_VERTICAL = 0
        private const val MAX_VERTICAL = 35
        private const val MIN_ANGULAR = -5
        private const val MAX_ANGULAR = 5
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Fragment3dControlsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Setup input listeners
        setupInputListeners()
        
        // Setup update button
        binding.updateButton.setOnClickListener {
            updatePositions()
        }
    }

    private fun loadSavedValues() {
        val sharedPref = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        xValue = sharedPref.getInt(KEY_X_VALUE, 0)
        yValue = sharedPref.getInt(KEY_Y_VALUE, 0)
        zValue = sharedPref.getInt(KEY_Z_VALUE, 0)
        
        // Update UI with loaded values
        binding.xInput.setText(xValue.toString())
        binding.yInput.setText(yValue.toString())
        binding.zInput.setText(zValue.toString())
    }

    private fun saveValues() {
        val sharedPref = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt(KEY_X_VALUE, xValue)
            putInt(KEY_Y_VALUE, yValue)
            putInt(KEY_Z_VALUE, zValue)
            apply()
        }
    }

    private fun setupInputListeners() {
        setupInputListener(binding.xInput) { newValue ->
            xValue = newValue
            saveValues()
        }
        
        setupInputListener(binding.yInput) { newValue ->
            yValue = newValue
            saveValues()
        }
        
        setupInputListener(binding.zInput) { newValue ->
            zValue = newValue
            saveValues()
        }
    }

    private fun setupInputListener(editText: EditText, onValueChanged: (Int) -> Unit) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                try {
                    val value = s.toString().toIntOrNull() ?: 0
                    onValueChanged(value)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing input value: ${e.message}")
                }
            }
        })
    }

    private fun updatePositions() {
        try {
            val activity = activity as? MainActivity
            if (activity == null || !activity.isConnected) {
                Toast.makeText(context, "Not connected to HC-05", Toast.LENGTH_SHORT).show()
                return
            }

            // Validate input values
            if (xValue < MIN_HORIZONTAL || xValue > MAX_HORIZONTAL) {
                Toast.makeText(context, "X must be between $MIN_HORIZONTAL and $MAX_HORIZONTAL", Toast.LENGTH_SHORT).show()
                return
            }
            if (yValue < MIN_VERTICAL || yValue > MAX_VERTICAL) {
                Toast.makeText(context, "Y must be between $MIN_VERTICAL and $MAX_VERTICAL", Toast.LENGTH_SHORT).show()
                return
            }
            if (zValue < MIN_ANGULAR || zValue > MAX_ANGULAR) {
                Toast.makeText(context, "Z must be between $MIN_ANGULAR and $MAX_ANGULAR", Toast.LENGTH_SHORT).show()
                return
            }

            // Get current positions from JibAnalysisFragment
            val jibFragment = activity.supportFragmentManager.fragments.find { 
                it is JibAnalysisFragment && it.isAdded 
            } as? JibAnalysisFragment

            jibFragment?.let { fragment ->
                // Compare and send commands
                if (fragment.horizontalPosition != xValue) {
                    val command = if (fragment.horizontalPosition < xValue) "r" else "l"
                    activity.sendCommand(command)
                }
                
                if (fragment.verticalPosition != yValue) {
                    val command = if (fragment.verticalPosition < yValue) "u" else "d"
                    activity.sendCommand(command)
                }
                
                if (fragment.angularPosition != zValue) {
                    val command = if (fragment.angularPosition < zValue) "a" else "c"
                    activity.sendCommand(command)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating positions: ${e.message}")
            Toast.makeText(context, "Error updating positions: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 