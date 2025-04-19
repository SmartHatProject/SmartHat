package com.team12.smarthat.models;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

public class SensorDataTest {
    
    private SensorData dustData;
    private SensorData noiseData;
    private SensorData gasData;
    
    @Before
    public void setUp() {
        dustData = new SensorData("dust", 50.0f);
        noiseData = new SensorData("noise", 75.0f);
        gasData = new SensorData("gas", 800.0f);
    }
    
    @Test
    public void sensorDataCreation_isCorrect() {
        assertEquals("dust", dustData.getSensorType());
        assertEquals(50.0f, dustData.getValue(), 0.01f);
        
        assertEquals("noise", noiseData.getSensorType());
        assertEquals(75.0f, noiseData.getValue(), 0.01f);
        
        assertEquals("gas", gasData.getSensorType());
        assertEquals(800.0f, gasData.getValue(), 0.01f);
    }
    
    @Test
    public void sensorTypeChecks_areCorrect() {
        assertTrue(dustData.isDustData());
        assertFalse(dustData.isNoiseData());
        assertFalse(dustData.isGasData());
        
        assertTrue(noiseData.isNoiseData());
        assertFalse(noiseData.isDustData());
        assertFalse(noiseData.isGasData());
        
        assertTrue(gasData.isGasData());
        assertFalse(gasData.isDustData());
        assertFalse(gasData.isNoiseData());
    }
    
    @Test
    public void testData_isIdentifiedCorrectly() {
        assertFalse(dustData.isTestData());
        
        dustData.setSource(SensorData.SOURCE_TEST);
        assertTrue(dustData.isTestData());
        
        dustData.setSource(SensorData.SOURCE_REAL);
        assertFalse(dustData.isTestData());
    }
    
    @Test
    public void valueValidation_clampsToValidRange() {
        SensorData highDust = new SensorData("dust", 2000.0f);
        assertEquals(1000.0f, highDust.getValue(), 0.01f);
        
        SensorData highNoise = new SensorData("noise", 200.0f);
        assertEquals(150.0f, highNoise.getValue(), 0.01f);
        
        SensorData highGas = new SensorData("gas", 6000.0f);
        assertEquals(5000.0f, highGas.getValue(), 0.01f);
        
        SensorData negativeDust = new SensorData("dust", -10.0f);
        assertEquals(0.0f, negativeDust.getValue(), 0.01f);
    }
    
    @Test
    public void formattedValue_hasCorrectUnits() {
        assertEquals("50.0 µg/m³", dustData.getFormattedValue());
        assertEquals("75.0 dB", noiseData.getFormattedValue());
        assertEquals("800.0 ppm", gasData.getFormattedValue());
    }
} 