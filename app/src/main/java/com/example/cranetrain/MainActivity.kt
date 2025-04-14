package com.example.cranetrain

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private val HC05_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val HC05_MAC_ADDRESS = "98:D3:41:F6:EA:F7" // Replace with your HC-05 MAC address
    private var isConnected = false
    private var isConnecting = false
    
    private lateinit var forceValue: TextView
    private lateinit var windValue: TextView
    private lateinit var motorStatus: Button
    private lateinit var bluetoothStatus: Button
    private lateinit var connectionStatus: TextView
    private lateinit var statusData: TextView
    private lateinit var threeDData: TextView
    private lateinit var controlsData: TextView
    
    private lateinit var leftViewPager: ViewPager2
    private lateinit var rightViewPager: ViewPager2
    private lateinit var leftTabLayout: TabLayout
    private lateinit var rightTabLayout: TabLayout

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            showMessage("Bluetooth permissions granted", isError = false)
            initiateBluetoothConnection()
        } else {
            showMessage("Bluetooth permissions are required to connect", isError = true)
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            showMessage("Bluetooth enabled", isError = false)
            initiateBluetoothConnection()
        } else {
            showMessage("Bluetooth must be enabled to connect", isError = true)
        }
    }

    private val CONNECTION_TIMEOUT = 10000L // 10 seconds timeout
    private val handler = Handler(Looper.getMainLooper())
    
    private val connectionTimeoutRunnable = Runnable {
        if (isConnecting && !isConnected) {
            isConnecting = false
            runOnUiThread {
                showMessage("Connection timeout. Please try again.", isError = true)
                updateConnectionStatus("Connection timeout", Color.RED)
                bluetoothStatus.isEnabled = true
            }
            bluetoothSocket?.close()
            bluetoothSocket = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupViewPagers()
        setupBluetoothAdapter()
    }

    private fun initializeViews() {
        forceValue = findViewById(R.id.forceValue)
        windValue = findViewById(R.id.windValue)
        motorStatus = findViewById(R.id.motorStatus)
        bluetoothStatus = findViewById(R.id.bluetoothStatus)
        connectionStatus = findViewById(R.id.connectionStatus)
        statusData = findViewById(R.id.statusData)
        threeDData = findViewById(R.id.threeDData)
        controlsData = findViewById(R.id.controlsData)
        leftViewPager = findViewById(R.id.leftViewPager)
        rightViewPager = findViewById(R.id.rightViewPager)
        leftTabLayout = findViewById(R.id.leftTabLayout)
        rightTabLayout = findViewById(R.id.rightTabLayout)

        motorStatus.setOnClickListener {
            toggleMotorStatus()
        }

        bluetoothStatus.setOnClickListener {
            if (isConnecting) {
                showMessage("Connection in progress...", isError = false)
                return@setOnClickListener
            }
            
            if (isConnected) {
                disconnectFromHC05()
            } else {
                checkBluetoothPermissions()
            }
        }

        updateConnectionStatus("Disconnected", Color.RED)
    }

    private fun setupViewPagers() {
        // Left panel setup
        leftViewPager.adapter = LeftPanelAdapter(this)
        TabLayoutMediator(leftTabLayout, leftViewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_3d_view)
                1 -> getString(R.string.tab_single_camera)
                2 -> getString(R.string.tab_object_analysis)
                else -> ""
            }
        }.attach()

        // Right panel setup
        rightViewPager.adapter = RightPanelAdapter(this)
        TabLayoutMediator(rightTabLayout, rightViewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_all_cameras)
                1 -> getString(R.string.tab_remote_control)
                2 -> getString(R.string.tab_numeric_control)
                3 -> getString(R.string.tab_logs)
                else -> ""
            }
        }.attach()
    }

    private fun setupBluetoothAdapter() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            showMessage("Bluetooth is not available on this device", isError = true)
            finish()
            return
        }
    }

    private fun checkBluetoothPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        // Debug: Check each permission individually
        val missingPermissions = mutableListOf<String>()
        permissions.forEach { permission ->
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission)
                showMessage("Missing permission: $permission", isError = true)
            }
        }

        if (missingPermissions.isEmpty()) {
            showMessage("All permissions already granted", isError = false)
            initiateBluetoothConnection()
        } else {
            showMessage("Requesting ${missingPermissions.size} permissions...", isError = false)
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun initiateBluetoothConnection() {
        if (!bluetoothAdapter.isEnabled) {
            showMessage("Requesting Bluetooth enable...", isError = false)
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        } else {
            connectToHC05()
        }
    }

    private fun connectToHC05() {
        if (isConnected || isConnecting) return

        // Check all required permissions
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            showMessage("Missing permissions: ${missingPermissions.joinToString()}", isError = true)
            checkBluetoothPermissions()
            return
        }

        isConnecting = true
        updateConnectionStatus("Connecting...", Color.YELLOW)
        bluetoothStatus.isEnabled = false

        // Set connection timeout
        handler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT)

        thread {
            var tempSocket: BluetoothSocket? = null
            try {
                val device = bluetoothAdapter.getRemoteDevice(HC05_MAC_ADDRESS)
                tempSocket = device.createRfcommSocketToServiceRecord(HC05_UUID)
                
                runOnUiThread {
                    showMessage("Attempting to connect to HC-05...", isError = false)
                }

                // Cancel discovery as it slows down the connection
                bluetoothAdapter.cancelDiscovery()
                
                tempSocket.connect()
                bluetoothSocket = tempSocket
                
                // Connection successful
                handler.removeCallbacks(connectionTimeoutRunnable)
                isConnected = true
                
                runOnUiThread {
                    bluetoothStatus.text = getString(R.string.connected)
                    updateConnectionStatus("Connected", Color.GREEN)
                    showMessage("Successfully connected to HC-05", isError = false)
                    bluetoothStatus.isEnabled = true
                }

                startBluetoothDataReading()
            } catch (e: IOException) {
                // Clean up the socket
                try {
                    tempSocket?.close()
                } catch (closeException: IOException) {
                    closeException.printStackTrace()
                }

                handler.removeCallbacks(connectionTimeoutRunnable)
                runOnUiThread {
                    showMessage("Connection failed: ${e.message}", isError = true)
                    updateConnectionStatus("Connection failed", Color.RED)
                    bluetoothStatus.isEnabled = true
                }
                disconnectFromHC05()
            } catch (e: Exception) {
                handler.removeCallbacks(connectionTimeoutRunnable)
                runOnUiThread {
                    showMessage("Unexpected error: ${e.message}", isError = true)
                    updateConnectionStatus("Connection error", Color.RED)
                    bluetoothStatus.isEnabled = true
                }
                disconnectFromHC05()
            } finally {
                isConnecting = false
            }
        }
    }

    private fun disconnectFromHC05() {
        if (!isConnected && !isConnecting) return

        thread {
            try {
                runOnUiThread {
                    updateConnectionStatus("Disconnecting...", Color.YELLOW)
                    bluetoothStatus.isEnabled = false
                }
                
                handler.removeCallbacks(connectionTimeoutRunnable)
                bluetoothSocket?.close()
                
                runOnUiThread {
                    showMessage("Disconnected from HC-05", isError = false)
                }
            } catch (e: IOException) {
                runOnUiThread {
                    showMessage("Error during disconnection: ${e.message}", isError = true)
                }
            } finally {
                bluetoothSocket = null
                isConnected = false
                isConnecting = false
                runOnUiThread {
                    bluetoothStatus.text = getString(R.string.connect_to_model)
                    updateConnectionStatus("Disconnected", Color.RED)
                    bluetoothStatus.isEnabled = true
                }
            }
        }
    }

    private fun updateConnectionStatus(status: String, color: Int) {
        connectionStatus.text = status
        connectionStatus.setTextColor(color)
    }

    private fun showMessage(message: String, isError: Boolean) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, 
            if (isError) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT)
        
        if (isError) {
            snackbar.setBackgroundTint(Color.RED)
            snackbar.setTextColor(Color.WHITE)
        }
        
        snackbar.show()
    }

    private fun startBluetoothDataReading() {
        thread {
            val inputStream = bluetoothSocket?.inputStream
            val buffer = ByteArray(1024)
            var bytes: Int

            while (isConnected) {
                try {
                    bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val data = String(buffer, 0, bytes).trim()
                        processReceivedData(data)
                    }
                } catch (e: IOException) {
                    runOnUiThread {
                        showMessage("Connection lost: ${e.message}", isError = true)
                    }
                    disconnectFromHC05()
                    break
                }
            }
        }
    }

    private fun processReceivedData(data: String) {
        runOnUiThread {
            try {
                // Split the data by newlines to handle multiple messages
                val messages = data.split("\n")
                
                for (message in messages) {
                    if (message.isNotEmpty()) {
                        // Parse the message format: "TYPE:CONTENT"
                        val parts = message.split(":", limit = 2)
                        if (parts.size == 2) {
                            val type = parts[0].trim()
                            val content = parts[1].trim()
                            
                            when (type.uppercase()) {
                                "STATUS" -> {
                                    statusData.text = content
                                    showMessage("Status update: $content", isError = false)
                                }
                                "3D" -> {
                                    threeDData.text = content
                                    showMessage("3D data: $content", isError = false)
                                }
                                "CONTROL" -> {
                                    controlsData.text = content
                                    showMessage("Control data: $content", isError = false)
                                }
                                else -> {
                                    showMessage("Unknown data type: $type", isError = true)
                                }
                            }
                        } else {
                            // If no type is specified, update all displays
                            statusData.text = message
                            threeDData.text = message
                            controlsData.text = message
                        }
                    }
                }
            } catch (e: Exception) {
                showMessage("Error processing data: ${e.message}", isError = true)
            }
        }
    }

    private fun toggleMotorStatus() {
        val currentStatus = motorStatus.text.toString()
        val newStatus = if (currentStatus == getString(R.string.motor_attached)) {
            getString(R.string.motor_detached)
        } else {
            getString(R.string.motor_attached)
        }
        motorStatus.text = newStatus
    }

    fun sendCommand(command: String) {
        if (!isConnected) {
            Toast.makeText(this, "Not connected to HC-05", Toast.LENGTH_SHORT).show()
            return
        }

        thread {
            try {
                bluetoothSocket?.outputStream?.write(command.toByteArray())
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to send command: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(connectionTimeoutRunnable)
        disconnectFromHC05()
    }
}

class LeftPanelAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ThreeDViewFragment()
            1 -> SingleCameraFragment()
            2 -> ObjectAnalysisFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}

class RightPanelAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AllCamerasFragment()
            1 -> RemoteControlFragment()
            2 -> NumericControlFragment()
            3 -> LogsFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}