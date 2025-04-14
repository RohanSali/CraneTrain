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
import android.util.Log
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
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private val HC05_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val HC05_MAC_ADDRESS = "98:D3:41:F6:EA:F7" // Your HC-05 MAC address
    private var isConnected = false
    private var isConnecting = false
    private var isMotorAttached = false
    private var currentWindSpeed = 0
    
    private lateinit var bluetoothStatus: Button
    private lateinit var connectionStatus: TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            initializeViews()
            setupViewPagers()
            setupBluetoothAdapter()

            // Initialize Bluetooth adapter
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

            // Set up motor button
            val motorButton = findViewById<Button>(R.id.motorButton)
            motorButton?.setOnClickListener {
                if (currentWindSpeed < 100) {
                    isMotorAttached = !isMotorAttached
                    updateMotorState()
                    val message = if (isMotorAttached) "Motor attached" else "Motor detached"
                    showMessage(message, isError = false)
                } else {
                    showMessage("Cannot attach motor when wind speed is high", isError = true)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}")
            showMessage("Error initializing app: ${e.message}", isError = true)
        }
    }

    private fun initializeViews() {
        try {
            bluetoothStatus = findViewById(R.id.bluetoothStatus) ?: throw IllegalStateException("bluetoothStatus not found")
            connectionStatus = findViewById(R.id.connectionStatus) ?: throw IllegalStateException("connectionStatus not found")
            leftViewPager = findViewById(R.id.leftViewPager) ?: throw IllegalStateException("leftViewPager not found")
            rightViewPager = findViewById(R.id.rightViewPager) ?: throw IllegalStateException("rightViewPager not found")
            leftTabLayout = findViewById(R.id.leftTabLayout) ?: throw IllegalStateException("leftTabLayout not found")
            rightTabLayout = findViewById(R.id.rightTabLayout) ?: throw IllegalStateException("rightTabLayout not found")

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
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing views: ${e.message}")
            showMessage("Error initializing views: ${e.message}", isError = true)
        }
    }

    private fun setupViewPagers() {
        try {
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
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up view pagers: ${e.message}")
            showMessage("Error setting up view pagers: ${e.message}", isError = true)
        }
    }

    private fun setupBluetoothAdapter() {
        try {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothAdapter = bluetoothManager?.adapter
            if (bluetoothAdapter == null) {
                showMessage("Bluetooth is not available on this device", isError = true)
                finish()
                return
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up Bluetooth adapter: ${e.message}")
            showMessage("Error setting up Bluetooth adapter: ${e.message}", isError = true)
        }
    }

    private fun checkBluetoothPermissions() {
        try {
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
                    Log.d("Bluetooth", "Missing permission: $permission")
                }
            }

            if (missingPermissions.isEmpty()) {
                Log.d("Bluetooth", "All permissions already granted")
                initiateBluetoothConnection()
            } else {
                Log.d("Bluetooth", "Requesting ${missingPermissions.size} permissions...")
                requestPermissionLauncher.launch(missingPermissions.toTypedArray())
            }
        } catch (e: Exception) {
            Log.e("Bluetooth", "Error checking permissions: ${e.message}")
            showMessage("Error checking permissions: ${e.message}", isError = true)
        }
    }

    private fun initiateBluetoothConnection() {
        try {
            if (bluetoothAdapter == null) {
                showMessage("Bluetooth is not available on this device", isError = true)
                return
            }

            if (!bluetoothAdapter!!.isEnabled) {
                showMessage("Requesting Bluetooth enable...", isError = false)
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                enableBluetoothLauncher.launch(enableBtIntent)
            } else {
                connectToHC05()
            }
        } catch (e: Exception) {
            Log.e("Bluetooth", "Error initiating connection: ${e.message}")
            showMessage("Error initiating Bluetooth connection: ${e.message}", isError = true)
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
                val device = bluetoothAdapter?.getRemoteDevice(HC05_MAC_ADDRESS)
                runOnUiThread {
                    showMessage("Found device: ${device?.name}", isError = false)
                }

                // Try different methods to create socket
                try {
                    tempSocket = device?.createRfcommSocketToServiceRecord(HC05_UUID)
                } catch (e: IOException) {
                    runOnUiThread {
                        showMessage("Failed to create socket with UUID, trying alternative method", isError = true)
                    }
                    // Alternative method for some devices
                    val m = device?.javaClass?.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    tempSocket = m?.invoke(device, 1) as BluetoothSocket
                }

                runOnUiThread {
                    showMessage("Attempting to connect to HC-05...", isError = false)
                }

                // Cancel discovery as it slows down the connection
                bluetoothAdapter?.cancelDiscovery()
                
                tempSocket?.connect()
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

                // Start data reading in a separate thread
                startBluetoothDataReading()
                
                // Send a test message to verify communication
                sendCommand("TEST")
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
        
        // Only log critical connection status changes
        if (status == "Connected" || status == "Disconnected" || status == "Connection failed") {
            val logsFragment = supportFragmentManager.fragments.find { 
                it is LogsFragment && it.isAdded 
            } as? LogsFragment
            logsFragment?.addLog("Connection Status: $status")
        }
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
            try {
                val inputStream = bluetoothSocket?.inputStream
                if (inputStream == null) {
                    Log.e("Bluetooth", "Input stream is null")
                    disconnectFromHC05()
                    return@thread
                }

                val buffer = ByteArray(100) // Buffer for up to 100 characters
                val stringBuilder = StringBuilder()

                while (isConnected) {
                    try {
                        // Add a small delay to prevent CPU overuse
                        Thread.sleep(10)
                        
                        if (inputStream.available() > 0) {
                            val bytes = inputStream.read(buffer)
                            if (bytes > 0) {
                                for (i in 0 until bytes) {
                                    val char = buffer[i].toInt().toChar()
                                    stringBuilder.append(char)
                                    
                                    // Process the line when we get a newline or reach 100 characters
                                    if (char == '\n' || stringBuilder.length >= 100) {
                                        val data = stringBuilder.toString().trim()
                                        if (data.isNotEmpty()) {
                                            runOnUiThread {
                                                processReceivedData(data)
                                            }
                                        }
                                        stringBuilder.clear()
                                    }
                                }
                            }
                        }
                    } catch (e: IOException) {
                        Log.e("Bluetooth", "Error reading data: ${e.message}")
                        runOnUiThread {
                            val logsFragment = supportFragmentManager.fragments.find { 
                                it is LogsFragment && it.isAdded 
                            } as? LogsFragment
                            logsFragment?.addLog("Connection Status: Connection failed")
                            showMessage("Connection lost: ${e.message}", isError = true)
                        }
                        disconnectFromHC05()
                        break
                    } catch (e: InterruptedException) {
                        Log.d("Bluetooth", "Data reading thread interrupted")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("Bluetooth", "Error in data reading thread: ${e.message}")
                disconnectFromHC05()
            }
        }
    }

    private fun updateUIWithData(parts: List<String>) {
        try {
            if (parts.isEmpty()) return

            // Update wind speed and check motor state
            val windSpeed = parts[0].toIntOrNull()
            if (windSpeed != null) {
                checkWindSpeedAndUpdateMotorState(windSpeed)
            }

            // Update logs with received data
            val logsFragment = supportFragmentManager.fragments.find { 
                it is LogsFragment && it.isAdded 
            } as? LogsFragment
            logsFragment?.addLog("Received: ${parts.joinToString(", ")}")
        } catch (e: Exception) {
            Log.e("Bluetooth", "Error updating UI with data: ${e.message}")
        }
    }

    private fun processReceivedData(data: String) {
        try {
            // Split the data by commas
            val parts = data.split(",")
            if (parts.isNotEmpty()) {
                // Update wind speed and check motor state
                val windSpeed = parts[0].toIntOrNull()
                if (windSpeed != null) {
                    checkWindSpeedAndUpdateMotorState(windSpeed)
                }

                // Update UI with received data
                updateUIWithData(parts)
            }
        } catch (e: Exception) {
            Log.e("Bluetooth", "Error processing data: ${e.message}")
        }
    }

    private fun updateMotorState() {
        val motorButton = findViewById<Button>(R.id.motorButton)
        if (isMotorAttached) {
            motorButton.text = "Motor Attached"
            motorButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            motorButton.text = "Motor Detached"
            motorButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        }
    }

    private fun checkWindSpeedAndUpdateMotorState(windSpeed: Int) {
        currentWindSpeed = windSpeed
        if (windSpeed > 100) {
            isMotorAttached = false
            updateMotorState()
            showMessage("Motor automatically detached due to high wind speed", isError = true)
        }
    }

    fun sendCommand(command: String) {
        try {
            if (!isMotorAttached) {
                showMessage("Cannot send commands when motor is detached", isError = true)
                return
            }
            
            if (currentWindSpeed > 100) {
                showMessage("Cannot send commands when wind speed is high", isError = true)
                return
            }

            if (!isConnected) {
                showMessage("Not connected to HC-05", isError = true)
                return
            }

            val outputStream = bluetoothSocket?.outputStream
            if (outputStream == null) {
                showMessage("Failed to send command: Output stream is null", isError = true)
                disconnectFromHC05()
                return
            }

            // Add newline to ensure command is properly terminated
            val commandWithNewline = "$command\n"
            outputStream.write(commandWithNewline.toByteArray())
            outputStream.flush() // Ensure data is sent immediately
            
            // Log the sent command
            val logsFragment = supportFragmentManager.fragments.find { 
                it is LogsFragment && it.isAdded 
            } as? LogsFragment
            logsFragment?.addLog("Sent: $command")
            
            Log.d("Bluetooth", "Command sent: $command")
        } catch (e: IOException) {
            Log.e("Bluetooth", "Error sending command: ${e.message}")
            showMessage("Failed to send command: ${e.message}", isError = true)
            disconnectFromHC05()
        } catch (e: Exception) {
            Log.e("Bluetooth", "Unexpected error sending command: ${e.message}")
            showMessage("Unexpected error sending command: ${e.message}", isError = true)
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
        val fragment = when (position) {
            0 -> ThreeDViewFragment()
            1 -> SingleCameraFragment()
            2 -> ObjectAnalysisFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
        // Set a unique tag for each fragment
        fragment.arguments = Bundle().apply {
            putString("TAG", "left_$position")
        }
        return fragment
    }
}

class RightPanelAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        val fragment = when (position) {
            0 -> AllCamerasFragment()
            1 -> RemoteControlFragment()
            2 -> NumericControlFragment()
            3 -> LogsFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
        // Set a unique tag for each fragment
        fragment.arguments = Bundle().apply {
            putString("TAG", "right_$position")
        }
        return fragment
    }
}