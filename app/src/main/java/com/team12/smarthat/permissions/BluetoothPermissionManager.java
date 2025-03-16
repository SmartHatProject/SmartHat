package com.team12.smarthat.permissions;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.team12.smarthat.utils.Constants;

import java.lang.ref.WeakReference;

/**
 * centeralized ble permission manager
 * 
 * single source of truth
 * version specific permission checks requesting permissions, and managing the result flow
 * Architecture(also explained in read me )
 * 1. MainActivity initializes this manager and passes it to BleConnectionManager
 * 2. BleConnectionManager uses it for permission checks before BLE operations
 * 3. All Bluetooth interactions require permission checks through this manager
 * permission flow
 * 1.check permissions using hasRequiredPermissions()
 * 2.case granted proceed with operation
 * 3. case not granted, request permissions with requestPermissions()
 * 4. Result handled through callbacks or onRequestPermissionsResult()
 */
public class BluetoothPermissionManager {
    // Constants
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 100;
    private static final String TAG = "BluetoothPermMgr";
    
    // Context references
    private final Context appContext;
    private final WeakReference<Activity> activityRef;
    
    // Activity Result Launcher (for modern API)
    private ActivityResultLauncher<String[]> permissionLauncher;
    
    // Permission callback
    private PermissionCallback permissionCallback;
    
    /**
     * Interface for permission request callbacks
     */
    public interface PermissionCallback {
        void onPermissionsGranted();
        void onPermissionsDenied(boolean somePermissionsPermanentlyDenied);
    }
    
    /**
     * Constructor for standard usage with Activity
     */
    public BluetoothPermissionManager(Activity activity) {
        this.activityRef = new WeakReference<>(activity);
        this.appContext = activity.getApplicationContext();
        
        // Only setup launcher if we're using a compatible activity
        if (activity instanceof ComponentActivity) {
            setupPermissionLauncher((ComponentActivity) activity);
        }
    }
    
