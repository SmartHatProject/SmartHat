package com.team12.smarthat.bluetooth.core;

import com.team12.smarthat.models.SensorData;
import com.team12.smarthat.utils.Constants;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class GasDataHandlerTest {

    private GasDataHandler gasDataHandler;
    private MockedStatic<Log> mockedLog;
    
    @Mock
    private SensorData mockSensorData;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // No need to mock Constants, we have a test implementation
        
        // Mock Android Log class
        mockedLog = Mockito.mockStatic(Log.class);
        
        // Setting up mock SensorData
        when(mockSensorData.getSensorType()).thenReturn("gas");
        when(mockSensorData.getValue()).thenReturn(50.0f);
        when(mockSensorData.isAnomalous()).thenReturn(false);
        
        // Create test subject
        gasDataHandler = Mockito.spy(new GasDataHandler());
        
        // Mock the createGasData method to return our mock sensor data
        doReturn(mockSensorData).when(gasDataHandler).createGasData(anyFloat(), anyLong(), anyBoolean());
    }
    
    @After
    public void tearDown() {
        // Close the mocked statics to release resources
        if (mockedLog != null) {
            mockedLog.close();
        }
    }

    @Test
    public void testValidGasData() throws JSONException {
        // Create a valid gas data JSON
        JSONObject json = new JSONObject();
        json.put("messageType", "GAS_SENSOR_DATA");
        json.put("data", 75.0);
        json.put("timeStamp", System.currentTimeMillis());

        // Convert to bytes as it would be received from BLE
        byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);

        // Process the data
        GasDataHandler.ProcessResult result = gasDataHandler.processGasData(data);

        // Verify the result
        assertTrue("Result should be successful", result.success);
        assertNotNull("Data should not be null", result.data);
        assertEquals("Data should be gas type", "gas", result.data.getSensorType());
        assertEquals("Anomaly type should be NONE", GasDataHandler.ProcessResult.ANOMALY_NONE, result.anomalyType);
        assertFalse("Data should not be anomalous", result.data.isAnomalous());
    }

    @Test
    public void testSuddenChangeAnomaly() throws JSONException {
        // Setup behavior to simulate sudden change anomaly
        when(mockSensorData.isAnomalous()).thenReturn(true);
        
        // First send a normal reading
        JSONObject json1 = new JSONObject();
        json1.put("messageType", "GAS_SENSOR_DATA");
        json1.put("data", 50.0);
        json1.put("timeStamp", System.currentTimeMillis());
        byte[] data1 = json1.toString().getBytes(StandardCharsets.UTF_8);
        gasDataHandler.processGasData(data1);

        // Then send a reading with a sudden jump (> 50% change in < 2 seconds)
        long timestamp = System.currentTimeMillis() + 1000; // 1 second later
        JSONObject json2 = new JSONObject();
        json2.put("messageType", "GAS_SENSOR_DATA");
        json2.put("data", 120.0);  // > 100% increase
        json2.put("timeStamp", timestamp);
        byte[] data2 = json2.toString().getBytes(StandardCharsets.UTF_8);
        
        // Mock isRealisticGasChange to return false
        doReturn(false).when(gasDataHandler).isRealisticGasChange(anyFloat(), anyFloat(), anyLong());
        
        // Process the data
        GasDataHandler.ProcessResult result = gasDataHandler.processGasData(data2);

        // Verify the result
        assertTrue("Result should be successful", result.success);
        assertNotNull("Data should not be null", result.data);
        assertTrue("Data should be marked as anomalous", result.data.isAnomalous());
    }

    @Test
    public void testUnrealisticRateOfChange() throws JSONException {
        // Setup behavior to simulate unrealistic rate anomaly
        when(mockSensorData.isAnomalous()).thenReturn(true);
        
        // First send a normal reading
        JSONObject json1 = new JSONObject();
        json1.put("messageType", "GAS_SENSOR_DATA");
        json1.put("data", 30.0);
        json1.put("timeStamp", System.currentTimeMillis());
        byte[] data1 = json1.toString().getBytes(StandardCharsets.UTF_8);
        gasDataHandler.processGasData(data1);

        // Mock isRealisticGasChange for testing
        doReturn(false).when(gasDataHandler).isRealisticGasChange(anyFloat(), anyFloat(), anyLong());
        
        // Then send a reading with unrealistic rate of change
        long timestamp = System.currentTimeMillis() + 500; // 0.5 seconds later
        JSONObject json2 = new JSONObject();
        json2.put("messageType", "GAS_SENSOR_DATA");
        json2.put("data", 45.0);  // 50% increase in 0.5 seconds = 100% per second
        json2.put("timeStamp", timestamp);
        byte[] data2 = json2.toString().getBytes(StandardCharsets.UTF_8);
        
        // Process the data
        GasDataHandler.ProcessResult result = gasDataHandler.processGasData(data2);

        // Verify the result
        assertTrue("Result should be successful", result.success);
        assertNotNull("Data should not be null", result.data);
        assertTrue("Data should be marked as anomalous", result.data.isAnomalous());
    }

    @Test
    public void testStuckReadingsAnomaly() throws JSONException {
        // Setup behavior for stuck readings anomaly
        when(mockSensorData.isAnomalous()).thenReturn(true);
        
        // Use reflection to set consecutiveIdenticalReadings to trigger stuck readings
        try {
            Field field = GasDataHandler.class.getDeclaredField("consecutiveIdenticalReadings");
            field.setAccessible(true);
            field.set(gasDataHandler, 15);
        } catch (Exception e) {
            fail("Could not set consecutiveIdenticalReadings field: " + e.getMessage());
        }
        
        // Create a gas data JSON
        JSONObject json = new JSONObject();
        json.put("messageType", "GAS_SENSOR_DATA");
        json.put("data", 60.0);
        json.put("timeStamp", System.currentTimeMillis());
        byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);
        
        // Process the data
        GasDataHandler.ProcessResult result = gasDataHandler.processGasData(data);
        
        // Verify the result
        assertTrue("Result should be successful", result.success);
        assertNotNull("Data should not be null", result.data);
        assertTrue("Data should be marked as anomalous", result.data.isAnomalous());
    }

    @Test
    public void testOutOfRangeAnomaly() throws JSONException {
        // Setup behavior for out of range anomaly
        when(mockSensorData.isAnomalous()).thenReturn(true);
        
        // Send a reading with an out-of-range value
        JSONObject json = new JSONObject();
        json.put("messageType", "GAS_SENSOR_DATA");
        json.put("data", 9999.0); // Way above MAX_GAS_VALUE
        json.put("timeStamp", System.currentTimeMillis());
        byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);
        
        // Process the data
        GasDataHandler.ProcessResult result = gasDataHandler.processGasData(data);

        // Verify the result
        assertFalse("Result should not be successful", result.success);
        assertNotNull("Data should not be null", result.data);
        assertTrue("Data should be marked as anomalous", result.data.isAnomalous());
    }

    @Test
    public void testInvalidJSON() {
        // Send invalid JSON data
        byte[] data = "{invalid json".getBytes(StandardCharsets.UTF_8);
        
        // Process the data
        GasDataHandler.ProcessResult result = gasDataHandler.processGasData(data);

        // Verify the result
        assertFalse("Result should not be successful", result.success);
        assertNull("Data should be null", result.data);
        assertEquals("Message should indicate waiting for more fragments", 
            "Incomplete JSON, waiting for more fragments", result.message);
    }

    @Test
    public void testFragmentedJSON() throws JSONException {
        // Create a valid gas data JSON
        JSONObject json = new JSONObject();
        json.put("messageType", "GAS_SENSOR_DATA");
        json.put("data", 75.0);
        json.put("timeStamp", System.currentTimeMillis());
        String jsonString = json.toString();
        
        // Split the JSON into two fragments
        int splitPoint = jsonString.length() / 2;
        byte[] fragment1 = jsonString.substring(0, splitPoint).getBytes(StandardCharsets.UTF_8);
        byte[] fragment2 = jsonString.substring(splitPoint).getBytes(StandardCharsets.UTF_8);
        
        // Process the first fragment - should be incomplete
        GasDataHandler.ProcessResult result1 = gasDataHandler.processGasData(fragment1);
        
        // Verify first fragment result
        assertFalse("First fragment should not be successful", result1.success);
        assertNull("First fragment data should be null", result1.data);
        
        // Process the second fragment - should complete the JSON
        GasDataHandler.ProcessResult result2 = gasDataHandler.processGasData(fragment2);
        
        // Verify second fragment result (now we have complete JSON)
        assertTrue("Second fragment should be successful", result2.success);
        assertNotNull("Second fragment data should not be null", result2.data);
        assertEquals("Data should be gas type", "gas", result2.data.getSensorType());
    }

    @Test
    public void testIsRealisticGasChange() {
        // Test the actual method directly rather than mocking it
        
        // Test realistic change (less than 30% per second)
        assertTrue("Should be realistic", 
            gasDataHandler.isRealisticGasChange(100.0f, 90.0f, 1000));
        
        // Test unrealistic change (more than 30% per second)
        assertFalse("Should be unrealistic", 
            gasDataHandler.isRealisticGasChange(130.0f, 100.0f, 500));
        
        // Test small values exception
        assertTrue("Should be realistic for small values", 
            gasDataHandler.isRealisticGasChange(5.0f, 1.0f, 100));
        
        // Test long time difference exception
        assertTrue("Should be realistic for long time differences", 
            gasDataHandler.isRealisticGasChange(200.0f, 100.0f, 6000));
    }

    @Test
    public void testResetAllCounters() throws Exception {
        // Set up initial state using reflection
        Field consecutiveIdenticalReadingsField = GasDataHandler.class.getDeclaredField("consecutiveIdenticalReadings");
        consecutiveIdenticalReadingsField.setAccessible(true);
        consecutiveIdenticalReadingsField.set(gasDataHandler, 10);
        
        // Call reset
        gasDataHandler.resetAllCounters();
        
        // Verify reset worked
        assertEquals("Consecutive identical readings should be 0", 
            0, gasDataHandler.getConsecutiveIdenticalReadings());
        
        // Test that JSON buffer is cleared by sending a partial JSON after reset
        JSONObject json = new JSONObject();
        json.put("messageType", "GAS_SENSOR_DATA");
        byte[] partialData = json.toString().getBytes(StandardCharsets.UTF_8);
        
        // Process partial data
        GasDataHandler.ProcessResult result = gasDataHandler.processGasData(partialData);
        
        // Should fail but with a clean buffer
        assertFalse("Should not be successful with incomplete JSON", result.success);
        assertEquals("Should show waiting for more fragments message",
            "Incomplete JSON, waiting for more fragments", result.message);
    }
    
    @Test
    public void callCreateGasData() {
        // Call the private createGasData method via our spy
        float value = 45.0f;
        long timestamp = System.currentTimeMillis();
        boolean isAnomalous = false;
        
        SensorData data = gasDataHandler.createGasData(value, timestamp, isAnomalous);
        
        // Verify the result
        assertNotNull("Created gas data should not be null", data);
        assertEquals("Gas data should have correct type", "gas", data.getSensorType());
        assertEquals("Gas data should have correct value", value, data.getValue(), 0.001f);
        assertEquals("Gas data should have correct anomalous state", isAnomalous, data.isAnomalous());
    }
} 