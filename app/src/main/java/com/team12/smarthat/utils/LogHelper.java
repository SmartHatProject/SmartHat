package com.team12.smarthat.utils;

import android.util.Log;

/**
 * Helper class for logging that can be easily disabled for production builds
 */
public class LogHelper {
    /**
     * Log a debug message if debug logging is enabled
     * 
     * @param tag Tag for the log message
     * @param message Message to log
     */
    public static void d(String tag, String message) {
        if (Constants.ENABLE_DEBUG_LOGGING) {
            Log.d(tag, message);
        }
    }
    
    /**
     * Log an error message (these will always be logged)
     * 
     * @param tag Tag for the log message
     * @param message Message to log
     */
    public static void e(String tag, String message) {
        Log.e(tag, message);
    }
    
    /**
     * Log an error message with an exception (these will always be logged)
     * 
     * @param tag Tag for the log message
     * @param message Message to log
     * @param throwable Exception to log
     */
    public static void e(String tag, String message, Throwable throwable) {
        Log.e(tag, message, throwable);
    }
    
    /**
     * 
     * @param tag Tag for the log message
     * @param message Message to log
     */
    public static void i(String tag, String message) {
        Log.i(tag, message);
    }
} 