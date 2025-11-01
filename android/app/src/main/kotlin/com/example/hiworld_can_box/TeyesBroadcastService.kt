package com.example.hiworld_can_box

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.plugin.common.EventChannel

object TeyesBroadcastBridge {
    private const val TAG = "TeyesCAN"

    // Replace and extend this list as you discover the correct action strings on your device
    val candidateActions: List<String> = listOf(
        // Plausible TEYES/HiWorld/RZC/SYU/RoadRover broadcasts (firmware-dependent)
        // TEYES / HiWorld variants
        "com.teyes.canbus.DATA",
        "com.teyes.canbus.CAN_INFO",
        "com.android.teyes.canbus.ACTION",
        "ru.teyes.can.ACTION_CAN_DATA",
        "com.hiworld.canbus.DATA",
        "com.hiworld.can.CAN_INFO",
        "com.hiworld.teyes.CAN_DATA",
        "com.hiworld.teyes.intent.action.CAN_INFO",
        "com.hiworld.teyes.intent.action.CanInfo",
        // RoadRover / RZC
        "com.roadrover.canbus.ACTION_DATA",
        "com.roadrover.action.CAN",
        // SYU / MTCD-ish
        "com.syu.ms.action.CANBUS",
        "com.syu.canbus.ACTION",
        // Other common after-market stacks
        "com.microntek.canbus",
        "com.zxw.canbus",
        "com.zt.canbus.ACTION",
        // Fallback custom action you can test via adb
        "com.example.hiworld_can_box.TEST_CAN"
    )

    private var eventSink: EventChannel.EventSink? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val testHandler = Handler(Looper.getMainLooper())
    private var testRunnable: Runnable? = null
    private var isTesting: Boolean = false

    fun attach(sink: EventChannel.EventSink) {
        eventSink = sink
        Log.i(TAG, "EventChannel attached")
    }

    fun detach() {
        eventSink = null
        Log.i(TAG, "EventChannel detached")
    }

    fun start(context: Context) {
        if (broadcastReceiver != null) return

        val appContext = context.applicationContext

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                try {
                    val payload = buildPayload(intent)
                    // Emit on UI thread for Flutter
                    mainHandler.post {
                        eventSink?.success(payload)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Error forwarding CAN intent", t)
                }
            }
        }

        val filter = IntentFilter()
        filter.priority = 999 // try to receive ordered broadcasts earlier
        for (action in candidateActions) {
            try {
                filter.addAction(action)
            } catch (t: Throwable) {
                // Ignore malformed action strings
            }
        }

        try {
            appContext.registerReceiver(broadcastReceiver, filter)
            Log.i(TAG, "Registered BroadcastReceiver for ${candidateActions.size} candidate actions")
            // Emit a meta event so the Flutter UI can confirm registration
            mainHandler.post {
                eventSink?.success(mapOf(
                    "event" to "receiver_registered",
                    "timestamp" to System.currentTimeMillis(),
                    "actions" to candidateActions
                ))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register BroadcastReceiver", t)
        }
    }

    fun stop(context: Context) {
        val appContext = context.applicationContext
        broadcastReceiver?.let {
            try {
                appContext.unregisterReceiver(it)
                Log.i(TAG, "Unregistered BroadcastReceiver")
            } catch (t: Throwable) {
                Log.w(TAG, "BroadcastReceiver already unregistered or failed", t)
            }
        }
        broadcastReceiver = null
    }

