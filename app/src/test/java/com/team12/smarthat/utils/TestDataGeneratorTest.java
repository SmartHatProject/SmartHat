package com.team12.smarthat.utils;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.team12.smarthat.models.SensorData;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class TestDataGeneratorTest {

    private TestDataGenerator testDataGenerator;
    private TestDataGenerator.TestDataListener mockListener;
    
    @Before
    public void setUp() {
        testDataGenerator = new TestDataGenerator();
        mockListener = mock(TestDataGenerator.TestDataListener.class);
        testDataGenerator.setListener(mockListener);
    }
    
    @Test
    public void testModeStates_areManaged() {
        assertEquals(TestDataGenerator.TestMode.OFF, testDataGenerator.getCurrentMode());
        assertFalse(testDataGenerator.isTestModeActive());
        
        testDataGenerator.setCurrentMode(TestDataGenerator.TestMode.NORMAL);
        assertEquals(TestDataGenerator.TestMode.NORMAL, testDataGenerator.getCurrentMode());
        
        testDataGenerator.setCurrentMode(TestDataGenerator.TestMode.HIGH_DUST);
        assertEquals(TestDataGenerator.TestMode.HIGH_DUST, testDataGenerator.getCurrentMode());
        
        testDataGenerator.setCurrentMode(TestDataGenerator.TestMode.HIGH_NOISE);
        assertEquals(TestDataGenerator.TestMode.HIGH_NOISE, testDataGenerator.getCurrentMode());
        
        testDataGenerator.setCurrentMode(TestDataGenerator.TestMode.HIGH_GAS);
        assertEquals(TestDataGenerator.TestMode.HIGH_GAS, testDataGenerator.getCurrentMode());
        
        testDataGenerator.setCurrentMode(TestDataGenerator.TestMode.RANDOM);
        assertEquals(TestDataGenerator.TestMode.RANDOM, testDataGenerator.getCurrentMode());
        
        testDataGenerator.setCurrentMode(TestDataGenerator.TestMode.OFF);
        assertEquals(TestDataGenerator.TestMode.OFF, testDataGenerator.getCurrentMode());
    }
    
    @Test
    public void startAndStopTestMode_updatesRunningState() {
        testDataGenerator.startTestMode(TestDataGenerator.TestMode.NORMAL);
        assertEquals(TestDataGenerator.TestMode.NORMAL, testDataGenerator.getCurrentMode());
        assertTrue(testDataGenerator.isTestModeActive());
        
        testDataGenerator.stopTestMode();
        assertEquals(TestDataGenerator.TestMode.OFF, testDataGenerator.getCurrentMode());
        assertFalse(testDataGenerator.isTestModeActive());
        
        testDataGenerator.startTestMode(TestDataGenerator.TestMode.NORMAL);
        assertTrue(testDataGenerator.isTestModeActive());
        
        testDataGenerator.startTestMode(TestDataGenerator.TestMode.OFF);
        assertEquals(TestDataGenerator.TestMode.OFF, testDataGenerator.getCurrentMode());
        assertFalse(testDataGenerator.isTestModeActive());
    }
    
    @Test
    public void cleanup_resetsState() {
        testDataGenerator.startTestMode(TestDataGenerator.TestMode.NORMAL);
        assertTrue(testDataGenerator.isTestModeActive());
        
        testDataGenerator.cleanup();
        
        assertFalse(testDataGenerator.isTestModeActive());
        assertEquals(TestDataGenerator.TestMode.OFF, testDataGenerator.getCurrentMode());
    }
} 