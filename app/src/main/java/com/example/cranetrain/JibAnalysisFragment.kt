package com.example.cranetrain

import android.content.Context
import android.graphics.Color
import android.os.Bundle
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
    private var forceValue = 0

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
        
        // Load saved values
        loadSavedValues()
        
        // Update UI with initial values
        updatePositionDisplay()
        updateHookPosition()
        updateForceDisplay()
    }

    private fun loadSavedValues() {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        verticalPosition = sharedPref.getInt("vertical_position", 0)
        horizontalPosition = sharedPref.getInt("horizontal_position", 0)
        angularPosition = sharedPref.getInt("angular_position", 0)
        forceValue = sharedPref.getInt("force_value", 0)
    }

    private fun saveValues() {
        val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt("vertical_position", verticalPosition)
            putInt("horizontal_position", horizontalPosition)
            putInt("angular_position", angularPosition)
            putInt("force_value", forceValue)
            apply()
        }
    }

    fun updateJibData(vertical: Int, horizontal: Int, angular: Int, force: Int) {
        verticalPosition = vertical
        horizontalPosition = horizontal
        angularPosition = angular
        forceValue = force
        
        saveValues()
        updatePositionDisplay()
        updateHookPosition()
        updateForceDisplay()
    }

    private fun updatePositionDisplay() {
        binding.verticalPositionText.text = "Vertical Position: $verticalPosition"
        binding.horizontalPositionText.text = "Horizontal Position: $horizontalPosition"
        binding.angularPositionText.text = "Angular Position: $angularPosition"
    }

    private fun updateHookPosition() {
        val scaleWidth = binding.scaleCard.width.toFloat()
        val hookWidth = binding.hookImage.width.toFloat()
        val position = (horizontalPosition / 10f) * (scaleWidth - hookWidth)
        binding.hookImage.translationX = position
        binding.forceValue.translationX = position
    }

    private fun updateForceDisplay() {
        // Calculate force color based on position
        val minForce = 1000
        val maxForce = 400
        val forceRange = minForce - maxForce
        val position = horizontalPosition.coerceIn(0, 10)
        
        // Calculate expected force for current position
        val expectedForce = minForce - (position * forceRange / 10)
        
        // Calculate color based on force difference
        val forceDiff = (forceValue - expectedForce).toFloat()
        val maxDiff = 200f // Maximum allowed difference
        
        val color = when {
            forceDiff > maxDiff -> Color.RED
            forceDiff < -maxDiff -> Color.RED
            else -> {
                val ratio = (forceDiff + maxDiff) / (2 * maxDiff)
                val red = (255 * ratio).toInt()
                val green = (255 * (1 - ratio)).toInt()
                Color.rgb(red, green, 0)
            }
        }
        
        binding.forceValue.setTextColor(color)
        binding.forceValue.text = "$forceValue N"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 