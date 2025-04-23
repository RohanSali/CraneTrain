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
import android.widget.Toast
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
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.Response
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.graphics.drawable.BitmapDrawable

class SingleCameraFragment : Fragment() {
    private var _binding: FragmentSingleCameraBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var webSocketManager: WebSocketManager
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    
    private lateinit var previewView: PreviewView
    private lateinit var noSignalOverlay: FrameLayout
    private val cameraButtons = mutableListOf<Button>()
    private lateinit var imageView: ImageView // For WebSocket streams
    
    private var usbManager: UsbManager? = null
    private var availableDeviceCameras = 0
    private var availableUsbCameras = 0
    private var isPermissionGranted = false
    private var currentCameraIndex = 0 // 0-5 for the 6 possible cameras
    private var isFragmentActive = false
    private var lastFrameReceivedTime = 0L
    private val handler = Handler(Looper.getMainLooper())

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
        
        // Initialize WebSocket manager
        webSocketManager = WebSocketManager()
        
        initializeViews(view)
        checkPermissionAndInitialize()
        
        // Connect to WebSocket
        connectToWebSocket()
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
        
        // Initialize ImageView for WebSocket streams
        imageView = ImageView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        (previewView.parent as? ViewGroup)?.addView(imageView)
        imageView.visibility = View.GONE
        
        showNoSignal()
    }

    private val socketListener = object : WebSocketListener() {
        private fun isFragmentActive(): Boolean {
            return isAdded && !isDetached && activity != null && !activity!!.isFinishing
        }

        private fun runOnUiThreadIfActive(action: () -> Unit) {
            if (isFragmentActive()) {
                activity?.runOnUiThread {
                    if (isFragmentActive()) {
                        try {
                            action()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in UI thread action: ${e.message}")
                        }
                    }
                }
            }
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            try {
                Log.i(TAG, "WebSocket connected successfully")
                runOnUiThreadIfActive {
                    Toast.makeText(requireContext(), "WebSocket connected", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onOpen: ${e.message}")
                e.printStackTrace()
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isFragmentActive()) {
                Log.w(TAG, "Fragment not active, ignoring message")
                return
            }

            try {
                Log.i(TAG, "Received WebSocket message, length: ${text.length}")
                if (text.isEmpty()) {
                    Log.e(TAG, "Received empty message")
                    showNoSignal()
                    return
                }

                val jsonObject = JSONObject(text)
                val keys = jsonObject.keys().asSequence().toList()
                Log.i(TAG, "Parsed JSON object, keys: ${keys.joinToString()}")
                
                if (jsonObject.has("frames")) {
                    val framesArray = jsonObject.getJSONArray("frames")
                    if (framesArray.length() == 0) {
                        Log.e(TAG, "Received empty frames array")
                        showNoSignal()
                        return
                    }
                    
                    Log.i(TAG, "Processing ${framesArray.length()} frames")
                    
                    // Process frames on a background thread
                    Thread {
                        if (!isFragmentActive()) {
                            Log.w(TAG, "Fragment not active, stopping frame processing")
                            return@Thread
                        }

                        try {
                            val bitmaps = mutableListOf<Bitmap>()
                            var hasError = false
                            
                            // Decode all frames first
                            for (i in 0 until framesArray.length()) {
                                if (!isFragmentActive()) {
                                    Log.w(TAG, "Fragment not active, stopping frame decoding")
                                    return@Thread
                                }

                                try {
                                    val base64Image = framesArray.getString(i)
                                    if (base64Image.isEmpty() || base64Image == "null") {
                                        Log.e(TAG, "Frame $i is empty or null")
                                        hasError = true
                                        break
                                    }
                                    
                                    val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)
                                    if (imageBytes.isEmpty()) {
                                        Log.e(TAG, "Frame $i decoded bytes are empty")
                                        hasError = true
                                        break
                                    }
                                    
                                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                    if (bitmap != null) {
                                        Log.i(TAG, "Successfully decoded frame $i: ${bitmap.width}x${bitmap.height}, config: ${bitmap.config}, isMutable: ${bitmap.isMutable}")
                                        bitmaps.add(bitmap)
                                    } else {
                                        Log.e(TAG, "Failed to decode frame $i - BitmapFactory returned null")
                                        hasError = true
                                        break
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error processing frame $i: ${e.message}")
                                    e.printStackTrace()
                                    hasError = true
                                    break
                                }
                            }
                            
                            // Update UI on main thread
                            runOnUiThreadIfActive {
                                if (!isFragmentActive()) {
                                    Log.w(TAG, "Fragment not active, skipping UI update")
                                    return@runOnUiThreadIfActive
                                }

                                if (!hasError && bitmaps.size > 0) {
                                    Log.i(TAG, "Updating UI with ${bitmaps.size} frames")
                                    // Only update if current camera is a WebSocket camera
                                    if (currentCameraIndex >= 2) {
                                        try {
                                            val frameIndex = currentCameraIndex - 2
                                            if (frameIndex < bitmaps.size) {
                                                // Update ImageView
                                                imageView.setImageBitmap(bitmaps[frameIndex])
                                                imageView.visibility = View.VISIBLE
                                                previewView.visibility = View.GONE
                                                noSignalOverlay.visibility = View.GONE
                                                
                                                Log.i(TAG, "Updated WebSocket camera view")
                                            }
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error updating WebSocket camera view: ${e.message}")
                                            e.printStackTrace()
                                            showNoSignal()
                                        }
                                    }
                                } else {
                                    Log.e(TAG, "Failed to update UI - hasError: $hasError, bitmaps size: ${bitmaps.size}")
                                    if (currentCameraIndex >= 2) {
                                        showNoSignal()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error in frame processing thread: ${e.message}")
                            e.printStackTrace()
                        }
                    }.start()
                } else if (jsonObject.has("error")) {
                    val error = jsonObject.getString("error")
                    Log.e(TAG, "Received error from server: $error")
                    runOnUiThreadIfActive {
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                        showNoSignal()
                    }
                } else {
                    Log.e(TAG, "Received message without 'frames' or 'error' key")
                    showNoSignal()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing WebSocket message: ${e.message}")
                e.printStackTrace()
                runOnUiThreadIfActive {
                    showNoSignal()
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket Error: ${t.message}")
            t.printStackTrace()
            runOnUiThreadIfActive {
                Toast.makeText(requireContext(), "WebSocket connection failed: ${t.message}", Toast.LENGTH_SHORT).show()
                showNoSignal()
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed with code $code: $reason")
            runOnUiThreadIfActive {
                showNoSignal()
            }
        }
    }

    private fun connectToWebSocket() {
        webSocketManager.connect(socketListener)
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
        
        // Update WebSocket camera buttons (cameras 3-6)
        for (i in 2..5) {
            val isAvailable = true // WebSocket cameras are always available
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
            index >= 2 -> startWebSocketCamera(index - 2)
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
                    imageView.visibility = View.GONE
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

    private fun startWebSocketCamera(wsIndex: Int) {
        // WebSocket camera is handled by the socketListener
        previewView.visibility = View.GONE
        imageView.visibility = View.GONE
        noSignalOverlay.visibility = View.VISIBLE // Show No Signal initially
    }

    private fun showNoSignal() {
        previewView.visibility = View.GONE
        imageView.visibility = View.GONE
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
        try {
            // Clean up bitmap
            val drawable = imageView.drawable as? BitmapDrawable
            drawable?.bitmap?.recycle()
            
            cameraExecutor.shutdown()
            webSocketManager.disconnect()
            cameraProvider?.unbindAll()
            _binding = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroyView: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "SingleCameraFragment"
    }
} 