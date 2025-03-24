package com.team12.smarthat.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresPermission;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.team12.smarthat.models.SensorData;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for handling notifications
 */
@SuppressLint("MissingPermission")
public class NotificationUtils {
    private final Context context;
    private final NotificationManagerCompat manager;
    
    // notification cooldown
    private final Map<String, Long> lastAlertTimes = new HashMap<>();
    
    //alert cooldown periods
    private static final long ALERT_COOLDOWN = Constants.NOTIFICATION_COOLDOWN_TYPE; // 20sec sametype
    private static final long GENERAL_COOLDOWN = 10000; // 10s

    public NotificationUtils(Context context) {
        this.context = context;
        this.manager = NotificationManagerCompat.from(context);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ID,
                    "SmartHat Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alerts for dust and noise levels");
            channel.enableVibration(true);
            channel.enableLights(true);
            
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * threshold alert notification
     * checks for notification permissions and respects cooldown periods
     */
    @SuppressLint("MissingPermission")
    public void sendThresholdAlert(String title, String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // Check if notifications are enabled for this specific sensor type
                if (!areNotificationsEnabledForType(getAlertTypeFromTitle(title))) {
                    Log.d(Constants.TAG_MAIN, "Alert suppressed because " + getAlertTypeFromTitle(title) + " notifications are disabled: " + title);
                    return;
                }
                
                String alertType = getAlertTypeFromTitle(title);
                if (!shouldShowAlert(alertType)) {
                    Log.d(Constants.TAG_MAIN, "Alert suppressed due to cooldown: " + alertType);
                    return;
                }
                
                // priority notification
                Notification notification = new NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .setDefaults(NotificationCompat.DEFAULT_ALL)
                    .build();
                
                try {
                    recordAlertTime(alertType);
                    // id notifications
                    int notificationId = getNotificationIdForType(alertType);
                    
                    // Check for notification permission on Android 13+
                    if (hasNotificationPermission()) {
                        manager.notify(notificationId, notification);
                        Log.d(Constants.TAG_MAIN, "Alert sent: " + title);
                    } else {
                        Log.e(Constants.TAG_MAIN, "POST_NOTIFICATIONS permission not granted");
                    }
                } catch (SecurityException e) {
                    Log.e(Constants.TAG_MAIN, "Notification permission denied: " + e.getMessage());
                } catch (Exception e) {
                    Log.e(Constants.TAG_MAIN, "Failed to send notification: " + e.getMessage());
                }
            } catch (Exception e) {
                Log.e(Constants.TAG_MAIN, "Error in sendThresholdAlert: " + e.getMessage());
            }
        });
    }
    
    /**
     * cooldown based alerts
     * @param alertType dust/noise
     * @return true if enough time passed since last alert
     */
    private boolean shouldShowAlert(String alertType) {
        long now = System.currentTimeMillis();
        
        // Check general cooldown first (for any notification)
        Long lastGeneralAlert = lastAlertTimes.get("general");
        if (lastGeneralAlert != null && now - lastGeneralAlert < GENERAL_COOLDOWN) {
            return false;
        }
        
        // Check type-specific cooldown
        Long lastTypeAlert = lastAlertTimes.get(alertType);
        return lastTypeAlert == null || now - lastTypeAlert > ALERT_COOLDOWN;
    }
    
    /**
     * record when cooldown alert was sent
     * @param alertType alert type
     */
    private void recordAlertTime(String alertType) {
        long now = System.currentTimeMillis();
        lastAlertTimes.put("general", now);
        lastAlertTimes.put(alertType, now);
    }
    
    /**
     * get alert type from notification title
     * @param title notification title
     * @return alert type string
     */
    private String getAlertTypeFromTitle(String title) {
        if (title == null) {
            return "unknown";
        }
        
        String lowerTitle = title.toLowerCase();
        if (lowerTitle.contains("dust")) {
            return "dust";
        } else if (lowerTitle.contains("noise")) {
            return "noise";
        } else {
            return "general";
        }
    }
    
    /**
     * get notification id for an alert type
     * @param alertType the type of alert
     * @return notification id constant
     */
    private int getNotificationIdForType(String alertType) {
        if (alertType.equalsIgnoreCase("dust")) {
            return Constants.NOTIFICATION_ID_DUST;
        } else if (alertType.equalsIgnoreCase("noise")) {
            return Constants.NOTIFICATION_ID_NOISE;
        } else if (alertType.equalsIgnoreCase("gas")) {
            return Constants.NOTIFICATION_ID_GAS;
        } else {
            return Constants.NOTIFICATION_ID_GENERAL;
        }
    }
    
    /**
     * Send an alert with the given title and message
     * @param title the alert title
     * @param message the alert message
     */
    @SuppressLint("MissingPermission")
    public void sendAlert(String title, String message) {
        sendThresholdAlert(title, message);
    }

    /**
     * Get custom dust threshold
     * @return The user-defined dust threshold or default from Constants
     */
    private float getCustomDustThreshold() {
        return context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .getFloat(Constants.PREF_DUST_THRESHOLD, Constants.DUST_THRESHOLD);
    }
    
    /**
     * Get custom noise threshold
     * @return The user-defined noise threshold or default from Constants
     */
    private float getCustomNoiseThreshold() {
        return context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .getFloat(Constants.PREF_NOISE_THRESHOLD, Constants.NOISE_THRESHOLD);
    }
    
    /**
     * Get custom gas threshold
     * @return The user-defined gas threshold or default from Constants
     */
    private float getCustomGasThreshold() {
        return context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .getFloat(Constants.PREF_GAS_THRESHOLD, Constants.GAS_THRESHOLD);
    }

    /**
     * Show an alert for dust sensor threshold breach
     * @param data the dust sensor data that triggered the alert
     */
    @SuppressLint("MissingPermission")
    public void showDustAlert(SensorData data) {
        // Check if this is test data and adjust the title
        boolean isTestData = data.isTestData();
        float value = data.getValue();
        
        // Get custom threshold if available
        float threshold = getCustomDustThreshold();
        
        String title = isTestData ? "Test Dust Alert" : "Dust Alert";
        String message = String.format("Dust level of %.1f µg/m³ exceeds safe limit (%.1f µg/m³)%s", 
                                      value, threshold,
                                      isTestData ? " [TEST DATA]" : "");
        
        sendAlert(title, message);
    }

    /**
     * Show an alert for noise sensor threshold breach
     * @param data the noise sensor data that triggered the alert
     */
    @SuppressLint("MissingPermission")
    public void showNoiseAlert(SensorData data) {
        // Check if this is test data and adjust the title
        boolean isTestData = data.isTestData();
        float value = data.getValue();
        
        // Get custom threshold if available
        float threshold = getCustomNoiseThreshold();
        
        String title = isTestData ? "Test Noise Alert" : "Noise Alert";
        String message = String.format("Noise level of %.1f dB exceeds safe limit (%.1f dB)%s", 
                                      value, threshold,
                                      isTestData ? " [TEST DATA]" : "");
        
        sendAlert(title, message);
    }

    /**
     * Show an alert for gas sensor threshold breach
     * @param data the gas sensor data that triggered the alert
     */
    @SuppressLint("MissingPermission")
    public void showGasAlert(SensorData data) {
        // Check if this is test data and adjust the title
        boolean isTestData = data.isTestData();
        float value = data.getValue();
        
        // Get custom threshold if available
        float threshold = getCustomGasThreshold();
        
        String title = isTestData ? "Test Gas Alert" : "Gas Alert";
        String message = String.format("Gas level of %.1f ppm exceeds safe limit (%.1f ppm)%s", 
                                      value, threshold,
                                      isTestData ? " [TEST DATA]" : "");
        
        sendAlert(title, message);
    }

    /**
     * Check if the app has POST_NOTIFICATIONS permission on 13+
     * @return true if permission is granted or not needed (Android < 13), false otherwise
     */
    private boolean hasNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return androidx.core.content.ContextCompat.checkSelfPermission(context, 
                    android.Manifest.permission.POST_NOTIFICATIONS) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true; // Permission not needed on older Android versions
    }
    
    /**
     * Check if notifications are enabled in app preferences
     * @return true if notifications are enabled, false otherwise
     */
    public boolean areNotificationsEnabled() {
        // Check system permission, POST_NOTIFICATIONS permission on Android 13+, and app preference
        boolean systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled();
        boolean permissionGranted = hasNotificationPermission();
        boolean appEnabled = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(Constants.PREF_NOTIFICATIONS_ENABLED, true); // Default to enabled
        
        return systemEnabled && appEnabled && permissionGranted;
    }
    
    /**
     * Check if notifications are enabled for a specific sensor type
     * @param alertType the type of alert (dust, noise, or gas)
     * @return true if notifications are enabled for this type, false otherwise
     */
    public boolean areNotificationsEnabledForType(String alertType) {
        // First check if notifications are enabled globally
        if (!areNotificationsEnabled()) {
            return false;
        }
        
        // Then check for specific type
        String prefKey;
        if (alertType.equalsIgnoreCase("dust")) {
            prefKey = Constants.PREF_DUST_NOTIFICATIONS_ENABLED;
        } else if (alertType.equalsIgnoreCase("noise")) {
            prefKey = Constants.PREF_NOISE_NOTIFICATIONS_ENABLED;
        } else if (alertType.equalsIgnoreCase("gas")) {
            prefKey = Constants.PREF_GAS_NOTIFICATIONS_ENABLED;
        } else {
            // For unknown types, fall back to the global setting
            return true;
        }
        
        return context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .getBoolean(prefKey, true); // Default to enabled
    }
    
    /**
     * Set notification enabled/disabled state in app preferences
     * @param enabled true to enable notifications, false to disable
     */
    public void setNotificationsEnabled(boolean enabled) {
        context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(Constants.PREF_NOTIFICATIONS_ENABLED, enabled)
                .apply();
        
        Log.d(Constants.TAG_MAIN, "Notifications " + (enabled ? "enabled" : "disabled") + " by user");
    }
    
    /**
     * Set notification enabled/disabled state for a specific sensor type
     * @param alertType the type of alert (dust, noise, or gas)
     * @param enabled true to enable notifications, false to disable
     */
    public void setNotificationsEnabledForType(String alertType, boolean enabled) {
        String prefKey;
        if (alertType.equalsIgnoreCase("dust")) {
            prefKey = Constants.PREF_DUST_NOTIFICATIONS_ENABLED;
        } else if (alertType.equalsIgnoreCase("noise")) {
            prefKey = Constants.PREF_NOISE_NOTIFICATIONS_ENABLED;
        } else if (alertType.equalsIgnoreCase("gas")) {
            prefKey = Constants.PREF_GAS_NOTIFICATIONS_ENABLED;
        } else {
            // For unknown types, do nothing
            return;
        }
        
        context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(prefKey, enabled)
                .apply();
        
        Log.d(Constants.TAG_MAIN, alertType + " notifications " + (enabled ? "enabled" : "disabled") + " by user");
    }
}
