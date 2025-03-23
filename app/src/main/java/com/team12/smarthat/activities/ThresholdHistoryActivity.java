package com.team12.smarthat.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.team12.smarthat.R;
import com.team12.smarthat.adapters.ThresholdBreachAdapter;
import com.team12.smarthat.database.DatabaseHelper;
import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;

import java.util.List;

public class ThresholdHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView tvNoData;
    private ThresholdBreachAdapter adapter;
    private DatabaseHelper databaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_threshold_history);
        
        // back button
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Threshold Breach History");
        }

        initializeComponents();
        

        loadThresholdBreaches();
    }
    
    private void initializeComponents() {
        recyclerView = findViewById(R.id.rv_threshold_history);
        tvNoData = findViewById(R.id.tv_no_data);
        
        // recyclerview setup
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ThresholdBreachAdapter(this);
        recyclerView.setAdapter(adapter);
        
    //dbsingletone
        databaseHelper = DatabaseHelper.getInstance();
    }
    
    private void loadThresholdBreaches() {
        // Use custom thresholds from preferences instead of constants
        databaseHelper.getThresholdBreachesWithCustomThresholds(this)
            .observe(this, sensorDataList -> {
                if (sensorDataList != null && !sensorDataList.isEmpty()) {
                    recyclerView.setVisibility(View.VISIBLE);
                    tvNoData.setVisibility(View.GONE);
                    adapter.setBreaches(sensorDataList);
                } else {
                    recyclerView.setVisibility(View.GONE);
                    tvNoData.setVisibility(View.VISIBLE);
                }
            });
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 