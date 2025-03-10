package com.team12.smarthat.utils;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Permission utility for Bluetooth operations
 * Checks for BLUETOOTH_CONNECT, BLUETOOTH_SCAN, and LOCATION which are the permissions we need
 */
public class PermissionUtils {
    /**
     * Check if BLUETOOTH_CONNECT permission is granted
     * This is one of the permissions needed for the Pixel 4A demo
     */
    public static boolean hasBluetoothConnectPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) 
                    == PackageManager.PERMISSION_GRANTED;
            Log.d(Constants.TAG_BLUETOOTH, "BLUETOOTH_CONNECT permission check: " + (hasPermission ? "GRANTED" : "DENIED"));
            return hasPermission;
        }
        // For Android 11 and below, we don't need BLUETOOTH_CONNECT
        return true;
    }
    
    
    public static boolean hasBluetoothScanPermission(@NonNull Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) 
                    == PackageManager.PERMISSION_GRANTED;
            Log.d(Constants.TAG_BLUETOOTH, "BLUETOOTH_SCAN permission check: " + (hasPermission ? "GRANTED" : "DENIED"));
            return hasPermission;
        }
        // For Android 11 and below, we check for the equivalent permission
        boolean hasPermission = ContextCompat.checkSelfPermission(context, 
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        Log.d(Constants.TAG_BLUETOOTH, "BLUETOOTH_SCAN permission check: " + (hasPermission ? "GRANTED" : "DENIED"));
        return hasPermission;
    }
    
    /**
     * Check if ACCESS_FINE_LOCATION permission is granted
     * This is required for BLE scanning on many Android versions
     */
    public static boolean hasLocationPermission(Context context) {
        boolean hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) 
                == PackageManager.PERMISSION_GRANTED;
        Log.d(Constants.TAG_BLUETOOTH, "ACCESS_FINE_LOCATION permission check: " + (hasPermission ? "GRANTED" : "DENIED"));
        return hasPermission;
    }
    
    /**
     * Check if a specific permission is granted
     */
    public static boolean hasPermission(Context context, String permission) {
        boolean hasPermission = ContextCompat.checkSelfPermission(context, permission) 
                == PackageManager.PERMISSION_GRANTED;
        Log.d(Constants.TAG_BLUETOOTH, "Permission check for " + permission + ": " + (hasPermission ? "GRANTED" : "DENIED"));
        return hasPermission;
    }
    
    /**
     * Check if all required Bluetooth permissions are granted based on the Android version
     */
    public static boolean hasRequiredBluetoothPermissions(Context context) {
        boolean result;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires these permissions with neverForLocation
            boolean hasConnect = hasBluetoothConnectPermission(context);
            boolean hasScan = hasBluetoothScanPermission(context);
            result = hasConnect && hasScan;
            Log.d(Constants.TAG_BLUETOOTH, "Android 12+ required permissions check: " +
                  "CONNECT=" + hasConnect + ", SCAN=" + hasScan + ", RESULT=" + result);
        } else {
            // Older Android versions require location permission
            result = hasLocationPermission(context);
            Log.d(Constants.TAG_BLUETOOTH, "Pre-Android 12 required permissions check: " +
                  "LOCATION=" + result);
        }
        return result;
    }
    
    /**
     * Get a list of required permissions based on Android version
     * We need BLUETOOTH_CONNECT, BLUETOOTH_SCAN, and sometimes ACCESS_FINE_LOCATION
     */
    public static List<String> getRequiredPermissions(Context context) {
        List<String> permissions = new ArrayList<>();
        
        // For Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            // Location is still needed for BLE scanning on some devices
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            Log.d(Constants.TAG_BLUETOOTH, "Getting permissions for Android 12+: BLUETOOTH_CONNECT, BLUETOOTH_SCAN");
        } else {
            // For older Android versions
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            Log.d(Constants.TAG_BLUETOOTH, "Getting permissions for pre-Android 12: ACCESS_FINE_LOCATION");
        }
        
        return permissions;
    }

    /**
     * Check if the app has all required Bluetooth permissions
     * @param context The context to check
     * @return true if all required permissions are granted, false otherwise
     */
    public static boolean hasAllBluetoothPermissions(@NonNull Context context) {
        return hasBluetoothConnectPermission(context) && hasBluetoothScanPermission(context);
    }

    /**
     * Run a Bluetooth operation safely with permission checks
     * @param context The context to check permissions
     * @param operation The operation to run if permissions are granted
     * @param errorHandler The handler to call if permissions are not granted
     * @param requiresConnect Whether the operation requires BLUETOOTH_CONNECT permission
     * @param requiresScan Whether the operation requires BLUETOOTH_SCAN permission
     */
    public static void runWithBluetoothPermission(
            @NonNull Context context,
            @NonNull Runnable operation,
            @NonNull Runnable errorHandler,
            boolean requiresConnect,
            boolean requiresScan) {
        
        boolean hasPermission = true;
        
        if (requiresConnect && !hasBluetoothConnectPermission(context)) {
            Log.e(Constants.TAG_BLUETOOTH, "Missing BLUETOOTH_CONNECT permission for operation");
            hasPermission = false;
        }
        
        if (requiresScan && !hasBluetoothScanPermission(context)) {
            Log.e(Constants.TAG_BLUETOOTH, "Missing BLUETOOTH_SCAN permission for operation");
            hasPermission = false;
        }
        
        if (hasPermission) {
            try {
                operation.run();
            } catch (SecurityException se) {
                Log.e(Constants.TAG_BLUETOOTH, "SecurityException during Bluetooth operation: " + se.getMessage());
                errorHandler.run();
            }
        } else {
            errorHandler.run();
        }
    }
}