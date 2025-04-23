package com.example.cranetrain

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.cranetrain.databinding.FragmentJibAnalysisBinding
import com.google.android.material.snackbar.Snackbar

class JibAnalysisFragment : Fragment() {
    private var _binding: FragmentJibAnalysisBinding? = null
    private val binding get() = _binding!!
    
    private var verticalPosition = 0
    private var horizontalPosition = 0
    private var angularPosition = 0
    private var forceValue = 0 // Force value kept in volatile memory
    private var isFirstLaunch = true
    private var lastForceUpdateTime = 0L // Track last force update time
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isFragmentActive = false

    companion object {
        private const val TAG = "JibAnalysisFragment"
        private const val PREF_NAME = "jib_analysis_prefs"
        private const val KEY_VERTICAL_POS = "vertical_position"
        private const val KEY_HORIZONTAL_POS = "horizontal_position"
        private const val KEY_ANGULAR_POS = "angular_position"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val FORCE_UPDATE_TIMEOUT = 15000L //15 seconds timeout for force value
        private const val FORCE_UPDATE_INTERVAL = 100L // 100ms update interval
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJibAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isFragmentActive = true
        
        // Initialize force value to 0
        forceValue = 0
        lastForceUpdateTime = 0
        
        // Load saved values (excluding force)
        loadSavedValues()
        
        // Update UI with initial values
        updatePositionDisplay()
        updateHookPosition()
        updateForceDisplay()
        
        // Start force update loop
        startForceUpdateLoop()
    }

    private fun startForceUpdateLoop() {
        mainHandler.post(object : Runnable {
            override fun run() {
                if (isFragmentActive) {
                    updateForceDisplay()
                    mainHandler.postDelayed(this, FORCE_UPDATE_INTERVAL)
                }
            }
        })
    }

    private fun loadSavedValues() {
        val sharedPref = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        isFirstLaunch = sharedPref.getBoolean(KEY_FIRST_LAUNCH, true)
        
        if (isFirstLaunch) {
            // Initialize all values to 0 for first launch
            verticalPosition = 0
            horizontalPosition = 0
            angularPosition = 0
            
            // Save initial values and mark first launch as complete
            saveValues()
            sharedPref.edit().putBoolean(KEY_FIRST_LAUNCH, false).apply()
            Log.i(TAG, "First launch - initialized values to 0")
        } else {
            // Load saved values (excluding force)
            verticalPosition = sharedPref.getInt(KEY_VERTICAL_POS, 0)
            horizontalPosition = sharedPref.getInt(KEY_HORIZONTAL_POS, 0)
            angularPosition = sharedPref.getInt(KEY_ANGULAR_POS, 0)
            Log.i(TAG, "Loaded saved values: V=$verticalPosition, H=$horizontalPosition, A=$angularPosition")
        }
    }

