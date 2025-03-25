package com.team12.smarthat.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.team12.smarthat.R;


public class DataVisualizationActivity extends AppCompatActivity {
    private static final String TAG = "DataVisualizationActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_visualization);
        
       
        Toolbar toolbar = findViewById(R.id.data_viz_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        
        // TODO: Implement data visualization components
        TextView placeholderText = findViewById(R.id.placeholder_text);
        if (placeholderText != null) {
            placeholderText.setText(R.string.data_visualization_placeholder);
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
           //in the action bar
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 