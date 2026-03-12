package com.example.docscanner

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import org.opencv.android.OpenCVLoader

/**
 * @HiltAndroidApp — tells Hilt "this is the root of the app, generate your DI container here"
 *
 * Every Hilt app MUST have exactly one Application class with this annotation.
 * Hilt generates a base class behind the scenes that handles all the injection setup.
 *
 * We also init OpenCV here because:
 * - It loads native C++ libraries (.so files) into memory
 * - Must happen ONCE before any OpenCV call
 * - Application.onCreate() runs before any Activity, so it's the safest place
 */
@HiltAndroidApp
class DocScannerApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Load OpenCV native libraries
        if (!OpenCVLoader.initDebug()){
            Log.e(TAG, "OpenCV initialization failed!")
        } else {
            Log.d(TAG, "OpenCV initialized successfully")
        }
    }

    companion object {
        private const val TAG = "DocScannerApp"
    }
}