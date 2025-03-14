package com.team12.smarthat;

import android.app.Application;

import com.team12.smarthat.database.DatabaseHelper;

/** 
 * 
 * NOTE ALSO MENTIONED IN CONFIG FILE
 * 1. This is the ONLY place where DatabaseHelper.initialize() should be called
 * 2. This initialization must occur before any calls to DatabaseHelper.getInstance()
 * 3. All components should use DatabaseHelper.getInstance() to access the singleton instance
 * 4. Never create new instances of DatabaseHelper directly
 */
public class AppController extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        DatabaseHelper.initialize(this);
    }
}