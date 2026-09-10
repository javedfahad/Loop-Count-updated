package com.example.util

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.Display
import android.view.WindowManager

object DisplayRefreshRateHelper {

    /**
     * Attempts to unlock the display's maximum supported refresh rate (e.g., 90Hz, 120Hz, 144Hz)
     * for silky-smooth animations and fluid list scrolling.
     * On Android 6.0+ (API 23+) through Android 11+ (API 30+), it finds the highest refresh rate display mode.
     * On Android 11+ (API 30+), it also configures preferredRefreshRate and preferredDisplayModeId.
     */
    fun enableMaxRefreshRate(activity: Activity) {
        try {
            val window = activity.window ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    activity.display
                } else {
                    @Suppress("DEPRECATION")
                    window.windowManager.defaultDisplay
                }

                if (display != null) {
                    val supportedModes = display.supportedModes
                    // Find mode with highest refresh rate (e.g. 120Hz, 144Hz)
                    val maxMode = supportedModes.maxByOrNull { it.refreshRate }
                    if (maxMode != null && maxMode.refreshRate > 60f) {
                        val layoutParams = window.attributes
                        layoutParams.preferredDisplayModeId = maxMode.modeId
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            layoutParams.preferredRefreshRate = maxMode.refreshRate
                        }
                        window.attributes = layoutParams
                    }
                }
            }
        } catch (_: Exception) {
            // Gracefully ignore if device vendor does not support display mode switching
        }
    }

    /**
     * Returns the current screen refresh rate in Hz (e.g., 60, 90, 120) for display/debug purposes.
     */
    fun getDisplayRefreshRate(context: Context): Float {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display?.refreshRate ?: 60f
            } else {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                @Suppress("DEPRECATION")
                wm?.defaultDisplay?.refreshRate ?: 60f
            }
        } catch (_: Exception) {
            60f
        }
    }

    /**
     * Returns the maximum supported refresh rate in Hz for this device.
     */
    fun getMaxSupportedRefreshRate(context: Context): Float {
        return try {
            val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display
            } else {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                @Suppress("DEPRECATION")
                wm?.defaultDisplay
            }

            if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                display.supportedModes.maxOfOrNull { it.refreshRate } ?: display.refreshRate
            } else {
                display?.refreshRate ?: 60f
            }
        } catch (_: Exception) {
            60f
        }
    }
}
