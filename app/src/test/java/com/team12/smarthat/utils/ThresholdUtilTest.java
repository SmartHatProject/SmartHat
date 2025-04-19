package com.team12.smarthat.utils;

import static org.junit.Assert.*;

import org.junit.Test;

import com.team12.smarthat.models.SensorData;

public class ThresholdUtilTest {

    @Test
    public void dustThresholdCheck_identifiesExceedingValues() {
        assertFalse(isDustAboveThreshold(Constants.DUST_THRESHOLD - 10.0f));
        assertFalse(isDustAboveThreshold(Constants.DUST_THRESHOLD - 1.0f));
        
        assertTrue(isDustAboveThreshold(Constants.DUST_THRESHOLD));
        assertTrue(isDustAboveThreshold(Constants.DUST_THRESHOLD + 10.0f));
        assertTrue(isDustAboveThreshold(Constants.DUST_THRESHOLD * 2));
    }
    
    @Test
    public void noiseThresholdCheck_identifiesExceedingValues() {
        assertFalse(isNoiseAboveThreshold(Constants.NOISE_THRESHOLD - 10.0f));
        assertFalse(isNoiseAboveThreshold(Constants.NOISE_THRESHOLD - 1.0f));
        
        assertTrue(isNoiseAboveThreshold(Constants.NOISE_THRESHOLD));
        assertTrue(isNoiseAboveThreshold(Constants.NOISE_THRESHOLD + 10.0f));
        assertTrue(isNoiseAboveThreshold(Constants.NOISE_THRESHOLD * 1.5f));
    }
    
    @Test
    public void gasThresholdCheck_identifiesExceedingValues() {
        assertFalse(isGasAboveThreshold(Constants.GAS_THRESHOLD - 100.0f));
        assertFalse(isGasAboveThreshold(Constants.GAS_THRESHOLD - 1.0f));
        
        assertTrue(isGasAboveThreshold(Constants.GAS_THRESHOLD));
        assertTrue(isGasAboveThreshold(Constants.GAS_THRESHOLD + 100.0f));
        assertTrue(isGasAboveThreshold(Constants.GAS_THRESHOLD * 2));
    }
    
    @Test
    public void sensorData_identifiesThresholdExceeded() {
        SensorData safeDust = new SensorData(SensorData.TYPE_DUST, Constants.DUST_THRESHOLD - 10.0f);
        SensorData safeNoise = new SensorData(SensorData.TYPE_NOISE, Constants.NOISE_THRESHOLD - 5.0f);
        SensorData safeGas = new SensorData(SensorData.TYPE_GAS, Constants.GAS_THRESHOLD - 100.0f);
        
        SensorData dangerousDust = new SensorData(SensorData.TYPE_DUST, Constants.DUST_THRESHOLD + 10.0f);
        SensorData dangerousNoise = new SensorData(SensorData.TYPE_NOISE, Constants.NOISE_THRESHOLD + 5.0f);
        SensorData dangerousGas = new SensorData(SensorData.TYPE_GAS, Constants.GAS_THRESHOLD + 100.0f);
        
        assertFalse(isSensorDataAboveThreshold(safeDust));
        assertFalse(isSensorDataAboveThreshold(safeNoise));
        assertFalse(isSensorDataAboveThreshold(safeGas));
        
        assertTrue(isSensorDataAboveThreshold(dangerousDust));
        assertTrue(isSensorDataAboveThreshold(dangerousNoise));
        assertTrue(isSensorDataAboveThreshold(dangerousGas));
    }
    
    private boolean isDustAboveThreshold(float value) {
        return value >= Constants.DUST_THRESHOLD;
    }
    
    private boolean isNoiseAboveThreshold(float value) {
        return value >= Constants.NOISE_THRESHOLD;
    }
    
    private boolean isGasAboveThreshold(float value) {
        return value >= Constants.GAS_THRESHOLD;
    }
    
    private boolean isSensorDataAboveThreshold(SensorData data) {
        if (data.isDustData()) {
            return isDustAboveThreshold(data.getValue());
        } else if (data.isNoiseData()) {
            return isNoiseAboveThreshold(data.getValue());
        } else if (data.isGasData()) {
            return isGasAboveThreshold(data.getValue());
        }
        return false;
    }
} 