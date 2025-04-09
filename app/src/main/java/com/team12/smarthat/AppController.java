package com.team12.smarthat;

import android.app.Application;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.material.color.DynamicColors;
import com.team12.smarthat.database.DatabaseHelper;

/** 
 * 
 * note: also mentioned in config file
 * 1. this is the only place where databasehelper.initialize() should be called
 * 2. this initialization must happen before any calls to databasehelper.getinstance()
 * 3. all components should use databasehelper.getinstance() to access the singleton instance
 * 4. never create new instances of databasehelper directly
 */
public class AppController extends Application {
    private static final String TAG = "AppController";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler backgroundHandler;
    private Runnable watchdogRunnable;
    private static final long WATCHDOG_INTERVAL_MS = 5000; // 5 second check interval
    private static final long WATCHDOG_TIMEOUT_MS = 2000; // 2 seconds response threshold
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Initialize database
        DatabaseHelper.initialize(this);
        
        // Apply dynamic colors on Android 12+ devices (like Pixel 4a)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivitiesIfAvailable(this);
        }
        
        // Set up simple ANR watchdog - use a more lightweight approach
        setupSimpleWatchdog();
    }
    
    /**
     * Simple watchdog implementation to detect UI thread blockage
     * Using a direct approach with less overhead
     */
    private void setupSimpleWatchdog() {
        // Use a background thread but without a looper to reduce overhead
        Thread watchdogThread = new Thread(() -> {
            try {
                // Create handler in the calling thread without a looper
                backgroundHandler = new Handler(Looper.getMainLooper());
                
                while (!Thread.interrupted()) {
                    // Check main thread responsiveness
                    checkMainThreadResponsiveness();
                    
                    // Sleep between checks to reduce CPU usage
                    Thread.sleep(WATCHDOG_INTERVAL_MS);
                }
            } catch (InterruptedException e) {
                Log.d(TAG, "Watchdog thread interrupted");
            }
        });
        
        watchdogThread.setName("ANR-SimpleWatchdog");
        watchdogThread.setDaemon(true); // Daemon thread won't prevent app exit
        watchdogThread.start();
    }
    
    private void checkMainThreadResponsiveness() {
        final boolean[] responded = {false};
        final long startTime = System.currentTimeMillis();
        
        // Post to main thread
        mainHandler.post(() -> {
            responded[0] = true;
            long delay = System.currentTimeMillis() - startTime;
            if (delay > 500) {
                // Main thread responded but was slow
                Log.w(TAG, "Main thread response delay: " + delay + "ms");
            }
        });
        
        // Wait for response
        try {
            Thread.sleep(WATCHDOG_TIMEOUT_MS);
            
            if (!responded[0]) {
                // Main thread didn't respond in time
                Log.e(TAG, "Possible ANR detected! Main thread unresponsive for " 
                       + WATCHDOG_TIMEOUT_MS + "ms");
                
                // Try recovery actions
                System.gc();
            }
        } catch (InterruptedException e) {
            Log.d(TAG, "Watchdog check interrupted");
        }
    }
    
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        
        // Aggressively handle memory pressure to prevent system killing the app
        if (level >= TRIM_MEMORY_MODERATE) {
            Log.w(TAG, "Memory pressure detected: " + level);
            System.gc();
        }
    }
}