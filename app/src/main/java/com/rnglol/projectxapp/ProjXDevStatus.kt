package com.rnglol.projectxapp

import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.BatteryManager
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import org.json.JSONObject

class ProjXDevStatus// init gps
    (main_act: MainActivity) {
    private val TAG = "ProjectX/Status"

    // gps
    // course location (see ACCESS_COARSE_LOCATION)
    private val fusedLocationClient: FusedLocationProviderClient
    // fine location (see ACCESS_FINE_LOCATION)
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    // latitude and longitude values to send
    private var latitude = 0.0
    private var longitude = 0.0

    // battery info to send
    // battery level
    private var batteryPct = 0.0
    // battery charge status
    private var chargeStatus = "NONE"

    // MainActivity
    private var mainActivity: MainActivity = main_act

    // constructor
    init {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(mainActivity)
    }

    private fun prepareBatteryStatus() {
        Log.d(TAG, "Preparing battery status")
        // battery charging status mess
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
            mainActivity.registerReceiver(null, ifilter)
        }
        val chStatus: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = chStatus == BatteryManager.BATTERY_STATUS_CHARGING
                || chStatus == BatteryManager.BATTERY_STATUS_FULL

        // charge status
        chargeStatus = if(isCharging) "Charging" else "Discharging"

        // battery level
        batteryPct = batteryStatus?.let { intent ->
            val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            level * 100 / scale.toDouble()
        }?:batteryPct
    }

    // set-up location request
    private fun createLocationRequest() {
        Log.d(TAG, "Creating location request")

        // create fine location request
        locationRequest = LocationRequest.create().apply {
            // intervals of location upd
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // get fine location request
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(mainActivity)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        // add fine location update listener
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations){
                    // update position
                    latitude = location.latitude
                    longitude = location.longitude
                }
                Log.v(TAG, "Forced location update: $latitude/$longitude")
            }
        }

        // get course location request
        fusedLocationClient.requestLocationUpdates(locationRequest,
            locationCallback,
            Looper.getMainLooper())
        // add course location update listeners
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location : Location ->
                // update position
                latitude=location.latitude
                longitude=location.longitude
                Log.v(TAG, "Fused location update: $latitude/$longitude")
            }
            .addOnFailureListener {
                Log.e(TAG, "Fused location Error")
            }
    }

    fun startLocationUpdates() {
        Log.d(TAG, "Start location update")

        // set-up location request
        createLocationRequest()
    }

    fun getAndSendStatus(time_stamp: String, sendJsonUrl: String, androidId: String) {
        Log.d(TAG, "Get and send status")

        // prepare battery status
        prepareBatteryStatus()

        // create JSON which will be sent
        val jsonPos = JSONObject()
        jsonPos.put("time_stamp",time_stamp)
        jsonPos.put("android_id",androidId)
        jsonPos.put("latitude",latitude)
        jsonPos.put("longitude",longitude)
        jsonPos.put("charge_level",batteryPct)
        jsonPos.put("charge_status",chargeStatus)

        // upload JSON to server
        UploadState().execute(sendJsonUrl, jsonPos.toString())
    }
}