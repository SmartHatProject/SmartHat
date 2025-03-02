package com.team12.smarthat;

import android.app.Application;

import com.team12.smarthat.database.DatabaseHelper;

/** app lifecycle manager init & access to shared resources*/
public class AppController extends Application {
    private DatabaseHelper databaseHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        initializeDatabase();
    }

    private void initializeDatabase() {
        databaseHelper = new DatabaseHelper(this);
    }

    public DatabaseHelper getDatabaseHelper() {
        return databaseHelper;
    }
}