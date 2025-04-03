package com.team12.smarthat.services;

import android.app.Notification;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleService;

import com.team12.smarthat.bluetooth.core.BleConnectionManager;
import com.team12.smarthat.bluetooth.core.BluetoothServiceIntegration;
import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.permissions.BluetoothPermissionManager;
import com.team12.smarthat.utils.Constants;
import com.team12.smarthat.utils.NotificationUtils;

/**
 * Foreground service to maintain BLE connection with SmartHat device
 * when the app is in the background or closed.
 */
public class BleForegroundService extends LifecycleService 
        implements BluetoothServiceIntegration.SensorDataListener {
    
    private static final String TAG = "BleForegroundService";
    
    // Reuse existing components with same configuration
    private BleConnectionManager connectionManager;
    private BluetoothServiceIntegration btIntegration;
    private NotificationUtils notificationUtils;
    
    // Wake lock to improve service reliability during device sleep
    private PowerManager.WakeLock wakeLock;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        
        // Initialize components exactly as in MainActivity
        notificationUtils = new NotificationUtils(this);
        
        // Get existing singleton instance (no new objects created)
        // We can use getApplicationContext instead of Activity since we're using the singleton
        connectionManager = BleConnectionManager.getInstance(getApplicationContext(), null);
        
        // Create service integration using existing connection manager
        btIntegration = new BluetoothServiceIntegration(connectionManager);
        btIntegration.addSensorDataListener(this);
        btIntegration.observeConnectionState(this);
        
        // Check if device is ignoring battery optimizations
        checkBatteryOptimization();
        
        // Acquire a partial wake lock to maintain BLE connection during sleep
        // This only keeps the CPU running at minimum levels needed for BLE
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, 
                                           "SmartHat:BLEServiceWakeLock");
        wakeLock.acquire();
        
        // Start as foreground service with notification
        Notification notification = notificationUtils.createForegroundServiceNotification(
                "SmartHat is monitoring your environment",
                "Connected to ESP32 device"
        );
        startForeground(Constants.NOTIFICATION_ID_GENERAL, notification);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        // Let LifecycleService handle the intent first
        super.onStartCommand(intent, flags, startId);
        
        // Use START_STICKY to ensure service restarts if killed
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");
        
        // Clean up resources
        cleanup();
        
        super.onDestroy();
    }
    
    /**
     * Clean up resources
     */
    private void cleanup() {
        Log.d(TAG, "Cleaning up service resources");
        
        if (btIntegration != null) {
            btIntegration.removeSensorDataListener(this);
        }
        
        // Release wake lock if held
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
        
        // No need to disconnect the Bluetooth device as MainActivity
        // will handle that, and we don't want to interfere with 
        // singleton instance management
    }
    
    @Override
    public void onSensorData(SensorData data, String sensorType) {
        // Handle sensor data from BLE device
        Log.d(TAG, "Received sensor data in background: " + sensorType + " - " + data.getValue());
        
        // Update the notification with latest data
        // This keeps the notification active and prevents the service from being killed
        String contentText = "Last reading: " + sensorType + " - " + data.getValue();
        Notification updatedNotification = notificationUtils.createForegroundServiceNotification(
                "SmartHat is monitoring your environment",
                contentText
        );
        startForeground(Constants.NOTIFICATION_ID_GENERAL, updatedNotification);
    }
    
    /**
     * Check if the app is ignoring battery optimizations and log result
     * Does not disrupt user experience by forcing a permission prompt
     */
    private void checkBatteryOptimization() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            String packageName = getPackageName();
            boolean isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName);
            
            if (isIgnoringBatteryOptimizations) {
                Log.d(TAG, "App is already ignoring battery optimizations");
            } else {
                Log.d(TAG, "App is subject to battery optimizations - background operation may be limited");
                // We only log here but don't prompt the user automatically
                // This can be triggered from settings if user experiences connection issues
            }
        }
    }
    
    /**
     * Request battery optimization exemption if needed
     * This should be called from user settings, not automatically
     * 
     * @return true if app is already ignoring battery optimizations
     */
    public boolean requestBatteryOptimizationExemption() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        String packageName = getPackageName();
        
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return false;
        }
        return true;
    }
} 