package com.example.cranetrain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AllCamerasFragment : Fragment() {
    private lateinit var camera1Preview: PreviewView
    private lateinit var camera1NoSignal: FrameLayout
    private lateinit var camera2Preview: SurfaceView
    private lateinit var camera2NoSignal: FrameLayout
    private lateinit var camera3Preview: SurfaceView
    private lateinit var camera3NoSignal: FrameLayout
    private lateinit var camera4Preview: SurfaceView
    private lateinit var camera4NoSignal: FrameLayout

    private var cameraExecutor: ExecutorService? = null
    private var usbManager: UsbManager? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
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

        // Initialize views
        camera1Preview = view.findViewById(R.id.camera1Preview)
        camera1NoSignal = view.findViewById(R.id.camera1NoSignal)
        camera2Preview = view.findViewById(R.id.camera2Preview)
        camera2NoSignal = view.findViewById(R.id.camera2NoSignal)
        camera3Preview = view.findViewById(R.id.camera3Preview)
        camera3NoSignal = view.findViewById(R.id.camera3NoSignal)
        camera4Preview = view.findViewById(R.id.camera4Preview)
        camera4NoSignal = view.findViewById(R.id.camera4NoSignal)

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Initialize USB manager
        usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager

        // Check camera permission
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Show "No Signal" for USB cameras initially
        camera2NoSignal.visibility = View.VISIBLE
        camera3NoSignal.visibility = View.VISIBLE
        camera4NoSignal.visibility = View.VISIBLE
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        requireContext(),
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(cameraProvider)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get camera provider", e)
                camera1NoSignal.visibility = View.VISIBLE
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        try {
            // Unbind any bound use cases before rebinding
            cameraProvider.unbindAll()

            // Create preview use case
            val preview = Preview.Builder().build()

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Set up preview
            preview.setSurfaceProvider(camera1Preview.surfaceProvider)

            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector,
                preview
            )

            // Show preview, hide no signal
            camera1NoSignal.visibility = View.GONE
        } catch (e: Exception) {
            Log.e(TAG, "Use case binding failed", e)
            camera1NoSignal.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor?.shutdown()
    }

    companion object {
        private const val TAG = "AllCamerasFragment"
    }
} 