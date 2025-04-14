package com.example.cranetrain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.lifecycle.LifecycleOwner
import com.example.cranetrain.databinding.FragmentSingleCameraBinding

class SingleCameraFragment : Fragment() {
    private var _binding: FragmentSingleCameraBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    
    private lateinit var previewView: PreviewView
    private lateinit var noSignalOverlay: FrameLayout
    private val cameraButtons = mutableListOf<Button>()
    
    private var usbManager: UsbManager? = null
    private var availableDeviceCameras = 0
    private var availableUsbCameras = 0
    private var isPermissionGranted = false
    private var currentCameraIndex = 0 // 0-5 for the 6 possible cameras
    private var isFragmentActive = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isPermissionGranted = isGranted
        if (isGranted) {
            initializeCameras()
        } else {
            showNoSignal()
            updateButtonStates()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSingleCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        isFragmentActive = true
        initializeViews(view)
        checkPermissionAndInitialize()
    }

    private fun initializeViews(view: View) {
        previewView = view.findViewById(R.id.cameraPreview)
        noSignalOverlay = view.findViewById(R.id.noSignalOverlay)
        
        // Initialize camera buttons
        for (i in 1..6) {
            val button = view.findViewById<Button>(
                resources.getIdentifier("camera${i}Button", "id", requireContext().packageName)
            )
            cameraButtons.add(button)
            button.setOnClickListener {
                switchToCamera(i - 1)
            }
        }
        
        showNoSignal()
    }

    private fun checkPermissionAndInitialize() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                isPermissionGranted = true
                initializeCameras()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showNoSignal()
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun initializeCameras() {
        countAvailableCameras()
        updateButtonStates()
        switchToCamera(0) // Start with first available camera
    }

    private fun countAvailableCameras() {
        if (isPermissionGranted) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
            try {
                val provider = cameraProviderFuture.get()
                availableDeviceCameras = minOf(provider.availableCameraInfos.size, 2)
                Log.d(TAG, "Found $availableDeviceCameras device cameras")
            } catch (e: Exception) {
                Log.e(TAG, "Error counting device cameras", e)
                availableDeviceCameras = 0
            }
        } else {
            availableDeviceCameras = 0
        }

        usbManager = requireContext().getSystemService(Context.USB_SERVICE) as? UsbManager
        availableUsbCameras = usbManager?.deviceList?.size ?: 0
        availableUsbCameras = minOf(availableUsbCameras, 4)
        Log.d(TAG, "Found $availableUsbCameras USB cameras")
    }

    private fun updateButtonStates() {
        // Update device camera buttons
        for (i in 0..1) {
            val isAvailable = isPermissionGranted && i < availableDeviceCameras
            cameraButtons[i].isEnabled = isAvailable
            if (isAvailable) {
                cameraButtons[i].text = "Camera ${i + 1}"
                if (i == currentCameraIndex) {
                    cameraButtons[i].setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                } else {
                    cameraButtons[i].setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                }
            } else {
                cameraButtons[i].text = "No Signal"
                cameraButtons[i].setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            }
        }
        
        // Update USB camera buttons
        for (i in 2..5) {
            val isAvailable = (i - 2) < availableUsbCameras
            cameraButtons[i].isEnabled = isAvailable
            if (isAvailable) {
                cameraButtons[i].text = "Camera ${i + 1}"
                if (i == currentCameraIndex) {
                    cameraButtons[i].setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                } else {
                    cameraButtons[i].setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                }
            } else {
                cameraButtons[i].text = "No Signal"
                cameraButtons[i].setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            }
        }
    }

    private fun switchToCamera(index: Int) {
        currentCameraIndex = index
        updateButtonStates()
        
        when {
            index < 2 && isPermissionGranted -> startDeviceCamera(index)
            index >= 2 -> startUsbCamera(index - 2)
            else -> showNoSignal()
        }
    }

    private fun startDeviceCamera(cameraId: Int) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                cameraProvider?.unbindAll()
                cameraProvider = cameraProviderFuture.get()
                
                preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val cameraSelector = when (cameraId) {
                    0 -> CameraSelector.DEFAULT_BACK_CAMERA
                    1 -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> CameraSelector.DEFAULT_BACK_CAMERA
                }

                try {
                    cameraProvider?.bindToLifecycle(
                        this as LifecycleOwner,
                        cameraSelector,
                        preview
                    )
                    
                    // Update UI
                    previewView.visibility = View.VISIBLE
                    noSignalOverlay.visibility = View.GONE
                    
                    Log.d(TAG, "Device camera $cameraId started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind camera $cameraId", e)
                    showNoSignal()
                    updateButtonStates()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera provider", e)
                showNoSignal()
                updateButtonStates()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun startUsbCamera(usbIndex: Int) {
        previewView.visibility = View.GONE
        noSignalOverlay.visibility = View.VISIBLE
        // TODO: Implement USB camera initialization when hardware is available
    }

    private fun showNoSignal() {
        previewView.visibility = View.GONE
        noSignalOverlay.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        isFragmentActive = false
    }

    override fun onResume() {
        super.onResume()
        if (!isFragmentActive) {
            isFragmentActive = true
            initializeCameras()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
        _binding = null
    }

    companion object {
        private const val TAG = "SingleCameraFragment"
    }
} 