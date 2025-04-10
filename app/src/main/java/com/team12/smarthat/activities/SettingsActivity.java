package com.team12.smarthat.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.google.android.material.slider.Slider;
import com.team12.smarthat.R;
import com.team12.smarthat.databinding.ActivitySettingsBinding;
import com.team12.smarthat.utils.Constants;
import com.team12.smarthat.utils.NotificationUtils;
import com.team12.smarthat.utils.PermissionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    
    // Cache color resources to avoid repeated lookups
    private int colorGreen, colorGreenLight, colorBlue, colorOrangeLight, colorOrangeDark, colorRedLight, colorRedDark;
    
    // Thread pool for background operations
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            // Enable strict mode for development builds to detect UI thread issues
            if (Constants.DEV_MODE) {
                enableStrictMode();
            }
            
            // Just set up basic content view in onCreate - defer everything else
            binding = ActivitySettingsBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
            
            // Initialize toolbar - this is lightweight
            setSupportActionBar(binding.settingsToolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowHomeEnabled(true);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
            
            // Initialize notification utils early to avoid NPE in onResume
            notificationUtils = new NotificationUtils(this);
            
            // Initialize preferences early to avoid NPE in toggle setup
            preferences = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
            
            // Initialize permission manager
            permissionManager = new PermissionManager(this);
            
            // Load threshold values synchronously to prevent flash of default values
            if (savedInstanceState != null) {
                dustThreshold = savedInstanceState.getFloat("dust_threshold", Constants.DUST_THRESHOLD);
                noiseThreshold = savedInstanceState.getFloat("noise_threshold", Constants.NOISE_THRESHOLD);
                gasThreshold = savedInstanceState.getFloat("gas_threshold", Constants.GAS_THRESHOLD);
                thresholdsChanged = savedInstanceState.getBoolean("thresholds_changed", false);
            } else {
                // Load from SharedPreferences
                dustThreshold = preferences.getFloat(Constants.PREF_DUST_THRESHOLD, Constants.DUST_THRESHOLD);
                noiseThreshold = preferences.getFloat(Constants.PREF_NOISE_THRESHOLD, Constants.NOISE_THRESHOLD);
                gasThreshold = preferences.getFloat(Constants.PREF_GAS_THRESHOLD, Constants.GAS_THRESHOLD);
            }
            
            // Defer all heavy initialization to a post-UI render handler with longer delay
            new Handler().postDelayed(() -> initializeSettingsActivity(savedInstanceState), 300);
        } catch (Exception e) {
            Log.e(Constants.TAG_MAIN, "Error in onCreate: " + e.getMessage());
        }
    }
    
    /**
     * Enable StrictMode for development builds
     */
    private void enableStrictMode() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            android.os.StrictMode.setThreadPolicy(new android.os.StrictMode.ThreadPolicy.Builder()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build());
        }
    }
    
    /**
     * Initialize the activity components after the UI has been rendered
     * This prevents ANRs by ensuring UI is shown before heavy work starts
     */
    private void initializeSettingsActivity(Bundle savedInstanceState) {
        try {
            // Cache color resources
            cacheColorResources();
            
            // Initialize notification utils (reusing existing class)
            // Already initialized in onCreate
            
            // Set up UI components with already loaded values
            setupNotificationToggles();
            setupThresholdSliders();
            setupResetButton();
            
            // Update threshold text displays
            updateDustThresholdText(dustThreshold);
            updateNoiseThresholdText(noiseThreshold);
            updateGasThresholdText(gasThreshold);
            
            // Show threshold section with correct values
            binding.thresholdSectionHeader.setVisibility(View.VISIBLE);
            binding.thresholdCard.setVisibility(View.VISIBLE);
            
        } catch (Exception e) {
            Log.e(Constants.TAG_MAIN, "Error in initializeSettingsActivity: " + e.getMessage());
        }
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
        
        // Update all notification toggle states when activity resumes
        // in case permissions or system notification settings changed
        updateNotificationToggleState();
        
        // Ensure that child toggles enabled state matches main toggle state
        boolean notificationsEnabled = binding.switchNotifications.isChecked();
        updateChildTogglesState(notificationsEnabled);
        
        // Ensure service notification states are consistent with UI state
        if (notificationsEnabled) {
            // Only enable individual notifications if main toggle is on
            notificationUtils.setNotificationsEnabled(true);
            notificationUtils.setNotificationsEnabledForType("dust", binding.switchDustNotifications.isChecked());
            notificationUtils.setNotificationsEnabledForType("noise", binding.switchNoiseNotifications.isChecked());
            notificationUtils.setNotificationsEnabledForType("gas", binding.switchGasNotifications.isChecked());
        } else {
            // If main toggle is off, ensure all notifications are disabled at service level
            notificationUtils.setNotificationsEnabled(false);
            notificationUtils.setNotificationsEnabledForType("dust", false);
            notificationUtils.setNotificationsEnabledForType("noise", false);
            notificationUtils.setNotificationsEnabledForType("gas", false);
        }
        
        // Log current state for debugging
        Log.d(Constants.TAG_MAIN, "SettingsActivity resumed, notification state refreshed");
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // Save any threshold changes when leaving activity
        if (thresholdsChanged) {
            // Save all thresholds in a single batch operation
            saveAllThresholds();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // Clean up resources to prevent memory leaks
        try {
            // Shutdown executor service
            backgroundExecutor.shutdown();
            
            // Clear references
            binding = null;
            notificationUtils = null;
            permissionManager = null;
        } catch (Exception e) {
            Log.e(Constants.TAG_MAIN, "Error cleaning up resources: " + e.getMessage());
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
        
        // Set checked state without triggering listener
        binding.switchNotifications.setOnCheckedChangeListener(null);
        binding.switchNotifications.setChecked(notificationsEnabled);
        
        // Restore listener
        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Check and request notification permission if needed
                permissionManager.requestNotificationPermission(granted -> {
                    if (granted) {
                        // Permission was granted, enable notifications
                        onMainNotificationToggleChanged(true);
                    } else {
                        // Permission denied, show message and update UI
                        permissionManager.showPermissionSettingsSnackbar(
                            binding.getRoot(),
                            "Notification permission required for alerts"
                        );
                        // Reset the toggle since permission was denied
                        binding.switchNotifications.setOnCheckedChangeListener(null);
                        binding.switchNotifications.setChecked(false);
                        binding.switchNotifications.setOnCheckedChangeListener((btn, chk) -> 
                            this.onMainNotificationToggleChanged(chk));
                        
                        // Make sure notification is actually disabled at service level
                        onMainNotificationToggleChanged(false);
                    }
                });
            } else {
                onMainNotificationToggleChanged(false);
            }
        });
        
        // Update child toggle states using the unified method
        updateChildTogglesState(notificationsEnabled);
    }
    
    private void setupNotificationToggles() {
        // Setup main notifications toggle first
        setupMainNotificationsToggle();
        
        // Then setup child switches after main toggle is configured
        setupChildSwitchListeners();
        setupChildSwitches();
        
        // Make sure child toggles reflect main toggle state
        boolean notificationsEnabled = binding.switchNotifications.isChecked();
        updateChildTogglesState(notificationsEnabled);
    }
    
    private void setupMainNotificationsToggle() {
        // Get current state
        boolean notificationsEnabled = notificationUtils.areNotificationsEnabled();
        
        // Set initial checked state without triggering listener
        binding.switchNotifications.setOnCheckedChangeListener(null);
        binding.switchNotifications.setChecked(notificationsEnabled);
        
        // Set listener
        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Check and request notification permission if needed
                permissionManager.requestNotificationPermission(granted -> {
                    if (granted) {
                        // Permission was granted, enable notifications
                        onMainNotificationToggleChanged(true);
                    } else {
                        // Permission denied, show message and update UI
                        permissionManager.showPermissionSettingsSnackbar(
                            binding.getRoot(),
                            "Notification permission required for alerts"
                        );
                        // Reset the toggle since permission was denied
                        binding.switchNotifications.setOnCheckedChangeListener(null);
                        binding.switchNotifications.setChecked(false);
                        binding.switchNotifications.setOnCheckedChangeListener((btn, chk) -> 
                            this.onMainNotificationToggleChanged(chk));
                        
                        // Make sure notification is actually disabled at service level
                        onMainNotificationToggleChanged(false);
                    }
                });
            } else {
                onMainNotificationToggleChanged(false);
            }
        });
    }
    
    private void setupChildSwitchListeners() {
        // Set up dust switch listener
        binding.switchDustNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Only save preference and update notification if main toggle is on
            if (binding.switchNotifications.isChecked()) {
                preferences.edit().putBoolean(Constants.PREF_DUST_NOTIFICATIONS_ENABLED, isChecked).apply();
                notificationUtils.setNotificationsEnabledForType("dust", isChecked);
                Log.d(Constants.TAG_MAIN, "Dust notifications preference set to: " + isChecked);
            }
        });
        
        // Set up noise switch listener
        binding.switchNoiseNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Only save preference and update notification if main toggle is on
            if (binding.switchNotifications.isChecked()) {
                preferences.edit().putBoolean(Constants.PREF_NOISE_NOTIFICATIONS_ENABLED, isChecked).apply();
                notificationUtils.setNotificationsEnabledForType("noise", isChecked);
                Log.d(Constants.TAG_MAIN, "Noise notifications preference set to: " + isChecked);
            }
        });
        
        // Set up gas switch listener
        binding.switchGasNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Only save preference and update notification if main toggle is on
            if (binding.switchNotifications.isChecked()) {
                preferences.edit().putBoolean(Constants.PREF_GAS_NOTIFICATIONS_ENABLED, isChecked).apply();
                notificationUtils.setNotificationsEnabledForType("gas", isChecked);
                Log.d(Constants.TAG_MAIN, "Gas notifications preference set to: " + isChecked);
            }
        });
    }
    
    private void setupChildSwitches() {
        // Get current states from preferences
        boolean dustEnabled = preferences.getBoolean(Constants.PREF_DUST_NOTIFICATIONS_ENABLED, true);
        boolean noiseEnabled = preferences.getBoolean(Constants.PREF_NOISE_NOTIFICATIONS_ENABLED, true);
        boolean gasEnabled = preferences.getBoolean(Constants.PREF_GAS_NOTIFICATIONS_ENABLED, true);
        
        // Set initial checked states without triggering listeners
        binding.switchDustNotifications.setOnCheckedChangeListener(null);
        binding.switchNoiseNotifications.setOnCheckedChangeListener(null);
        binding.switchGasNotifications.setOnCheckedChangeListener(null);
        
        binding.switchDustNotifications.setChecked(dustEnabled);
        binding.switchNoiseNotifications.setChecked(noiseEnabled);
        binding.switchGasNotifications.setChecked(gasEnabled);
        
        // Restore listeners
        setupChildSwitchListeners();
    }
    
    /**
     * Extracted method to handle main toggle state changes
     */
    private void onMainNotificationToggleChanged(boolean isEnabled) {
        // First update preference
        preferences.edit().putBoolean(Constants.PREF_NOTIFICATIONS_ENABLED, isEnabled).apply();
        
        // Then update service level notifications state
        notificationUtils.setNotificationsEnabled(isEnabled);
        
        // Then update UI and child notification states
        updateChildTogglesState(isEnabled);
        
        if (isEnabled) {
            Log.d(Constants.TAG_MAIN, "Main notifications enabled, child states restored");
        } else {
            Log.d(Constants.TAG_MAIN, "Main notifications disabled, all child notifications disabled");
        }
    }
    
    /**
     * Updates both enabled state and checked state of child toggles
     */
    private void updateChildTogglesState(boolean mainToggleEnabled) {
        if (mainToggleEnabled) {
            // Enable child toggles UI
            binding.switchDustNotifications.setEnabled(true);
            binding.switchNoiseNotifications.setEnabled(true);
            binding.switchGasNotifications.setEnabled(true);
            
            // Restore saved preferences for child toggles
            boolean dustEnabled = preferences.getBoolean(Constants.PREF_DUST_NOTIFICATIONS_ENABLED, true);
            boolean noiseEnabled = preferences.getBoolean(Constants.PREF_NOISE_NOTIFICATIONS_ENABLED, true);
            boolean gasEnabled = preferences.getBoolean(Constants.PREF_GAS_NOTIFICATIONS_ENABLED, true);
            
            // Update UI to reflect saved preferences - temporarily remove listeners to prevent loops
            binding.switchDustNotifications.setOnCheckedChangeListener(null);
            binding.switchNoiseNotifications.setOnCheckedChangeListener(null);
            binding.switchGasNotifications.setOnCheckedChangeListener(null);
            
            binding.switchDustNotifications.setChecked(dustEnabled);
            binding.switchNoiseNotifications.setChecked(noiseEnabled);
            binding.switchGasNotifications.setChecked(gasEnabled);
            
            // Restore listeners
            setupChildSwitchListeners();
            
            // Update service level settings
            notificationUtils.setNotificationsEnabledForType("dust", dustEnabled);
            notificationUtils.setNotificationsEnabledForType("noise", noiseEnabled);
            notificationUtils.setNotificationsEnabledForType("gas", gasEnabled);
        } else {
            // Temporarily remove listeners to prevent loops
            binding.switchDustNotifications.setOnCheckedChangeListener(null);
            binding.switchNoiseNotifications.setOnCheckedChangeListener(null);
            binding.switchGasNotifications.setOnCheckedChangeListener(null);
            
            // Disable child toggles UI
            binding.switchDustNotifications.setEnabled(false);
            binding.switchNoiseNotifications.setEnabled(false);
            binding.switchGasNotifications.setEnabled(false);
            
            // Set all child toggles to unchecked
            binding.switchDustNotifications.setChecked(false);
            binding.switchNoiseNotifications.setChecked(false);
            binding.switchGasNotifications.setChecked(false);
            
            // Restore listeners
            setupChildSwitchListeners();
            
            // Turn off all notifications at service level
            notificationUtils.setNotificationsEnabledForType("dust", false);
            notificationUtils.setNotificationsEnabledForType("noise", false);
            notificationUtils.setNotificationsEnabledForType("gas", false);
        }
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
                saveAllThresholds();
                Log.d(Constants.TAG_MAIN, "Dust threshold saved via batch operation");
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
                saveAllThresholds();
                Log.d(Constants.TAG_MAIN, "Noise threshold saved via batch operation");
            }
        });
    }

    private void setupGasThresholdSlider() {
        // Set initial value based on current threshold
        binding.sliderGasThreshold.setValue(gasThreshold);
        
        // Update value text and risk level
        updateGasThresholdText(gasThreshold);
        
        // Set listener for continuous updates
        binding.sliderGasThreshold.addOnChangeListener((slider, value, fromUser) -> {
            // Update display
            updateGasThresholdText(value);
            
            // Save new threshold
            if (fromUser) {
                gasThreshold = value;
                thresholdsChanged = true;
                Log.d(Constants.TAG_MAIN, "Gas threshold updated to: " + gasThreshold);
            }
        });
        
        // Set listener for when user stops interacting
        binding.sliderGasThreshold.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(Slider slider) {
                // Not needed but required by interface
            }
            
            @Override
            public void onStopTrackingTouch(Slider slider) {
                // Save when user stops interacting with slider
                saveAllThresholds();
                Log.d(Constants.TAG_MAIN, "Gas threshold saved via batch operation");
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
            
            // Save all threshold values in one batch operation
            saveAllThresholds();
            
            Log.d(Constants.TAG_MAIN, "Thresholds reset to defaults");
        });
    }
    
    private void cacheColorResources() {
        try {
            colorGreen = getResources().getColor(android.R.color.holo_green_dark, null);
            colorGreenLight = getResources().getColor(android.R.color.holo_green_light, null);
            colorBlue = getResources().getColor(android.R.color.holo_blue_dark, null);
            colorOrangeLight = getResources().getColor(android.R.color.holo_orange_light, null);
            colorOrangeDark = getResources().getColor(android.R.color.holo_orange_dark, null);
            colorRedLight = getResources().getColor(android.R.color.holo_red_light, null);
            colorRedDark = getResources().getColor(android.R.color.holo_red_dark, null);
        } catch (Exception e) {
            Log.e(Constants.TAG_MAIN, "Error caching color resources: " + e.getMessage());
        }
    }
    
    private void updateDustThresholdText(float threshold) {
        try {
            binding.txtDustThresholdValue.setText(String.format("%.1f μg/m³", threshold));
            
            // Update risk level indicator
            TextView riskText = binding.txtDustThresholdRisk;
            if (threshold <= 12.0f) {
                riskText.setText("Good");
                riskText.setTextColor(colorGreen);
            } else if (threshold <= 35.4f) {
                riskText.setText("Moderate");
                riskText.setTextColor(colorBlue);
            } else if (threshold <= 55.4f) {
                riskText.setText("Unhealthy for Sensitive");
                riskText.setTextColor(colorOrangeLight);
            } else if (threshold <= 150.4f) {
                riskText.setText("Unhealthy");
                riskText.setTextColor(colorOrangeDark);
            } else if (threshold <= 250.4f) {
                riskText.setText("Very Unhealthy");
                riskText.setTextColor(colorRedLight);
            } else {
                riskText.setText("Hazardous");
                riskText.setTextColor(colorRedDark);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG_MAIN, "Error updating dust threshold text: " + e.getMessage());
        }
    }
    
    private void updateNoiseThresholdText(float threshold) {
        try {
            binding.txtNoiseThresholdValue.setText(String.format("%.1f dB", threshold));
            
            // Update risk level indicator based on OSHA standards
            TextView riskText = binding.txtNoiseThresholdRisk;
            if (threshold < 85.0f) {
                riskText.setText("Safe");
                riskText.setTextColor(colorGreen);
            } else if (threshold == 85.0f) {
                riskText.setText("OSHA Standard");
                riskText.setTextColor(colorGreenLight);
            } else if (threshold <= 90.0f) {
                riskText.setText("8hr Limit");
                riskText.setTextColor(colorBlue);
            } else if (threshold <= 95.0f) {
                riskText.setText("4hr Limit");
                riskText.setTextColor(colorOrangeLight);
            } else if (threshold <= 100.0f) {
                riskText.setText("2hr Limit");
                riskText.setTextColor(colorOrangeDark);
            } else if (threshold <= 110.0f) {
                riskText.setText("30min Limit");
                riskText.setTextColor(colorRedLight);
            } else {
                riskText.setText("Immediate Risk");
                riskText.setTextColor(colorRedDark);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG_MAIN, "Error updating noise threshold text: " + e.getMessage());
        }
    }

    private void updateGasThresholdText(float threshold) {
        try {
            binding.txtGasThresholdValue.setText(String.format("%.1f ppm", threshold));
            
            // Update risk level indicator based on the air quality chart
            TextView riskText = binding.txtGasThresholdRisk;
            if (threshold <= 350.0f) {
                riskText.setText("Healthy outside air level");
                riskText.setTextColor(colorBlue);
            } else if (threshold <= 600.0f) {
                riskText.setText("Healthy indoor climate");
                riskText.setTextColor(colorGreen);
            } else if (threshold <= 800.0f) {
                riskText.setText("Acceptable level");
                riskText.setTextColor(colorGreenLight);
            } else if (threshold <= 1000.0f) {
                riskText.setText("Ventilation required");
                riskText.setTextColor(colorOrangeLight);
            } else if (threshold <= 1200.0f) {
                riskText.setText("Ventilation necessary");
                riskText.setTextColor(colorOrangeDark);
            } else if (threshold <= 2000.0f) {
                riskText.setText("Negative health effects");
                riskText.setTextColor(colorRedLight);
            } else {
                riskText.setText("Hazardous prolonged exposure");
                riskText.setTextColor(colorRedDark);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG_MAIN, "Error updating gas threshold text: " + e.getMessage());
        }
    }
    
    /**
     * Save all thresholds in a single batch operation
     * More efficient than calling individual save methods
     */
    private void saveAllThresholds() {
        runInBackground(() -> {
            try {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putFloat(Constants.PREF_DUST_THRESHOLD, dustThreshold);
                editor.putFloat(Constants.PREF_NOISE_THRESHOLD, noiseThreshold);
                editor.putFloat(Constants.PREF_GAS_THRESHOLD, gasThreshold);
                editor.apply();
                Log.d(Constants.TAG_MAIN, "All thresholds saved in batch operation");
                thresholdsChanged = false;
            } catch (Exception e) {
                Log.e(Constants.TAG_MAIN, "Error saving thresholds: " + e.getMessage());
            }
        });
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // Helper method to run tasks on background thread
    private void runInBackground(Runnable task) {
        try {
            if (!backgroundExecutor.isShutdown()) {
                backgroundExecutor.execute(task);
            }
        } catch (Exception e) {
            Log.e(Constants.TAG_MAIN, "Error running background task: " + e.getMessage());
        }
    }
} 