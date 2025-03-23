package com.team12.smarthat.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.team12.smarthat.R;
import com.team12.smarthat.databinding.ActivitySettingsBinding;
import com.team12.smarthat.utils.Constants;
import com.team12.smarthat.utils.NotificationUtils;

public class SettingsActivity extends AppCompatActivity {
    
    private NotificationUtils notificationUtils;
    private ActivitySettingsBinding binding;
    private SharedPreferences preferences;
    
    // Keys for saved preferences
    private static final String PREF_DUST_THRESHOLD = "dust_threshold";
    private static final String PREF_NOISE_THRESHOLD = "noise_threshold";
    
    // Default thresholds from Constants
    private float dustThreshold = Constants.DUST_THRESHOLD;
    private float noiseThreshold = Constants.NOISE_THRESHOLD;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Use ViewBinding instead of findViewById
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Initialize toolbar
        setSupportActionBar(binding.settingsToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        
        // Initialize notification utils (reusing existing class)
        notificationUtils = new NotificationUtils(this);
        
        // Get preferences
        preferences = getSharedPreferences("app_prefs", MODE_PRIVATE);
        
        // Load saved thresholds
        loadSavedThresholds();
        
        // Set up UI components
        setupNotificationToggle();
        setupThresholdSliders();
    }
    
    private void loadSavedThresholds() {
        // Load saved values or use defaults from Constants
        dustThreshold = preferences.getFloat(PREF_DUST_THRESHOLD, Constants.DUST_THRESHOLD);
        noiseThreshold = preferences.getFloat(PREF_NOISE_THRESHOLD, Constants.NOISE_THRESHOLD);
    }
    
    private void setupNotificationToggle() {
        // Get current notification state from the existing utility
        boolean notificationsEnabled = notificationUtils.areNotificationsEnabled();
        binding.switchNotifications.setChecked(notificationsEnabled);
        
        // Set listener
        binding.switchNotifications.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Use existing notification utility to change setting
                notificationUtils.setNotificationsEnabled(isChecked);
                Log.d(Constants.TAG_MAIN, "Notifications " + (isChecked ? "enabled" : "disabled") + " in Settings");
            }
        });
    }
    
    private void setupThresholdSliders() {
        // Setup dust threshold slider
        setupDustThresholdSlider();
        
        // Setup noise threshold slider
        setupNoiseThresholdSlider();
    }
    
    private void setupDustThresholdSlider() {
        // Set initial progress based on current threshold
        int progress = (int)((dustThreshold - Constants.DUST_MIN_VALUE) * 100 / 
                (Constants.DUST_MAX_VALUE - Constants.DUST_MIN_VALUE));
        binding.sliderDustThreshold.setProgress(progress);
        
        // Update value text
        updateDustThresholdText(dustThreshold);
        
        // Set listener
        binding.sliderDustThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Calculate new threshold value
                float newThreshold = Constants.DUST_MIN_VALUE + 
                        (progress / 100f) * (Constants.DUST_MAX_VALUE - Constants.DUST_MIN_VALUE);
                
                // Cap at reasonable limits (0-500 μg/m³)
                if (newThreshold > 500f) newThreshold = 500f;
                if (newThreshold < 10f) newThreshold = 10f;
                
                // Update display
                updateDustThresholdText(newThreshold);
                
                // Save new threshold
                if (fromUser) {
                    dustThreshold = newThreshold;
                    saveDustThreshold();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
    
    private void setupNoiseThresholdSlider() {
        // Set initial progress based on current threshold
        int progress = (int)((noiseThreshold - Constants.NOISE_MIN_VALUE) * 100 / 
                (Constants.NOISE_MAX_VALUE - Constants.NOISE_MIN_VALUE));
        binding.sliderNoiseThreshold.setProgress(progress);
        
        // Update value text
        updateNoiseThresholdText(noiseThreshold);
        
        // Set listener
        binding.sliderNoiseThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Calculate new threshold value
                float newThreshold = Constants.NOISE_MIN_VALUE + 
                        (progress / 100f) * (Constants.NOISE_MAX_VALUE - Constants.NOISE_MIN_VALUE);
                
                // Cap at reasonable limits (50-115 dB)
                if (newThreshold < 50f) newThreshold = 50f;
                if (newThreshold > 115f) newThreshold = 115f;
                
                // Update display
                updateNoiseThresholdText(newThreshold);
                
                // Save new threshold
                if (fromUser) {
                    noiseThreshold = newThreshold;
                    saveNoiseThreshold();
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }
    
    private void updateDustThresholdText(float threshold) {
        binding.txtDustThresholdValue.setText(String.format("%.1f μg/m³", threshold));
    }
    
    private void updateNoiseThresholdText(float threshold) {
        binding.txtNoiseThresholdValue.setText(String.format("%.1f dB", threshold));
    }
    
    private void saveDustThreshold() {
        preferences.edit().putFloat(PREF_DUST_THRESHOLD, dustThreshold).apply();
        Log.d(Constants.TAG_MAIN, "Dust threshold set to: " + dustThreshold);
    }
    
    private void saveNoiseThreshold() {
        preferences.edit().putFloat(PREF_NOISE_THRESHOLD, noiseThreshold).apply();
        Log.d(Constants.TAG_MAIN, "Noise threshold set to: " + noiseThreshold);
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
} 