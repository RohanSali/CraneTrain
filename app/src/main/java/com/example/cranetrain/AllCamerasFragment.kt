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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.example.cranetrain.databinding.FragmentAllCamerasBinding
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

class AllCamerasFragment : Fragment() {
    private var _binding: FragmentAllCamerasBinding? = null
    private val binding get() = _binding!!
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var webSocketManager: WebSocketManager
    private var activeStreams = 0
    private val cameraProviders = mutableListOf<ProcessCameraProvider>()
    private val previews = mutableListOf<Preview>()
    private val previewViews = mutableListOf<PreviewView>()
    private val noSignalOverlays = mutableListOf<FrameLayout>()
    private val cameraTitles = mutableListOf<TextView>()
    private val toggleButtons = mutableListOf<ImageButton>()
    private val imageViews = mutableListOf<ImageView>() // For WebSocket streams

    private var usbManager: UsbManager? = null
    private var availableDeviceCameras = 0
    private var availableUsbCameras = 0
    private var isPermissionGranted = false
    private var currentDeviceCameraIndex = 0 // 0 for back, 1 for front
    private var lastFrameReceivedTime = 0L
    private val handler = Handler(Looper.getMainLooper())

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
    ): View {
        _binding = FragmentAllCamerasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        super.onViewCreated(view, savedInstanceState)
        Log.i(TAG, "✅ onViewCreated() hit")
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize WebSocket manager
        webSocketManager = WebSocketManager()
        
        // Start device cameras if permissions are granted
        if (allPermissionsGranted()) {
            startDeviceCameras()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
        
        // Connect to WebSocket
        connectToWebSocket()
        
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
            
            // For WebSocket streams (cameras 3-6), add ImageView
            if (i > 2) {
                val imageView = ImageView(requireContext())
                imageView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                (cameraView.findViewById<ViewGroup>(R.id.cameraPreview) as ViewGroup).addView(imageView)
                imageViews.add(imageView)
            }
            
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
                Log.i(TAG, "Found $availableDeviceCameras device cameras")
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
        Log.i(TAG, "Found $availableUsbCameras USB cameras")
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

    private fun startDeviceCameras() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Set up device camera preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewViews[0].surfaceProvider)
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
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
                    
                    Log.i(TAG, "Device camera $cameraId started successfully")
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                    showNoSignal(index)
                }
            } catch (exc: Exception) {
                Log.e(TAG, "Camera provider initialization failed", exc)
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
        try {
            if (index < noSignalOverlays.size) {
        noSignalOverlays[index].visibility = View.VISIBLE
                previewViews[index].visibility = if (index < 2) View.VISIBLE else View.GONE
                if (index >= 2 && index - 2 < imageViews.size) {
                    imageViews[index - 2].visibility = View.GONE
                }
                if (index < 2) { // Only for device cameras
                    toggleButtons[index].visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in showNoSignal for index $index: ${e.message}")
        }
    }

    private fun showAllNoSignal() {
        for (i in 0 until 6) {
            showNoSignal(i)
        }
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
                    return
                }

                val jsonObject = JSONObject(text)
                val keys = jsonObject.keys().asSequence().toList()
                Log.i(TAG, "Parsed JSON object, keys: ${keys.joinToString()}")
                
                if (jsonObject.has("frames")) {
                    val framesArray = jsonObject.getJSONArray("frames")
                    if (framesArray.length() == 0) {
                        Log.e(TAG, "Received empty frames array")
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
                                    // Update WebSocket streams (cameras 3-6)
                                    for (i in 0 until minOf(bitmaps.size, imageViews.size)) {
                                        try {
                                            val cameraIndex = i + 2
                                            if (cameraIndex >= previewViews.size) {
                                                Log.e(TAG, "Camera index $cameraIndex out of bounds")
                                                continue
                                            }

                                            Log.i(TAG, "Setting frame for camera $cameraIndex")
                                            
                                            // Get the parent container
                                            val container = previewViews[cameraIndex].parent as? ViewGroup
                                            if (container == null) {
                                                Log.e(TAG, "Container is null for camera $cameraIndex")
                                                continue
                                            }
                                            
                                            // Create new ImageView with proper dimensions
                                            val newImageView = ImageView(requireContext()).apply {
                                                layoutParams = ViewGroup.LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.MATCH_PARENT
                                                )
                                                scaleType = ImageView.ScaleType.CENTER_CROP
                                                setImageBitmap(bitmaps[i])
                                            }
                                            
                                            // Remove old ImageView if exists
                                            val oldImageView = imageViews[i]
                                            container.removeView(oldImageView)
                                            
                                            // Add new ImageView
                                            container.addView(newImageView)
                                            imageViews[i] = newImageView
                                            
                                            // Update visibility
                                            previewViews[cameraIndex].visibility = View.GONE
                                            newImageView.visibility = View.VISIBLE
                                            noSignalOverlays[cameraIndex].visibility = View.GONE
                                            cameraTitles[cameraIndex].text = "Camera ${cameraIndex + 1}"
                                            
                                            Log.i(TAG, "New ImageView $i created - width: ${newImageView.width}, height: ${newImageView.height}, " +
                                                    "scaleType: ${newImageView.scaleType}, drawable: ${newImageView.drawable != null}")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error updating UI for camera $i: ${e.message}")
                                            e.printStackTrace()
                                        }
                                    }
                                } else {
                                    Log.e(TAG, "Failed to update UI - hasError: $hasError, bitmaps size: ${bitmaps.size}")
                                    // Show error for all WebSocket streams
                                    for (i in 2 until previewViews.size) {
                                        try {
                                            showNoSignal(i)
                                            cameraTitles[i].text = "Camera ${i + 1} (Error)"
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error showing no signal for camera $i: ${e.message}")
                                        }
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
                        // Update WebSocket streams (cameras 3-6)
                        for (i in 2 until previewViews.size) {
                            showNoSignal(i)
                            cameraTitles[i].text = "Camera ${i + 1} (Error)"
                        }
                    }
                } else {
                    Log.e(TAG, "Received message without 'frames' or 'error' key")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing WebSocket message: ${e.message}")
                e.printStackTrace()
                runOnUiThreadIfActive {
                    // Update WebSocket streams (cameras 3-6)
                    for (i in 2 until previewViews.size) {
                        showNoSignal(i)
                        cameraTitles[i].text = "Camera ${i + 1} (Error)"
                    }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket Error: ${t.message}")
            t.printStackTrace()
            runOnUiThreadIfActive {
                Toast.makeText(requireContext(), "WebSocket connection failed: ${t.message}", Toast.LENGTH_SHORT).show()
                // Update WebSocket streams (cameras 3-6)
                for (i in 2 until previewViews.size) {
                    showNoSignal(i)
                    cameraTitles[i].text = "Camera ${i + 1} (Disconnected)"
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed with code $code: $reason")
            runOnUiThreadIfActive {
                // Update WebSocket streams (cameras 3-6)
                for (i in 2 until previewViews.size) {
                    showNoSignal(i)
                    cameraTitles[i].text = "Camera ${i + 1} (Disconnected)"
                }
            }
        }
    }

    private fun connectToWebSocket() {
        webSocketManager.connect(socketListener)
    }

    private fun processWebSocketFrame(bytes: ByteArray) {
        // Process the received frame and update the appropriate preview
        // This will depend on your WebSocket server's frame format
        // For now, we'll just log the size
        Log.i("WebSocket", "Received frame of size: ${bytes.size}")
        
        // Update UI based on active streams
        activity?.runOnUiThread {
            updateCameraPreviews()
        }
    }

    private fun updateCameraPreviews() {
        // Update previews based on active streams
        // Keep device camera previews as is
        // Update WebSocket stream previews
        when (activeStreams) {
            1 -> {
                previewViews[1].visibility = View.GONE
                previewViews[2].visibility = View.GONE
                previewViews[3].visibility = View.GONE
            }
            2 -> {
                previewViews[1].visibility = View.VISIBLE
                previewViews[2].visibility = View.GONE
                previewViews[3].visibility = View.GONE
            }
            3 -> {
                previewViews[1].visibility = View.VISIBLE
                previewViews[2].visibility = View.VISIBLE
                previewViews[3].visibility = View.GONE
            }
            4 -> {
                previewViews[1].visibility = View.VISIBLE
                previewViews[2].visibility = View.VISIBLE
                previewViews[3].visibility = View.VISIBLE
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            requireContext(), it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            // Clean up bitmaps
            for (imageView in imageViews) {
                try {
                    val drawable = imageView.drawable as? BitmapDrawable
                    drawable?.bitmap?.recycle()
                } catch (e: Exception) {
                    Log.e(TAG, "Error recycling bitmap: ${e.message}")
                }
            }
            cameraExecutor.shutdown()
            webSocketManager.disconnect()
            _binding = null
            cameraProviders.forEach { provider ->
                try {
                    provider.unbindAll()
                } catch (e: Exception) {
                    Log.e(TAG, "Error unbinding camera provider", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroyView: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "AllCamerasFragment"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
} 