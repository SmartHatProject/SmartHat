package com.team12.smarthat.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.team12.smarthat.R;

/**
 * Activity for visualizing sensor data with graphs and charts.
 * Optimized for Android 12 on Pixel 4a.
 */
public class DataVisualizationActivity extends AppCompatActivity {
    private static final String TAG = "DataVisualizationActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_visualization);
        
        // Setup action bar with back button
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("Data Visualization");
        }
        
        // TODO: Implement data visualization components
        TextView placeholderText = findViewById(R.id.placeholder_text);
        if (placeholderText != null) {
            placeholderText.setText("Data visualization coming soon");
        }
        
        Log.d(TAG, "onCreate completed");
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // Handle the back button in the action bar
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 