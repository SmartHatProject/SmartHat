package com.team12.smarthat.utils;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import androidx.core.app.NotificationManagerCompat;
public class NotificationUtils {
private final Context context;
 private final NotificationManagerCompat manager;

 public NotificationUtils(Context context) {
 this.context = context;
  this.manager = NotificationManagerCompat.from(context);
 createNotificationChannel();}

  private void createNotificationChannel() {
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
NotificationChannel channel = new NotificationChannel(
 Constants.NOTIFICATION_CHANNEL_ID,
   "Sensor Alerts",
  NotificationManager.IMPORTANCE_HIGH
);
channel.setDescription("Alerts for environmental threshold breaches");
  manager.createNotificationChannel(channel);}}

    public void sendThresholdAlert(String title, String message) {
 Notification notification = new NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
 .setSmallIcon(android.R.drawable.ic_dialog_alert)
 .setContentTitle(title)
  .setContentText(message)
  .setPriority(NotificationCompat.PRIORITY_HIGH)
  .setAutoCancel(true)
 .build();
 if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
manager.notify(Constants.NOTIFICATION_ID, notification);
        }
    }
    public void sendAlert(String title, String message) {
        sendThresholdAlert(title, message);
    }
}
