package com.team12.smarthat.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.google.android.material.slider.Slider;
import com.team12.smarthat.R;
import com.team12.smarthat.databinding.ActivitySettingsBinding;
import com.team12.smarthat.utils.Constants;
import com.team12.smarthat.utils.NotificationUtils;
import com.team12.smarthat.utils.PermissionManager;

public class SettingsActivity extends AppCompatActivity {
    
    private NotificationUtils notificationUtils;
    private ActivitySettingsBinding binding;
    private SharedPreferences preferences;
    private PermissionManager permissionManager;
    
    // Default thresholds from Constants
    private float dustThreshold = Constants.DUST_THRESHOLD;
    private float noiseThreshold = Constants.NOISE_THRESHOLD;
    private float gasThreshold = Constants.GAS_THRESHOLD;
    
    // Track if values have changed
    private boolean thresholdsChanged = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Use ViewBinding instead of findViewById
            binding = ActivitySettingsBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
            
        // Initialize toolbar
            setSupportActionBar(binding.settingsToolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
            
            // Initialize permission manager
            permissionManager = new PermissionManager(this);
            
            // Initialize notification utils (reusing existing class)
        notificationUtils = new NotificationUtils(this);
        
        // Get preferences
        preferences = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
                    
                    // Restore state if available
                    if (savedInstanceState != null) {
            dustThreshold = savedInstanceState.getFloat("dust_threshold", Constants.DUST_THRESHOLD);
            noiseThreshold = savedInstanceState.getFloat("noise_threshold", Constants.NOISE_THRESHOLD);
            gasThreshold = savedInstanceState.getFloat("gas_threshold", Constants.GAS_THRESHOLD);
            thresholdsChanged = savedInstanceState.getBoolean("thresholds_changed", false);
                    } else {
                        // Load saved thresholds
            loadSavedThresholds();
        }
        
        // Set up UI components
        setupNotificationToggles();
        setupThresholdSliders();
        setupResetButton();
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // Save current slider values for configuration changes
        outState.putFloat("dust_threshold", dustThreshold);
        outState.putFloat("noise_threshold", noiseThreshold);
        outState.putFloat("gas_threshold", gasThreshold);
        outState.putBoolean("thresholds_changed", thresholdsChanged);
        super.onSaveInstanceState(outState);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Update toggle state in case permission settings changed while activity was paused
        updateNotificationToggleState();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // Ensure thresholds are saved when activity is paused
        if (thresholdsChanged) {
            saveDustThreshold();
            saveNoiseThreshold();
            saveGasThreshold();
            thresholdsChanged = false;
            Log.d(Constants.TAG_MAIN, "Thresholds saved on activity pause");
        }
    }
    
    private void loadSavedThresholds() {
        // Load saved values or use defaults from Constants
        dustThreshold = preferences.getFloat(Constants.PREF_DUST_THRESHOLD, Constants.DUST_THRESHOLD);
        noiseThreshold = preferences.getFloat(Constants.PREF_NOISE_THRESHOLD, Constants.NOISE_THRESHOLD);
        gasThreshold = preferences.getFloat(Constants.PREF_GAS_THRESHOLD, Constants.GAS_THRESHOLD);
        
        // Update UI with current values immediately
        updateDustThresholdText(dustThreshold);
        updateNoiseThresholdText(noiseThreshold);
        updateGasThresholdText(gasThreshold);
    }
    
    private void updateNotificationToggleState() {
        // Get current notification state from the existing utility
        boolean notificationsEnabled = notificationUtils.areNotificationsEnabled();
        binding.switchNotifications.setChecked(notificationsEnabled);
        
        // Update the dust and noise switches to reflect the master switch state
        updateSensorNotificationSwitches(notificationsEnabled);
    }
    
    private void setupNotificationToggles() {
        // Setup main notifications toggle
        setupMainNotificationsToggle();
        
        // Setup dust notifications toggle
        setupDustNotificationsToggle();
        
        // Setup noise notifications toggle
        setupNoiseNotificationsToggle();

        // Setup gas notifications toggle
        setupGasNotificationsToggle();
        
        // Setup background operation toggle
        setupBackgroundOperationToggle();
        
        // Setup battery optimization request button
        setupBatteryOptimizationButton();
    }
    
