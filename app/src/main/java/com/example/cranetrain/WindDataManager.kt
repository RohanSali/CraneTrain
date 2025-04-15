package com.example.cranetrain

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit

class WindDataManager(private val context: Context) {
    private var windSpeed: Float = 0f
    private var windDirection: Float = 0f
    private var isDataAvailable = false
    private var updateCallback: ((Float, Float) -> Unit)? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                fetchWindData(location)
            }
        }
    }

    fun startUpdates(callback: (Float, Float) -> Unit) {
        updateCallback = callback
        if (checkLocationPermission()) {
            startLocationUpdates()
        } else {
            // If no permission, use default values
            updateCallback?.invoke(0f, 0f)
        }
    }

    fun stopUpdates() {
        updateCallback = null
        fusedLocationClient.removeLocationUpdates(locationCallback)
        scope.cancel()
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, TimeUnit.SECONDS.toMillis(30))
            .setMinUpdateIntervalMillis(TimeUnit.SECONDS.toMillis(15))
            .build()

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun fetchWindData(location: Location) {
        scope.launch {
            try {
                val apiKey = "YOUR_OPENWEATHER_API_KEY" // Replace with your API key
                val url = "https://api.openweathermap.org/data/2.5/weather?" +
                        "lat=${location.latitude}&lon=${location.longitude}&appid=$apiKey&units=metric"
                
                val response = withContext(Dispatchers.IO) {
                    URL(url).readText()
                }
                
                val jsonObject = JSONObject(response)
                val windObject = jsonObject.getJSONObject("wind")
                
                windSpeed = windObject.getDouble("speed").toFloat()
                windDirection = windObject.getDouble("deg").toFloat()
                isDataAvailable = true
                
                withContext(Dispatchers.Main) {
                    updateCallback?.invoke(windSpeed, windDirection)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isDataAvailable = false
                withContext(Dispatchers.Main) {
                    updateCallback?.invoke(0f, 0f)
                }
            }
        }
    }

    fun getCurrentWindSpeed(): Float = if (isDataAvailable) windSpeed else 0f
    fun getCurrentWindDirection(): Float = if (isDataAvailable) windDirection else 0f
}