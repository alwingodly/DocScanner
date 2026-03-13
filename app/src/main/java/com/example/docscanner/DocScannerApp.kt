package com.example.docscanner

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * @HiltAndroidApp — tells Hilt "this is the root of the app, generate your DI container here"
 *
 * Every Hilt app MUST have exactly one Application class with this annotation.
 * Hilt generates a base class behind the scenes that handles all the injection setup.
 *
 * No OpenCV initialization needed — ML Kit initializes automatically via
 * Google Play Services when first used.
 */
@HiltAndroidApp
class DocScannerApp : Application()