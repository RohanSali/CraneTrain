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
import android.content.res.ColorStateList
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
import com.example.cranetrain.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private val HC05_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val HC05_MAC_ADDRESS = "98:D3:41:F6:EA:F7" // Your HC-05 MAC address
    private var isConnected = false
    private var isConnecting = false
    private var isMotorAttached = false
    private var currentWindSpeed = 0
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var windDataManager: WindDataManager

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startWindUpdates()
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
                binding.bluetoothStatus.isEnabled = true
            }
            bluetoothSocket?.close()
            bluetoothSocket = null
        }
    }

    // Add periodic command sending runnable
    private val periodicCommandRunnable = object : Runnable {
        override fun run() {
            if (isConnected) {
                sendCommand("7", checkMotorStatus = false) // Don't check motor status for periodic command
            }
            // Schedule the next execution after 5 seconds
            handler.postDelayed(this, 5000)
        }
    }

    private var windUpdateHandler = Handler(Looper.getMainLooper())
    private val REAL_WIND_DURATION = 20000L // 20 seconds for real values
    private val FAKE_WIND_DURATION = 10000L // 10 seconds for fake values
    private val WIND_DIRECTION_CHANGE_INTERVAL = 1800000L // 30 minutes for direction changes
    private var isUsingRandomWind = false
    private var currentWindDirection = 0f // Initial direction
    private var lastRealWindUpdate = 0L // Track last real wind update
    private var windUpdateCycle = 0L // Track the current cycle

    private val windUpdateRunnable = object : Runnable {
        override fun run() {
            if (isUsingRandomWind) {
                val randomWindSpeed = (20..65).random().toFloat()
                updateWindDisplay(randomWindSpeed, currentWindDirection)
                Log.d("Wind", "Random wind update - Speed: $randomWindSpeed m/s, Direction: $currentWindDirection°")
            }
            windUpdateHandler.postDelayed(this, 1000) // Update every second
        }
    }

    private val windDirectionChangeRunnable = object : Runnable {
        override fun run() {
            if (isUsingRandomWind) {
                // Change direction to a random value between 0 and 360 degrees
                currentWindDirection = (0..360).random().toFloat()
                Log.d("Wind", "Wind direction changed to: ${currentWindDirection}°")
            }
            windUpdateHandler.postDelayed(this, WIND_DIRECTION_CHANGE_INTERVAL)
        }
    }

    private val windCycleRunnable = object : Runnable {
        override fun run() {
            windUpdateCycle++
            val cycleTime = System.currentTimeMillis() - lastRealWindUpdate
            
            if (cycleTime < REAL_WIND_DURATION) {
                // Real wind phase (20 seconds)
                isUsingRandomWind = false
                Log.d("Wind", "Real wind phase - Cycle: $windUpdateCycle")
            } else if (cycleTime < REAL_WIND_DURATION + FAKE_WIND_DURATION) {
                // Fake wind phase (10 seconds)
                if (!isUsingRandomWind) {
                    isUsingRandomWind = true
                    Log.d("Wind", "Switching to fake wind values - Cycle: $windUpdateCycle")
                }
            } else {
                // Reset cycle
                lastRealWindUpdate = System.currentTimeMillis()
                windUpdateCycle = 0
                Log.d("Wind", "Resetting wind cycle")
            }
            
            windUpdateHandler.postDelayed(this, 1000) // Check every second
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            setupViewPagers()
            setupBluetoothAdapter()
            setupMotorButton()
            initializeWindDataManager()
            checkLocationPermission()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}")
            showMessage("Error initializing app: ${e.message}", isError = true)
        }
    }

    private fun setupViewPagers() {
        try {
            // Left panel setup
            binding.leftViewPager.adapter = LeftPanelAdapter(this)
            TabLayoutMediator(binding.leftTabLayout, binding.leftViewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> getString(R.string.tab_3d_view)
                    1 -> getString(R.string.tab_single_camera)
                    2 -> getString(R.string.tab_object_analysis)
                    else -> ""
                }
            }.attach()

            // Right panel setup
            binding.rightViewPager.adapter = RightPanelAdapter(this)
            TabLayoutMediator(binding.rightTabLayout, binding.rightViewPager) { tab, position ->
                tab.text = when (position) {
                    0 -> getString(R.string.tab_all_cameras)
                    1 -> getString(R.string.tab_remote_control)
                    2 -> getString(R.string.tab_numeric_control)
                    3 -> getString(R.string.tab_logs)
                    else -> ""
                }
            }.attach()

            // Setup Bluetooth status button
            binding.bluetoothStatus.setOnClickListener {
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
            Log.e("MainActivity", "Error setting up view pagers: ${e.message}")
            showMessage("Error setting up view pagers: ${e.message}", isError = true)
        }
    }

    private fun setupMotorButton() {
        binding.motorButton.setOnClickListener {
            if (currentWindSpeed < 100) {
                isMotorAttached = !isMotorAttached
                updateMotorState()
                val message = if (isMotorAttached) "Motor attached" else "Motor detached"
                showMessage(message, isError = false)
            } else {
                showMessage("Cannot attach motor when wind speed is high", isError = true)
            }
        }
    }

    private fun initializeWindDataManager() {
        windDataManager = WindDataManager(this)
    }

    private fun updateConnectionStatus(status: String, color: Int) {
        // Make the indicator visible when updating status
        binding.connectionIndicator.visibility = View.VISIBLE
        
        // Update the connection indicator color
        when (status) {
            "Connected" -> {
                binding.connectionIndicator.backgroundTintList = ColorStateList.valueOf(Color.GREEN)
                binding.forceStatusIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            }
            "Disconnected" -> {
                binding.connectionIndicator.backgroundTintList = ColorStateList.valueOf(Color.RED)
                binding.forceStatusIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                binding.forceValue.text = "Force: 0N"
            }
            "Connection failed" -> {
                binding.connectionIndicator.backgroundTintList = ColorStateList.valueOf(Color.RED)
                binding.forceStatusIndicator.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
                binding.forceValue.text = "Force: 0N"
            }
            "Connecting..." -> binding.connectionIndicator.backgroundTintList = ColorStateList.valueOf(Color.YELLOW)
        }
        
        // Only log critical connection status changes
        if (status == "Connected" || status == "Disconnected" || status == "Connection failed") {
            val logsFragment = supportFragmentManager.fragments.find { 
                it is LogsFragment && it.isAdded 
            } as? LogsFragment
            logsFragment?.addLog("Connection Status: $status")
        }
    }

    private fun updateMotorState() {
        if (isMotorAttached) {
            binding.motorButton.text = "Motor Attached"
            binding.motorButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
        } else {
            binding.motorButton.text = "Motor Detached"
            binding.motorButton.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        }
    }

    private fun updateWindDisplay(speed: Float, direction: Float) {
        try {
            binding.windSpeedValue.text = String.format("%.1f", speed)
            binding.windDirectionValue.text = String.format("%.0f°", direction)
            
            // Update status indicator based on whether we're using real data
            binding.windStatusIndicator.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    if (isUsingRandomWind) android.R.color.transparent else android.R.color.holo_green_dark
                )
            )
            
            // Update motor state based on wind speed
            checkWindSpeedAndUpdateMotorState(speed.toInt())
        } catch (e: Exception) {
            Log.e("MainActivity", "Error updating wind display: ${e.message}")
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(connectionTimeoutRunnable)
        handler.removeCallbacks(periodicCommandRunnable)
        windUpdateHandler.removeCallbacks(windUpdateRunnable)
        windUpdateHandler.removeCallbacks(windDirectionChangeRunnable)
        windUpdateHandler.removeCallbacks(windCycleRunnable)
        disconnectFromHC05()
        windDataManager.stopUpdates()
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
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
        binding.bluetoothStatus.isEnabled = false

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
                    binding.bluetoothStatus.text = getString(R.string.connected)
                    updateConnectionStatus("Connected", Color.GREEN)
                    showMessage("Successfully connected to HC-05", isError = false)
                    binding.bluetoothStatus.isEnabled = true
                }

                // Start data reading in a separate thread
                startBluetoothDataReading()
                
                // Start periodic command sending
                handler.post(periodicCommandRunnable)
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
                    binding.bluetoothStatus.isEnabled = true
                }
                disconnectFromHC05()
            } catch (e: Exception) {
                handler.removeCallbacks(connectionTimeoutRunnable)
                runOnUiThread {
                    showMessage("Unexpected error: ${e.message}", isError = true)
                    updateConnectionStatus("Connection error", Color.RED)
                    binding.bluetoothStatus.isEnabled = true
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
                    binding.bluetoothStatus.isEnabled = false
                    // Reset force value to 0N
                    binding.forceValue.text = "Force: 0N"
                }
                
                handler.removeCallbacks(connectionTimeoutRunnable)
                handler.removeCallbacks(periodicCommandRunnable) // Stop periodic command sending
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
                    binding.bluetoothStatus.text = getString(R.string.connect_to_model)
                    updateConnectionStatus("Disconnected", Color.RED)
                    binding.bluetoothStatus.isEnabled = true
                }
            }
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

    private fun processReceivedData(data: String) {
        try {
            // Split the data by comma to handle multiple values
            val parts = data.split(",").map { it.trim() }
            
            // Process each part of the data
            parts.forEach { part ->
                when {
                    part.startsWith("Reading:") -> {
                        // Extract the numeric value from "Reading: xxx"
                        val value = part.substringAfter(":").trim()
                        updateForceValue(value)
                    }
                    part.toIntOrNull() != null -> {
                        // This is the wind speed value
                        val windSpeed = part.toInt()
                        checkWindSpeedAndUpdateMotorState(windSpeed)
                    }
                }
            }
            
            // Update logs with received data
            val logsFragment = supportFragmentManager.fragments.find { 
                it is LogsFragment && it.isAdded 
            } as? LogsFragment
            logsFragment?.addLog("Received: $data")
        } catch (e: Exception) {
            Log.e("Bluetooth", "Error processing data: ${e.message}")
        }
    }

    private fun updateForceValue(value: String) {
        runOnUiThread {
            try {
                // Try to convert the value to a number
                val numericValue = value.toDoubleOrNull()
                if (numericValue != null) {
                    // Format the value with 2 decimal places and add 'N' unit
                    binding.forceValue.text = "Force: ${String.format("%.2f", numericValue)} N"
                } else {
                    // If conversion fails, show the raw value with 'N'
                    binding.forceValue.text = "Force: ${value} N"
                }
                Log.d("Bluetooth", "Force value updated to: ${binding.forceValue.text}")
            } catch (e: Exception) {
                Log.e("Bluetooth", "Error updating force value: ${e.message}")
                // Keep the last valid value if there's an error
            }
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startWindUpdates()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun startWindUpdates() {
        // Start with real wind values
        isUsingRandomWind = false
        lastRealWindUpdate = System.currentTimeMillis()
        windUpdateCycle = 0

        // Start all update runnables
        windUpdateRunnable.run()
        windDirectionChangeRunnable.run()
        windCycleRunnable.run()

        // Try to get real wind data
        windDataManager.startUpdates { speed, direction ->
            if (!isUsingRandomWind) {
                updateWindDisplay(speed, direction)
                Log.d("Wind", "Real wind update - Speed: $speed m/s, Direction: $direction°")
            }
        }
    }

    fun sendCommand(command: String, checkMotorStatus: Boolean = true) {
        try {
            if (!isConnected) {
                showMessage("Not connected to HC-05", isError = true)
                return
            }

            // Check motor status for all commands except periodic '7'
            if (checkMotorStatus) {
                if (!isMotorAttached) {
                    showMessage("Cannot send commands when motor is detached", isError = true)
                    return
                }
                
                if (currentWindSpeed > 100) {
                    showMessage("Cannot send commands when wind speed is high", isError = true)
                    return
                }
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