    private void setupMainNotificationsToggle() {
        // Update toggle based on current permission/settings state
        updateNotificationToggleState();
        
        // Set listener
        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Check and request notification permission if needed
                permissionManager.requestNotificationPermission(granted -> {
                    if (granted) {
                        // Permission was granted, enable notifications
                        notificationUtils.setNotificationsEnabled(true);
                        updateSensorNotificationSwitches(true);
                    } else {
                        // Permission denied, show message and update UI
                        permissionManager.showPermissionSettingsSnackbar(
                            binding.getRoot(),
                            "Notification permission required for alerts"
                        );
                        // Reset the toggle since permission was denied
                        binding.switchNotifications.setChecked(false);
                        updateSensorNotificationSwitches(false);
                    }
                });
            } else {
                // Disable notifications
                notificationUtils.setNotificationsEnabled(false);
                updateSensorNotificationSwitches(false);
            }
            
            Log.d(Constants.TAG_MAIN, "Notifications " + (isChecked ? "enabled" : "disabled") + " in Settings");
        });
    }
    
    private void setupDustNotificationsToggle() {
        // Get current dust notification state
        boolean dustNotificationsEnabled = preferences.getBoolean(
                Constants.PREF_DUST_NOTIFICATIONS_ENABLED, true);
        binding.switchDustNotifications.setChecked(dustNotificationsEnabled);
        
        // Set initial enabled state based on master switch
        binding.switchDustNotifications.setEnabled(binding.switchNotifications.isChecked());
        
        // Set listener
        binding.switchDustNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Set dust notifications enabled/disabled
            notificationUtils.setNotificationsEnabledForType("dust", isChecked);
            Log.d(Constants.TAG_MAIN, "Dust notifications " + (isChecked ? "enabled" : "disabled") + " in Settings");
        });
    }
    
    private void setupNoiseNotificationsToggle() {
        // Get current noise notification state
        boolean noiseNotificationsEnabled = preferences.getBoolean(
                Constants.PREF_NOISE_NOTIFICATIONS_ENABLED, true);
        binding.switchNoiseNotifications.setChecked(noiseNotificationsEnabled);
        
        // Set initial enabled state based on master switch
        binding.switchNoiseNotifications.setEnabled(binding.switchNotifications.isChecked());
        
        // Set listener
        binding.switchNoiseNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Set noise notifications enabled/disabled
            notificationUtils.setNotificationsEnabledForType("noise", isChecked);
            Log.d(Constants.TAG_MAIN, "Noise notifications " + (isChecked ? "enabled" : "disabled") + " in Settings");
        });
    }

    private void setupGasNotificationsToggle() {
        // Get current gas notification state
        boolean gasNotificationsEnabled = preferences.getBoolean(
                Constants.PREF_GAS_NOTIFICATIONS_ENABLED, true);
        binding.switchGasNotifications.setChecked(gasNotificationsEnabled);
        
        // Set initial enabled state based on master switch
        binding.switchGasNotifications.setEnabled(binding.switchNotifications.isChecked());
        
        // Set listener
        binding.switchGasNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Set gas notifications enabled/disabled
            notificationUtils.setNotificationsEnabledForType("gas", isChecked);
            Log.d(Constants.TAG_MAIN, "Gas notifications " + (isChecked ? "enabled" : "disabled") + " in Settings");
        });
    }
    
    private void setupBackgroundOperationToggle() {
        // Get current background operation state
        boolean backgroundEnabled = preferences.getBoolean(
                Constants.PREF_BACKGROUND_OPERATION_ENABLED, true); // Default to enabled
        binding.switchBackgroundOperation.setChecked(backgroundEnabled);
        
        // Set listener
        binding.switchBackgroundOperation.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Save the setting
            preferences.edit()
                .putBoolean(Constants.PREF_BACKGROUND_OPERATION_ENABLED, isChecked)
                .apply();
            
            Log.d(Constants.TAG_MAIN, "Background operation " + (isChecked ? "enabled" : "disabled") + " in Settings");
        });
    }
    
    /**
     * Setup the battery optimization button to request exemption when needed
     */
    private void setupBatteryOptimizationButton() {
        // Check current battery optimization status
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        boolean isIgnoringBatteryOptimizations = false;
        
        if (powerManager != null) {
            isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(getPackageName());
        }
        
        // Update button state based on current status
        if (isIgnoringBatteryOptimizations) {
            binding.btnBatteryOptimization.setText(R.string.already_optimized);
            binding.btnBatteryOptimization.setEnabled(false);
        }
        
        // Set click listener
        binding.btnBatteryOptimization.setOnClickListener(v -> {
            // Request battery optimization exemption
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            
            try {
                startActivity(intent);
                Log.d(Constants.TAG_MAIN, "Requested battery optimization exemption");
            } catch (Exception e) {
                Log.e(Constants.TAG_MAIN, "Error requesting battery optimization exemption: " + e.getMessage());
                Toast.makeText(this, "Unable to open battery settings", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    /**
     * Updates the sensor-specific notification switches based on the master switch state
     * @param masterEnabled whether the master switch is enabled
     */
    private void updateSensorNotificationSwitches(boolean masterEnabled) {
        // Disable/enable the sensor-specific switches based on master switch
        binding.switchDustNotifications.setEnabled(masterEnabled);
        binding.switchNoiseNotifications.setEnabled(masterEnabled);
        binding.switchGasNotifications.setEnabled(masterEnabled);
    }
    
    private void setupThresholdSliders() {
        // Setup dust threshold slider
        setupDustThresholdSlider();
        
        // Setup noise threshold slider
        setupNoiseThresholdSlider();

        // Setup gas threshold slider
        setupGasThresholdSlider();
    }
    
    private void setupDustThresholdSlider() {
        // Set initial value based on current threshold
        binding.sliderDustThreshold.setValue(dustThreshold);
        
        // Update value text and risk level
        updateDustThresholdText(dustThreshold);
        
        // Set listener for continuous updates
        binding.sliderDustThreshold.addOnChangeListener((slider, value, fromUser) -> {
            // Update display
            updateDustThresholdText(value);
            
            // Save new threshold
            if (fromUser) {
                dustThreshold = value;
                thresholdsChanged = true;
                Log.d(Constants.TAG_MAIN, "Dust threshold updated to: " + dustThreshold);
            }
        });
        
        // Set listener for when user stops interacting
        binding.sliderDustThreshold.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
                // Not needed but required by interface
            }
            
            @Override
            public void onStopTrackingTouch(Slider slider) {
                // Save when user stops interacting with slider
                saveDustThreshold();
                Log.d(Constants.TAG_MAIN, "Dust threshold saved: " + dustThreshold);
            }
        });
    }
    
    private void setupNoiseThresholdSlider() {
        // Set initial value based on current threshold
        binding.sliderNoiseThreshold.setValue(noiseThreshold);
        
        // Update value text and risk level
        updateNoiseThresholdText(noiseThreshold);
        
        // Set listener for continuous updates
        binding.sliderNoiseThreshold.addOnChangeListener((slider, value, fromUser) -> {
            // Update display
            updateNoiseThresholdText(value);
            
            // Save new threshold
            if (fromUser) {
                noiseThreshold = value;
                thresholdsChanged = true;
                Log.d(Constants.TAG_MAIN, "Noise threshold updated to: " + noiseThreshold);
            }
        });
        
        // Set listener for when user stops interacting
        binding.sliderNoiseThreshold.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
                // Not needed but required by interface
            }
            
            @Override
            public void onStopTrackingTouch(Slider slider) {
                // Save when user stops interacting with slider
                saveNoiseThreshold();
                Log.d(Constants.TAG_MAIN, "Noise threshold saved: " + noiseThreshold);
            }
        });
    }

    private void setupGasThresholdSlider() {
        // Ensure gas threshold is a valid multiple of stepSize and within valid range
        float adjustedGasThreshold = Math.round(gasThreshold / 50.0f) * 50.0f;
        adjustedGasThreshold = Math.max(50.0f, Math.min(adjustedGasThreshold, 5000.0f));
        
        // set initial value
        binding.sliderGasThreshold.setValue(adjustedGasThreshold);
        
        // update text
        updateGasThresholdText(adjustedGasThreshold);
        
        // set change listener
        binding.sliderGasThreshold.addOnChangeListener((slider, value, fromUser) -> {
            // update display
            updateGasThresholdText(value);
            
            // save new value
            if (fromUser) {
                gasThreshold = value;
                thresholdsChanged = true;
                Log.d(Constants.TAG_MAIN, "Gas threshold updated to: " + gasThreshold);
            }
        });
        
        // set touch listener
        binding.sliderGasThreshold.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
                // required by interface
            }
            
            @Override
            public void onStopTrackingTouch(Slider slider) {
                // save on touch end
                saveGasThreshold();
                Log.d(Constants.TAG_MAIN, "Gas threshold saved: " + gasThreshold);
            }
        });
    }
    
    private void setupResetButton() {
        binding.btnResetThresholds.setOnClickListener(v -> {
            // Reset to default values from Constants
            dustThreshold = Constants.DUST_THRESHOLD;
            noiseThreshold = Constants.NOISE_THRESHOLD;
            gasThreshold = Constants.GAS_THRESHOLD;
            
            // Update UI
            binding.sliderDustThreshold.setValue(dustThreshold);
            binding.sliderNoiseThreshold.setValue(noiseThreshold);
            binding.sliderGasThreshold.setValue(gasThreshold);
            
            // Update display texts
            updateDustThresholdText(dustThreshold);
            updateNoiseThresholdText(noiseThreshold);
            updateGasThresholdText(gasThreshold);
            
            // Save default values
            saveDustThreshold();
            saveNoiseThreshold();
            saveGasThreshold();
            
            Log.d(Constants.TAG_MAIN, "Thresholds reset to defaults");
        });
    }
    
    private void updateDustThresholdText(float threshold) {
            binding.txtDustThresholdValue.setText(String.format("%.1f μg/m³", threshold));
            
            // Update risk level indicator
            TextView riskText = binding.txtDustThresholdRisk;
            if (threshold <= 12.0f) {
                riskText.setText("Good");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            } else if (threshold <= 35.4f) {
                riskText.setText("Moderate");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark, null));
            } else if (threshold <= 55.4f) {
                riskText.setText("Unhealthy for Sensitive");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_orange_light, null));
            } else if (threshold <= 150.4f) {
                riskText.setText("Unhealthy");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark, null));
            } else if (threshold <= 250.4f) {
                riskText.setText("Very Unhealthy");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_red_light, null));
            } else {
                riskText.setText("Hazardous");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
        }
    }
    
    private void updateNoiseThresholdText(float threshold) {
            binding.txtNoiseThresholdValue.setText(String.format("%.1f dB", threshold));
            
            // Update risk level indicator based on OSHA standards
            TextView riskText = binding.txtNoiseThresholdRisk;
            if (threshold < 85.0f) {
                riskText.setText("Safe");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            } else if (threshold == 85.0f) {
                riskText.setText("OSHA Standard");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_green_light, null));
            } else if (threshold <= 90.0f) {
                riskText.setText("8hr Limit");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark, null));
            } else if (threshold <= 95.0f) {
                riskText.setText("4hr Limit");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_orange_light, null));
            } else if (threshold <= 100.0f) {
                riskText.setText("2hr Limit");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark, null));
            } else if (threshold <= 110.0f) {
                riskText.setText("30min Limit");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_red_light, null));
            } else {
                riskText.setText("Immediate Risk");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
        }
    }

    private void updateGasThresholdText(float threshold) {
            binding.txtGasThresholdValue.setText(String.format("%.1f ppm", threshold));
            
        // update risk level
            TextView riskText = binding.txtGasThresholdRisk;
            if (threshold <= 50.0f) {
                riskText.setText("Good");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_green_dark, null));
            } else if (threshold <= 100.0f) {
                riskText.setText("Moderate");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_blue_dark, null));
        } else if (threshold <= 500.0f) {
                riskText.setText("Unhealthy for Sensitive");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_orange_light, null));
        } else if (threshold <= 1000.0f) {
                riskText.setText("Unhealthy");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark, null));
        } else if (threshold <= 2500.0f) {
                riskText.setText("Very Unhealthy");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_red_light, null));
            } else {
                riskText.setText("Hazardous");
            riskText.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
        }
    }
    
    private void saveDustThreshold() {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putFloat(Constants.PREF_DUST_THRESHOLD, dustThreshold);
        editor.commit(); // Use commit() instead of apply() to ensure synchronous update
                Log.d(Constants.TAG_MAIN, "Dust threshold set to: " + dustThreshold);
                thresholdsChanged = false;
    }
    
    private void saveNoiseThreshold() {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putFloat(Constants.PREF_NOISE_THRESHOLD, noiseThreshold);
        editor.commit(); // Use commit() instead of apply() to ensure synchronous update
                Log.d(Constants.TAG_MAIN, "Noise threshold set to: " + noiseThreshold);
                thresholdsChanged = false;
    }

    private void saveGasThreshold() {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putFloat(Constants.PREF_GAS_THRESHOLD, gasThreshold);
        editor.commit(); // Use commit() instead of apply() to ensure synchronous update
                Log.d(Constants.TAG_MAIN, "Gas threshold set to: " + gasThreshold);
                thresholdsChanged = false;
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
} 