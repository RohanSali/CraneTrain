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
    private var isOperationInProgress = false
    private var operationScope: Job? = null

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
        private const val POSITION_UPDATE_DELAY = 100L // ms
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
        
        setupInputListeners()
        loadSavedValues()
        
        binding.updateButton.setOnClickListener {
            if (!isOperationInProgress) {
                startPositionUpdate()
            }
        }
    }

    private fun startPositionUpdate() {
        operationScope = CoroutineScope(Dispatchers.Main).launch {
            isOperationInProgress = true
            binding.statusIndicator1.setBackgroundColor(resources.getColor(android.R.color.holo_orange_light, null))
            binding.statusIndicator2.setBackgroundColor(resources.getColor(android.R.color.holo_orange_light, null))
            binding.statusIndicator3.setBackgroundColor(resources.getColor(android.R.color.holo_orange_light, null))
            
            try {
                val activity = activity as? MainActivity
                if (activity == null || !activity.isConnected) {
                    Toast.makeText(context, "Not connected to HC-05", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Get current positions from JibAnalysisFragment
                val jibFragment = activity.supportFragmentManager.fragments.find { 
                    it is JibAnalysisFragment && it.isAdded 
                } as? JibAnalysisFragment

                if (jibFragment == null) {
                    Toast.makeText(context, "Jib Analysis panel not available", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Clamp values to extremes
                val targetX = xValue.coerceIn(MIN_HORIZONTAL, MAX_HORIZONTAL)
                val targetY = yValue.coerceIn(MIN_VERTICAL, MAX_VERTICAL)
                val targetZ = zValue.coerceIn(MIN_ANGULAR, MAX_ANGULAR)

                //Function to wait for position update
                suspend fun waitForPositionUpdate(
                    currentPosition: Int,
                    getPosition: () -> Int,
                    maxAttempts: Int = 10
                ): Boolean {
                    var attempts = 0
                    while (attempts < maxAttempts) {
                        delay(POSITION_UPDATE_DELAY)
                        val newPosition = getPosition()
                        if (newPosition != currentPosition) {
                            return true
                        }
                        attempts++
                    }
                    return false
                }

                // Adjust horizontal position
                var currentX = jibFragment.getCurrentHorizontalPosition()
                while (currentX != targetX) {
                    val command = if (currentX < targetX) "r" else "l"
                    activity.sendCommand(command)
                    
                    while (!waitForPositionUpdate(currentX, { jibFragment.getCurrentHorizontalPosition() })) {
                        Log.d(TAG,"No horizontal position update received")
                    }
                    if (command == "r") currentX++ else currentX--
                }
                // Check if any values were clamped
                val isClampedx = xValue != targetX
                binding.statusIndicator1.setBackgroundColor(
                    if (isClampedx) resources.getColor(android.R.color.holo_red_light, null)
                    else resources.getColor(android.R.color.holo_green_light, null)
                )

                // Adjust vertical position
                var currentY = jibFragment.getCurrentVerticalPosition()
                while (currentY != targetY) {
                    val command = if (currentY < targetY) "d" else "u"
                    activity.sendCommand(command)
                    
                    while(!waitForPositionUpdate(currentY, { jibFragment.getCurrentVerticalPosition() })) {
                        Log.d(TAG,"No vertical position update received")
                    }

                    if (command=="d") currentY++ else currentY--
                }
                // Check if any values were clamped
                val isClampedy = yValue != targetY
                binding.statusIndicator2.setBackgroundColor(
                    if (isClampedy) resources.getColor(android.R.color.holo_red_light, null)
                    else resources.getColor(android.R.color.holo_green_light, null)
                )

                // Adjust angular position
                var currentZ = jibFragment.getCurrentAngularPosition()
                while (currentZ != targetZ) {
                    val command = if (currentZ < targetZ) "c" else "a"
                    activity.sendCommand(command)
                    
                    while(!waitForPositionUpdate(currentZ, { jibFragment.getCurrentAngularPosition() })) {
                        Log.d(TAG,"No angular position update received")
                    }

                    if (command=="c") currentZ++ else currentZ--
                }
                // Check if any values were clamped
                val isClampedz = zValue != targetZ
                binding.statusIndicator3.setBackgroundColor(
                    if (isClampedz) resources.getColor(android.R.color.holo_red_light, null)
                    else resources.getColor(android.R.color.holo_green_light, null)
                )


            } catch (e: Exception) {
                Log.e(TAG, "Error updating positions: ${e.message}")
                binding.statusIndicator1.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, null))
                binding.statusIndicator2.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, null))
                binding.statusIndicator3.setBackgroundColor(resources.getColor(android.R.color.holo_red_light, null))
                Toast.makeText(context, "Error updating positions: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isOperationInProgress = false
            }
        }
    }

    private fun loadSavedValues() {
        val sharedPref = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        xValue = sharedPref.getInt(KEY_X_VALUE, 0)
        yValue = sharedPref.getInt(KEY_Y_VALUE, 0)
        zValue = sharedPref.getInt(KEY_Z_VALUE, 0)
        
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

    override fun onDestroyView() {
        super.onDestroyView()
        operationScope?.cancel()
        _binding = null
    }
} 