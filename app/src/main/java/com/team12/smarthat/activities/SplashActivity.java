package com.team12.smarthat.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.team12.smarthat.R;

/**
 * Splash Activity to show app branding and transition to the main app.
 * Optimized for Android 12 on Pixel 4a using the SplashScreen API.
 */
@SuppressLint("CustomSplashScreen") // We explicitly want our own implementation
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DURATION = 2000; // 2 seconds to properly display welcome screen

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Handle the SplashScreen API for Android 12+
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        
        super.onCreate(savedInstanceState);
        
        // Hide the action bar
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        
        setContentView(R.layout.activity_splash);

        // Get views
        TextView appNameText = findViewById(R.id.splash_app_name);
        TextView versionText = findViewById(R.id.version_text);
        ProgressBar loadingIndicator = findViewById(R.id.loading_indicator);
        
        // Set text
        appNameText.setText(R.string.app_name);
        
        // Make system UI immersive for a more elegant appearance
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        // Apply animations
        Animation textAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        appNameText.startAnimation(textAnimation);
        
        // Animate welcome text
        if (versionText != null) {
            Animation versionAnim = AnimationUtils.loadAnimation(this, R.anim.fade_in);
            versionAnim.setStartOffset(300); // Slight delay
            versionText.startAnimation(versionAnim);
        }
        
        // Animate loading indicator if it exists
        if (loadingIndicator != null) {
            Animation loadingAnim = AnimationUtils.loadAnimation(this, R.anim.fade_in);
            loadingAnim.setStartOffset(500); // Further delay
            loadingIndicator.startAnimation(loadingAnim);
        }

        // Delay transition to MainActivity
        new Handler(Looper.getMainLooper()).postDelayed(this::navigateToMainActivity, SPLASH_DURATION);
    }

    private void navigateToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        
        // Apply a fade transition
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        
        // Close this activity
        finish();
    }
} 