package com.team12.smarthat;

import android.app.Application;

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
    @Override
    public void onCreate() {
        super.onCreate();
        
        DatabaseHelper.initialize(this);
    }
}