package com.team12.smarthat.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

/**
 * Utility class for handling runtime permissions with a focus on Android 12+
 */
public class PermissionManager {
    private final AppCompatActivity activity;
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private PermissionCallback callback;

    /**
     * Callback interface for permission results
     */
    public interface PermissionCallback {
        void onPermissionResult(boolean granted);
    }

    /**
     * Constructor initializes the permission manager
     * @param activity Activity that will handle permission requests
     */
    public PermissionManager(AppCompatActivity activity) {
        this.activity = activity;
        initializePermissionLauncher();
    }

    /**
     * Sets up the permission launcher with Activity Result API
     */
    private void initializePermissionLauncher() {
        requestPermissionLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (callback != null) {
                    callback.onPermissionResult(isGranted);
                }
            });
    }

    /**
     * Check if notification permission is granted
     * @param context Context to check permission
     * @return true if permission is granted or not needed (Android < 13)
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, 
                    Manifest.permission.POST_NOTIFICATIONS) == 
                    PackageManager.PERMISSION_GRANTED;
        }
        return true; // Permission not needed on older Android versions
    }

    /**
     * Request notification permission for Android 13+
     * @param callback Callback to handle the permission result
     */
    public void requestNotificationPermission(PermissionCallback callback) {
        this.callback = callback;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, 
                    Manifest.permission.POST_NOTIFICATIONS) != 
                    PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // Already granted
                if (callback != null) {
                    callback.onPermissionResult(true);
                }
            }
        } else {
            // Not needed on older versions
            if (callback != null) {
                callback.onPermissionResult(true);
            }
        }
    }

    /**
     * Opens app settings page for the user to grant permissions manually
     */
    public void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", activity.getPackageName(), null);
        intent.setData(uri);
        activity.startActivity(intent);
    }

    /**
     * Shows a snackbar with a link to app settings for manual permission handling
     * @param view View to anchor the snackbar to
     * @param message Message to display
     */
    public void showPermissionSettingsSnackbar(android.view.View view, String message) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG)
            .setAction("Settings", v -> openAppSettings())
            .show();
    }
} 