    private fun saveValues() {
        val sharedPref = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt(KEY_VERTICAL_POS, verticalPosition)
            putInt(KEY_HORIZONTAL_POS, horizontalPosition)
            putInt(KEY_ANGULAR_POS, angularPosition)
            apply()
        }
        Log.i(TAG, "Saved values: V=$verticalPosition, H=$horizontalPosition, A=$angularPosition")
    }

    fun processArduinoData(data: String) {
        Log.d(TAG, "Received data: $data") // Log all incoming data
        
        when {
            data.startsWith("Horizontal Position : ") -> {
                try {
                    val value = data.substring("Horizontal Position : ".length).trim().toInt()
                    horizontalPosition = value
                    saveValues() // Save updated value
                    updateHookPosition()
                    updatePositionDisplay()
                    updateForceDisplay() // Update force color based on new position
                    Log.d(TAG, "Processed Horizontal Position: $value")
                } catch (e: NumberFormatException) {
                    Log.e(TAG, "Invalid horizontal position format: ${e.message}")
                }
            }
            data.startsWith("Vertical Position : ") -> {
                try {
                    val value = data.substring("Vertical Position : ".length).trim().toInt()
                    verticalPosition = value
                    Log.i(TAG,"V posi is read as ${verticalPosition}")
                    saveValues() // Save updated value
                    updatePositionDisplay()
                    Log.d(TAG, "Processed Vertical Position: $value")
                } catch (e: NumberFormatException) {
                    Log.e(TAG, "Invalid vertical position format: ${e.message}")
                }
            }
            data.startsWith("Angular Position : ") -> {
                try {
                    val value = data.substring("Angular Position : ".length).trim().toInt()
                    angularPosition = value
                    saveValues() // Save updated value
                    updatePositionDisplay()
                    Log.d(TAG, "Processed Angular Position: $value")
                } catch (e: NumberFormatException) {
                    Log.e(TAG, "Invalid angular position format: ${e.message}")
                }
            }
            data.startsWith("Reading : ") -> {
                try {
                    val value = data.substring("Reading : ".length).trim().toInt()
                    forceValue = value
                    lastForceUpdateTime = System.currentTimeMillis()
                    Log.i(TAG, "Reading is read as ${forceValue}")
                    Log.d(TAG, "Processed Reading: $value")
                    // Force display is updated in the continuous loop
                } catch (e: NumberFormatException) {
                    Log.e(TAG, "Invalid force format: ${e.message}")
                    Log.e(TAG, "Raw data that caused error: $data")
                }
            }
            else -> {
                Log.d(TAG, "Unrecognized data format: $data")
            }
        }
    }

    private fun updatePositionDisplay() {
        if (!isFragmentActive) return
        
        mainHandler.post {
            try {
                binding.verticalPositionText.text = "Vertical Position: $verticalPosition"
                binding.horizontalPositionText.text = "Horizontal Position: $horizontalPosition"
                binding.angularPositionText.text = "Angular Position: $angularPosition"
            } catch (e: Exception) {
                Log.e(TAG, "Error updating position display: ${e.message}")
            }
        }
    }

    private fun updateHookPosition() {
        if (!isFragmentActive) return
        
        mainHandler.post {
            try {
                val scaleWidth = binding.scaleCard.width.toFloat()
                val hookWidth = binding.hookImage.width.toFloat()
                val position = (horizontalPosition / 10f) * (scaleWidth - hookWidth)
                binding.hookImage.translationX = position
                binding.forceValue.translationX = position
            } catch (e: Exception) {
                Log.e(TAG, "Error updating hook position: ${e.message}")
            }
        }
    }

    private fun updateForceDisplay() {
        if (!isFragmentActive) return
        
        mainHandler.post {
            try {
                // Check if force value is stale (not updated for more than FORCE_UPDATE_TIMEOUT)
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastForceUpdateTime > FORCE_UPDATE_TIMEOUT) {
                    forceValue = 0
                    Log.i(TAG, "Force value reset to 0 due to timeout")
                }

                // Calculate force limits based on horizontal position
                val minForce = 1000 // Force limit at position 0
                val maxForce = 200  // Force limit at position 10
                val position = horizontalPosition.coerceIn(0, 10)
                
                // Calculate expected force for current position using linear interpolation
                val expectedForce = minForce - (position * (minForce - maxForce) / 10)
                
                // Calculate color based on force difference
                val forceDiff = (forceValue - expectedForce).toFloat()
                val maxDiff = 200f // Maximum allowed difference
                
                val color = when {
                    forceDiff > maxDiff -> Color.RED
                    else -> {
                        val ratio = (forceDiff + maxDiff) / (2 * maxDiff)
                        val red = (255 * ratio).toInt()
                        val green = (255 * (1 - ratio)).toInt()
                        Color.rgb(red, green, 0)
                    }
                }
                
                binding.forceValue.setTextColor(color)
                binding.forceValue.text = "$forceValue N"
            } catch (e: Exception) {
                Log.e(TAG, "Error updating force display: ${e.message}")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        isFragmentActive = false
    }

    override fun onResume() {
        super.onResume()
        isFragmentActive = true
        startForceUpdateLoop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentActive = false
        mainHandler.removeCallbacksAndMessages(null)
        _binding = null
    }
} 