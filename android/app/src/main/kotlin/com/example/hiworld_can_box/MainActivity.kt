package com.example.hiworld_can_box

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        val channel = EventChannel(flutterEngine.dartExecutor.binaryMessenger, "teyes_can_stream")
        channel.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                TeyesBroadcastBridge.attach(events)
                TeyesBroadcastBridge.start(applicationContext)
            }

            override fun onCancel(arguments: Any?) {
                TeyesBroadcastBridge.stop(applicationContext)
                TeyesBroadcastBridge.detach()
            }
        })

        val methodChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "teyes_can_control")
        methodChannel.setMethodCallHandler { call, result ->
            when (call.method) {
                "startTest" -> {
                    TeyesBroadcastBridge.start(applicationContext)
                    TeyesBroadcastBridge.startTest(applicationContext)
                    result.success(null)
                }
                "stopTest" -> {
                    TeyesBroadcastBridge.stopTest(applicationContext)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        }
    }
}
