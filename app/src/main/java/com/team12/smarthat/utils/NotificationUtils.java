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

public class NotificationUtils {
    private final Context context;
    private final NotificationManagerCompat manager;
    private long lastDustNotifTime = 0;
    private long lastNoiseNotifTime = 0;
    private static final long COOLDOWN_PERIOD = 10000; // 10 secs between same type notifications

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
        // hande on main thread to avoid ANRs
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                // priority notification
                Notification notification = new NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setPriority(NotificationCompat.PRIORITY_MAX) //max -> immediate , high _> no imm
                    .setCategory(NotificationCompat.CATEGORY_ALARM) // treat as an alarm for higher priority but might change
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(true)
                    .setDefaults(NotificationCompat.DEFAULT_ALL) // enable default notif
                    .build();
                
                boolean canNotify = false;
                
                // cooldowsn for each sensor , might change later
                if (title.contains("Dust")) {
                    long now = System.currentTimeMillis();
                    if (now - lastDustNotifTime > COOLDOWN_PERIOD) {
                        lastDustNotifTime = now;
                        canNotify = true;
                    } else {
                        Log.d(Constants.TAG_MAIN, "Dust notification suppressed (cooldown period)");
                    }
                } else if (title.contains("Noise")) {
                    long now = System.currentTimeMillis();
                    if (now - lastNoiseNotifTime > COOLDOWN_PERIOD) {
                        lastNoiseNotifTime = now;
                        canNotify = true;
                    } else {
                        Log.d(Constants.TAG_MAIN, "Noise notification suppressed (cooldown period)");
                    }
                } else {
                    // no cooldown for other types
                    canNotify = true;
                }
                
                // notif only if cooldown passed or a different type
                if (canNotify && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    // notf ids for each sensor
                    int notificationId;
                    if (title.contains("Dust")) {
                        notificationId = Constants.NOTIFICATION_ID_DUST;
                    } else if (title.contains("Noise")) {
                        notificationId = Constants.NOTIFICATION_ID_NOISE;
                    } else {
                        notificationId = Constants.NOTIFICATION_ID_GENERAL;
                    }
                    
                    try {
                        manager.notify(notificationId, notification);
                        Log.d(Constants.TAG_MAIN, "Notification sent: " + title);
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
    
    public void sendAlert(String title, String message) {
        sendThresholdAlert(title, message);
    }
}
