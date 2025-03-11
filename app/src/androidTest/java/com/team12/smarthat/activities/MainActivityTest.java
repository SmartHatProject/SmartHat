package com.team12.smarthat.activities;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Button;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.IdlingRegistry;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.idling.CountingIdlingResource;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;

import com.team12.smarthat.R;
import com.team12.smarthat.bluetooth.core.BleConnectionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

/**
 * Instrumented tests for {@link MainActivity}
 * 
 * These tests verify the Bluetooth functionality on a real device,
 * focusing on the UI interactions and connection state handling.
 * 
 * Note: These tests require Bluetooth permissions to be granted and Bluetooth to be enabled.
 */
@RunWith(AndroidJUnit4.class)
public class MainActivityTest {
    
    // Automatically grant permissions needed for the test
    private static final String[] NEEDED_PERMISSIONS = getNeededPermissions();
    
    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule.grant(NEEDED_PERMISSIONS);
    
    private CountingIdlingResource idlingResource;
    
    @Before
    public void setUp() {
        // Create an idling resource to wait for async operations
        idlingResource = new CountingIdlingResource("BluetoothOperations");
        IdlingRegistry.getInstance().register(idlingResource);
        
        // Ensure Bluetooth is enabled
        ensureBluetoothEnabled();
    }
    
    @After
    public void tearDown() {
        // Clean up the idling resource
        IdlingRegistry.getInstance().unregister(idlingResource);
    }
    
    /**
     * Tests that the MainActivity displays properly and shows the connect button
     */
    @Test
    public void testMainActivityDisplaysConnectButton() {
        // Launch the activity
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Check that the connect button is displayed
            Espresso.onView(withId(R.id.btn_connect))
                    .check(ViewAssertions.matches(isDisplayed()));
        }
    }
    
    /**
     * Tests the initial state of the UI components
     */
    @Test
    public void testInitialUiState() {
        // Launch the activity
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Check that the status text shows disconnected
            Espresso.onView(withId(R.id.tv_status))
                    .check(ViewAssertions.matches(withText(containsString("Disconnected"))));
            
            // Check that the dust and noise readings display initial values
            Espresso.onView(withId(R.id.tv_dust))
                    .check(ViewAssertions.matches(withText(containsString("Dust"))));
            
            Espresso.onView(withId(R.id.tv_noise))
                    .check(ViewAssertions.matches(withText(containsString("Noise"))));
        }
    }
    
    /**
     * Tests that the connect button changes state when clicked
     * (This test may need to be disabled if no ESP32 device is available)
     */
    @Test
    public void testConnectButtonChangesStateWhenClicked() {
        // Launch the activity
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Get the initial button text
            final String[] initialButtonText = new String[1];
            scenario.onActivity(activity -> {
                Button connectButton = activity.findViewById(R.id.btn_connect);
                initialButtonText[0] = connectButton.getText().toString();
            });
            
            // Click the connect button
            Espresso.onView(withId(R.id.btn_connect))
                    .perform(ViewActions.click());
            
            // Wait for a moment to allow the state to change
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // Check that the button text has changed
            scenario.onActivity(activity -> {
                Button connectButton = activity.findViewById(R.id.btn_connect);
                String newButtonText = connectButton.getText().toString();
                
                // The button text should have changed (from "Connect" to something else)
                // We don't assert the exact text since it may depend on the connection state
                assert !initialButtonText[0].equals(newButtonText) : 
                       "Button text did not change after click";
            });
        }
    }
    
    /**
     * Helper method to ensure Bluetooth is enabled
     */
    private void ensureBluetoothEnabled() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BluetoothManager bluetoothManager = 
                (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
            
            // Wait for Bluetooth to enable
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Helper method to get the permissions needed for the test
     */
    private static String[] getNeededPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            return new String[] {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        } else {
            // Pre-Android 12
            return new String[] {
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
    }
} 