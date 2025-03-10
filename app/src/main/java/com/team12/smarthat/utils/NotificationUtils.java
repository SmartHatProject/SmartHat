package com.team12.smarthat.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.team12.smarthat.models.SensorData;

import java.util.HashMap;
import java.util.Map;

public class NotificationUtils {
    private final Context context;
    private final NotificationManagerCompat manager;
    
    // notification cooldown
    private final Map<String, Long> lastAlertTimes = new HashMap<>();
    
    //alert cooldown periods
    private static final long ALERT_COOLDOWN = 30000; // 30s sametype
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
                "Sensor Alerts",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alerts for environmental threshold breaches");
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setBypassDnd(true); // notif can bypass do not disturb
            channel.setShowBadge(true); // app icon
            manager.createNotificationChannel(channel);
        }
    }

    public void sendThresholdAlert(String title, String message) {

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
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
                

                if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    try {

                        recordAlertTime(alertType);
                        // id notifications
                        int notificationId = getNotificationIdForType(alertType);
                        manager.notify(notificationId, notification);
                        Log.d(Constants.TAG_MAIN, "Alert sent: " + title);
                    } catch (SecurityException e) {
                        Log.e(Constants.TAG_MAIN, "Notification permission denied: " + e.getMessage());
                    } catch (Exception e) {
                        Log.e(Constants.TAG_MAIN, "Failed to send notification: " + e.getMessage());
                    }
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
        switch (alertType) {
            case "dust":
                return Constants.NOTIFICATION_ID_DUST;
            case "noise":
                return Constants.NOTIFICATION_ID_NOISE;
            default:
                return Constants.NOTIFICATION_ID_GENERAL;
        }
    }
    
    /**
     * treshold alerts
     */
    public void sendAlert(String title, String message) {
        sendThresholdAlert(title, message);
    }

    /**
     * Show an alert for dust sensor threshold breach
     * @param data the dust sensor data that triggered the alert
     */
    public void showDustAlert(SensorData data) {
        // Show notification for both real and test data
        float value = data.getValue();
        String title = "Dust Alert";
        String message = String.format("Dust level of %.1f µg/m³ exceeds safe limit (%d µg/m³)", 
                                      value, (int)Constants.DUST_THRESHOLD);
        sendAlert(title, message);
    }

    /**
     * Show an alert for noise sensor threshold breach
     * @param data the noise sensor data that triggered the alert
     */
    public void showNoiseAlert(SensorData data) {
        // Show notification for both real and test data
        float value = data.getValue();
        String title = "Noise Alert";
        String message = String.format("Noise level of %.1f dB exceeds safe limit (%d dB)", 
                                      value, (int)Constants.NOISE_THRESHOLD);
        sendAlert(title, message);
    }
}