    fun startTest(context: Context) {
        if (isTesting) return
        isTesting = true
        val appContext = context.applicationContext

        // Ensure receiver is active so we test the actual broadcast -> receiver -> EventChannel path
        start(appContext)

        var tick = 0
        val action = candidateActions.firstOrNull() ?: "com.teyes.canbus.DATA"
        testRunnable = object : Runnable {
            override fun run() {
                if (!isTesting) return
                tick += 1
                val rpm = 700 + (tick % 4000) // 700..4699
                val speed = (tick % 140) // 0..139

                val intent = Intent(action)
                intent.putExtra("rpm", rpm)
                intent.putExtra("speed", speed)
                intent.putExtra("gear", "D")
                intent.putExtra("fuel_level", 65)
                val nested = Bundle()
                nested.putInt("raw0", 0x12)
                nested.putInt("raw1", 0x34)
                intent.putExtra("raw_bundle", nested)

                // 1) Send broadcast so receiver path is exercised
                try {
                    appContext.sendBroadcast(intent)
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to send test broadcast", t)
                }

                // 2) Always also emit directly to EventChannel so emulator/devices with
                //    stricter broadcast rules still see test data immediately
                val payload = buildPayload(intent)
                mainHandler.post { eventSink?.success(payload) }

                testHandler.postDelayed(this, 500L)
            }
        }
        testHandler.post(testRunnable!!)
        Log.i(TAG, "Started test broadcast generator")
    }

    fun stopTest(context: Context) {
        isTesting = false
        testRunnable?.let { testHandler.removeCallbacks(it) }
        testRunnable = null
        Log.i(TAG, "Stopped test broadcast generator")
    }

    private fun buildPayload(intent: Intent): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        map["action"] = intent.action
        map["timestamp"] = System.currentTimeMillis()

        // Copy all extras into the map (sanitized to supported types)
        val extras = intent.extras
        if (extras != null) {
            for (key in extras.keySet()) {
                val value = sanitizeValue(extras.get(key))
                if (value != null) {
                    map[key] = value
                }
            }
        }

        // Normalize common keys to rpm and speed
        val normalized = normalizeRpmAndSpeed(extras)
        normalized["rpm"]?.let { map["rpm"] = it }
        normalized["speed"]?.let { map["speed"] = it }

        return map
    }

    private fun sanitizeValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is Int, is Long, is Double, is Float, is Boolean, is String -> value
            is Short -> value.toInt()
            is Byte -> value.toInt()
            is IntArray -> value.toList()
            is LongArray -> value.toList()
            is FloatArray -> value.toList()
            is DoubleArray -> value.toList()
            is BooleanArray -> value.toList()
            is Array<*> -> value.map { it?.toString() }
            is Bundle -> bundleToMap(value)
            else -> value.toString()
        }
    }

    private fun bundleToMap(bundle: Bundle): Map<String, Any?> {
        val m = mutableMapOf<String, Any?>()
        for (key in bundle.keySet()) {
            m[key] = sanitizeValue(bundle.get(key))
        }
        return m
    }

    private fun normalizeRpmAndSpeed(extras: Bundle?): Map<String, Number> {
        val result = mutableMapOf<String, Number>()
        if (extras == null) return result

        var rpm: Number? = null
        var speed: Number? = null

        val rpmCandidates = listOf("rpm", "RPM", "engineRpm", "engine_rpm", "tacho", "tacho_rpm")
        val speedCandidates = listOf("speed", "Speed", "vehicleSpeed", "vehicle_speed", "veh_speed", "vss")

        for (key in extras.keySet()) {
            if (rpm == null && rpmCandidates.any { key.contains(it, ignoreCase = true) }) {
                rpm = coerceNumber(extras.get(key))
            }
            if (speed == null && speedCandidates.any { key.contains(it, ignoreCase = true) }) {
                speed = coerceNumber(extras.get(key))
            }
        }

        if (rpm != null) result["rpm"] = rpm
        if (speed != null) result["speed"] = speed
        return result
    }

    private fun coerceNumber(value: Any?): Number? {
        return when (value) {
            is Int -> value
            is Long -> value
            is Float -> value.toDouble()
            is Double -> value
            is Short -> value.toInt()
            is Byte -> value.toInt()
            is String -> value.toDoubleOrNull() ?: value.toIntOrNull()
            else -> null
        }
    }
}


