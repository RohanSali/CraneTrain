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
import android.widget.FrameLayout
import android.widget.ImageButton
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

class AllCamerasFragment : Fragment() {
    private lateinit var cameraExecutor: ExecutorService
    private val cameraProviders = mutableListOf<ProcessCameraProvider>()
    private val previews = mutableListOf<Preview>()
    private val previewViews = mutableListOf<PreviewView>()
    private val noSignalOverlays = mutableListOf<FrameLayout>()
    private val cameraTitles = mutableListOf<TextView>()
    private val toggleButtons = mutableListOf<ImageButton>()
    
    private var usbManager: UsbManager? = null
    private var availableDeviceCameras = 0
    private var availableUsbCameras = 0
    private var isPermissionGranted = false
    private var currentDeviceCameraIndex = 0 // 0 for back, 1 for front

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isPermissionGranted = isGranted
        if (isGranted) {
            initializeCameras()
        } else {
            showNoSignal(0)
            showNoSignal(1)
            cameraTitles[0].text = "Device Camera (Permission Denied)"
            cameraTitles[1].text = "Device Camera (Permission Denied)"
            toggleButtons[0].visibility = View.GONE
            toggleButtons[1].visibility = View.GONE
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_all_cameras, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        initializeViews(view)
        checkPermissionAndInitialize()
    }

    private fun initializeViews(view: View) {
        for (i in 1..6) {
            val cameraView = view.findViewById<View>(
                resources.getIdentifier("camera$i", "id", requireContext().packageName)
            )
            previewViews.add(cameraView.findViewById(R.id.cameraPreview))
            noSignalOverlays.add(cameraView.findViewById(R.id.noSignalOverlay))
            cameraTitles.add(cameraView.findViewById(R.id.cameraTitle))
            toggleButtons.add(cameraView.findViewById(R.id.toggleCameraButton))
            
            // Initially show "No Signal" for all cameras
            showNoSignal(i-1)
            
            // Set up toggle button click listeners for device cameras
            if (i <= 2) {
                val index = i - 1
                toggleButtons[index].setOnClickListener {
                    toggleDeviceCamera(index)
                }
            }
        }
    }

    private fun toggleDeviceCamera(index: Int) {
        if (index > 1 || !isPermissionGranted) return
        
        currentDeviceCameraIndex = if (currentDeviceCameraIndex == 0) 1 else 0
        startDeviceCamera(index, currentDeviceCameraIndex)
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
                showAllNoSignal()
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun initializeCameras() {
        countAvailableCameras()
        startAvailableCameras()
    }

    private fun countAvailableCameras() {
        if (isPermissionGranted) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
            try {
                val cameraProvider = cameraProviderFuture.get()
                availableDeviceCameras = minOf(cameraProvider.availableCameraInfos.size, 2)
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

    private fun startAvailableCameras() {
        // Handle device cameras
        if (isPermissionGranted && availableDeviceCameras > 0) {
            // Show toggle button only if we have multiple device cameras
            if (availableDeviceCameras > 1) {
                toggleButtons[0].visibility = View.VISIBLE
            }
            startDeviceCamera(0, 0) // Start with back camera
        } else {
            showNoSignal(0)
            showNoSignal(1)
        }

        // Handle USB cameras
        var usbIndex = 0
        for (i in 2 until 6) {
            if (usbIndex < availableUsbCameras) {
                startUsbCamera(i, usbIndex)
                usbIndex++
            } else {
                showNoSignal(i)
            }
        }
    }

    private fun startDeviceCamera(index: Int, cameraId: Int) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewViews[index].surfaceProvider)
                    }

                val cameraSelector = when (cameraId) {
                    0 -> CameraSelector.DEFAULT_BACK_CAMERA
                    1 -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> CameraSelector.DEFAULT_BACK_CAMERA
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        viewLifecycleOwner,
                        cameraSelector,
                        preview
                    )
                    
                    // Update UI
                    cameraTitles[index].text = if (cameraId == 0) "Back Camera" else "Front Camera"
                    noSignalOverlays[index].visibility = View.GONE
                    previewViews[index].visibility = View.VISIBLE
                    
                    // Store provider and preview
                    if (!cameraProviders.contains(cameraProvider)) {
                        cameraProviders.add(cameraProvider)
                    }
                    previews.add(preview)
                    
                    Log.d(TAG, "Device camera $cameraId started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind camera $cameraId", e)
                    showNoSignal(index)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera provider", e)
                showNoSignal(index)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun startUsbCamera(index: Int, usbIndex: Int) {
        cameraTitles[index].text = "USB Camera ${usbIndex + 1}"
        showNoSignal(index)
        // TODO: Implement USB camera initialization when hardware is available
    }

    private fun showNoSignal(index: Int) {
        noSignalOverlays[index].visibility = View.VISIBLE
        previewViews[index].visibility = View.GONE
        if (index < 2) { // Only for device cameras
            toggleButtons[index].visibility = View.GONE
        }
    }

    private fun showAllNoSignal() {
        for (i in 0 until 6) {
            showNoSignal(i)
            cameraTitles[i].text = "Camera ${i + 1}"
        }
    }

    override fun onResume() {
        super.onResume()
        if (isPermissionGranted) {
            initializeCameras()
        } else {
            checkPermissionAndInitialize()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        cameraProviders.forEach { provider ->
            try {
                provider.unbindAll()
            } catch (e: Exception) {
                Log.e(TAG, "Error unbinding camera provider", e)
            }
        }
    }

    companion object {
        private const val TAG = "AllCamerasFragment"
    }
} 