    /**
     * Setup permission launcher 
     */
    private void setupPermissionLauncher(ComponentActivity activity) {
        permissionLauncher = activity.registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                boolean allGranted = true;
                boolean anyPermanentlyDenied = false;
                
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        // We'd need to check if permanently denied here
                        // But that requires extra logic with shouldShowRequestPermissionRationale
                    }
                }
                
                if (allGranted && permissionCallback != null) {
                    permissionCallback.onPermissionsGranted();
                } else if (permissionCallback != null) {
                    permissionCallback.onPermissionsDenied(anyPermanentlyDenied);
                }
            }
        );
    }
    
    /**
     * array of permissions required for12+
     * @return Array of required permission strings
     */
    public String[] getRequiredPermissions() {
        // Android 12+ (Pixel 4a) requires only these specific permissions
        return new String[] {
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        };
    }
    
    /**
     * Check if all required Bluetooth permissions for 12+ are granted
     * @return True if all required permissions are granted, false otherwise
     */
    public boolean hasRequiredPermissions() {
        // Check Android 12+ Bluetooth permissions
        String[] permissions = getRequiredPermissions();
        
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(appContext, permission) 
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Required permission not granted: " + permission);
                return false;
            }
        }
        
        Log.d(TAG, "All required Android 12+ permissions are granted");
        return true;
    }
    
    /**
     * Request necessary Bluetooth permissions
     */
    public void requestPermissions(PermissionCallback callback) {
        this.permissionCallback = callback;
        
        if (hasRequiredPermissions()) {
            // Already has permissions
            if (callback != null) {
                callback.onPermissionsGranted();
            }
            return;
        }
        
        // Check if we should show rationale for any permission
        boolean shouldShowRationale = false;
        for (String permission : getRequiredPermissions()) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    activityRef.get(), permission)) {
                shouldShowRationale = true;
                break;
            }
        }
        
        if (shouldShowRationale) {
            showPermissionRationale();
        } else {
            // Request permissions directly
            requestPermissionsInternal();
        }
    }
    
    /**
     * Request required permissions without a callback
     */
    public void requestRequiredPermissions() {
        requestPermissions(null);
    }
    
    /**
     * dialog explaining why permissions are needed
     */
    private void showPermissionRationale() {
        Activity activity = activityRef.get();
        if (activity == null) return;
        
        new AlertDialog.Builder(activity)
            .setTitle("Bluetooth Permissions Required")
            .setMessage("SmartHat needs Bluetooth permissions to connect to your device.")
            .setPositiveButton("OK", (dialog, which) -> {
                requestPermissionsInternal();
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                if (permissionCallback != null) {
                    permissionCallback.onPermissionsDenied(false);
                }
            })
            .setCancelable(false)
            .show();
    }
    
    /**
     * Request permissions using appropriate method
     */
    private void requestPermissionsInternal() {
        Activity activity = activityRef.get();
        if (activity == null) return;
        
        // Use Activity Result API if available
        if (permissionLauncher != null) {
            permissionLauncher.launch(getRequiredPermissions());
        } else {
            // Fall back to traditional method
            ActivityCompat.requestPermissions(
                activity,
                getRequiredPermissions(),
                REQUEST_BLUETOOTH_PERMISSIONS
            );
        }
    }
    
    /**
     * Process permission result from traditional onRequestPermissionsResult
     * Returns true if result was handled by this manager
     */
    public boolean handlePermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != REQUEST_BLUETOOTH_PERMISSIONS) {
            return false;
        }
        
        boolean allGranted = true;
        boolean anyPermanentlyDenied = false;
        
        Activity activity = activityRef.get();
        
        for (int i = 0; i < permissions.length; i++) {
            boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
            
            if (!granted) {
                allGranted = false;
                
                // Check if permission is permanently denied
                if (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
                        activity, permissions[i])) {
                    anyPermanentlyDenied = true;
                }
            }
        }
        
        if (allGranted && permissionCallback != null) {
            permissionCallback.onPermissionsGranted();
        } else if (permissionCallback != null) {
            permissionCallback.onPermissionsDenied(anyPermanentlyDenied);
        }
        
        return true;
    }
    
    /**
     * action if granted or request if not
     */
    public void executeWithPermissions(Runnable grantedAction) {
        executeWithPermissions(grantedAction, null);
    }
    
    /**
     * action if gra
     */
    public void executeWithPermissions(Runnable grantedAction, Runnable deniedAction) {
        if (hasRequiredPermissions()) {
            if (grantedAction != null) {
                grantedAction.run();
            }
        } else {
            requestPermissions(new PermissionCallback() {
                @Override
                public void onPermissionsGranted() {
                    if (grantedAction != null) {
                        grantedAction.run();
                    }
                }
                
                @Override
                public void onPermissionsDenied(boolean somePermissionsPermanentlyDenied) {
                    if (deniedAction != null) {
                        deniedAction.run();
                    }
                }
            });
        }
    }
    
    /**
     * Show settings dialog when permissions are permanently denied
     */
    public void showSettingsPermissionDialog() {
        Activity activity = activityRef.get();
        if (activity == null) return;
        
        new AlertDialog.Builder(activity)
            .setTitle("Permissions Required")
            .setMessage("SmartHat requires Bluetooth permissions to function properly. " +
                       "Please enable them in app settings.")
            .setPositiveButton("Settings", (dialog, which) -> {
              //to open app setting
             

                Log.d(TAG, "Would open settings here");
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                Log.d(TAG, "Settings dialog canceled");
                if (permissionCallback != null) {
                    permissionCallback.onPermissionsDenied(true);
                }
            })
            .setCancelable(false)
            .show();
    }
    
    /**
     * Logs
     */
    public void logPermissionStatus() {
        String[] permissions = getRequiredPermissions();
        for (String permission : permissions) {
            boolean granted = ContextCompat.checkSelfPermission(appContext, permission) 
                == PackageManager.PERMISSION_GRANTED;
            Log.d(TAG, "Permission " + permission + ": " + (granted ? "GRANTED" : "DENIED"));
        }
    }
    
    /**
     * Check if BLUETOOTH_CONNECT permission is granted 
     * @return true if granted, false otherwise
     */
    public boolean hasBluetoothConnectPermission() {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) 
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check if BLUETOOTH_SCAN permission is granted
     * @return true if granted
     */
    public boolean hasBluetoothScanPermission() {
        return ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) 
                == PackageManager.PERMISSION_GRANTED;
    }
    
    /**
     * operation that requires BLUETOOTH_CONNECT permission
     * @param grantedAction Action to perform if permission is granted
     * @param deniedAction Action to perform if permission is denied
     */
    public void executeWithConnectPermission(Runnable grantedAction, Runnable deniedAction) {
        if (hasBluetoothConnectPermission()) {
            grantedAction.run();
        } else {
            // Request the specific permissions needed
            String[] permissions = getRequiredPermissions();
            requestPermissions(new PermissionCallback() {
                @Override
                public void onPermissionsGranted() {
                    grantedAction.run();
                }
                
                @Override
                public void onPermissionsDenied(boolean somePermissionsPermanentlyDenied) {
                    if (deniedAction != null) {
                        deniedAction.run();
                    }
                }
            });
        }
    }
    
    /**
     * =operation that requires BLUETOOTH_SCAN permission
     * @param grantedAction Action to perform if permission is granted
     * @param deniedAction Action to perform if permission is denied
     */
    public void executeWithScanPermission(Runnable grantedAction, Runnable deniedAction) {
        if (hasBluetoothScanPermission()) {
            grantedAction.run();
        } else {
            // Request the specific permissions needed
            String[] permissions = getRequiredPermissions();
            requestPermissions(new PermissionCallback() {
                @Override
                public void onPermissionsGranted() {
                    grantedAction.run();
                }
                
                @Override
                public void onPermissionsDenied(boolean somePermissionsPermanentlyDenied) {
                    if (deniedAction != null) {
                        deniedAction.run();
                    }
                }
            });
        }
    }
    
    /**
     * operation that requires Bluetooth permissions on Android 12+
     * This method handles permission checking and proper error handling
     * @param operation The operation to execute if permissions are granted
     * @param errorHandler The handler to call if permissions are not granted
     * @param requireConnect Whether BLUETOOTH_CONNECT permission is required
     * @param requireScan Whether BLUETOOTH_SCAN permission is required
     */
    public void runWithPermissions(
            @NonNull Runnable operation,
            @NonNull Runnable errorHandler,
            boolean requireConnect,
            boolean requireScan) {
            
        boolean hasAllRequiredPermissions = true;
        
        // Check BLUETOOTH_CONNECT permission if required (Android 12+)
        if (requireConnect && !hasBluetoothConnectPermission()) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission for operation");
            hasAllRequiredPermissions = false;
        }
        
        // Check BLUETOOTH_SCAN permission if required (Android 12+)
        if (requireScan && !hasBluetoothScanPermission()) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission for operation");
            hasAllRequiredPermissions = false;
        }
        
        // Run the operation if all permissions are granted
        if (hasAllRequiredPermissions) {
            try {
                operation.run();
            } catch (SecurityException se) {
                Log.e(TAG, "Security exception during Bluetooth operation: " + se.getMessage());
                errorHandler.run();
            } catch (Exception e) {
                Log.e(TAG, "Error during Bluetooth operation: " + e.getMessage());
                errorHandler.run();
            }
        } else {
            // Call error handler if permissions are missing
            errorHandler.run();
        }
    }
} 