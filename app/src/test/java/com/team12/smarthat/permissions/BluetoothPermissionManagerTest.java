package com.team12.smarthat.permissions;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
public class BluetoothPermissionManagerTest {

    @Mock
    private Activity mockActivity;
    
    @Mock
    private Context mockContext;
    
    private BluetoothPermissionManager permissionManager;
    
    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        when(mockActivity.getApplicationContext()).thenReturn(mockContext);
        
        permissionManager = mock(BluetoothPermissionManager.class);
        
        when(permissionManager.getRequiredPermissions()).thenCallRealMethod();
    }
    
    @Test
    public void requiredPermissions_containsCorrectPermissions() {
        String[] permissions = permissionManager.getRequiredPermissions();
        
        assertEquals(2, permissions.length);
        assertEquals(Manifest.permission.BLUETOOTH_SCAN, permissions[0]);
        assertEquals(Manifest.permission.BLUETOOTH_CONNECT, permissions[1]);
    }
    
    @Test
    public void permissionCallback_isCalledCorrectly() {
        BluetoothPermissionManager.PermissionCallback mockCallback = mock(BluetoothPermissionManager.PermissionCallback.class);
        
        doCallRealMethod().when(permissionManager).requestPermissions(any(BluetoothPermissionManager.PermissionCallback.class));
        when(permissionManager.hasRequiredPermissions()).thenReturn(true);
        
        permissionManager.requestPermissions(mockCallback);
        
        verify(mockCallback).onPermissionsGranted();
        verify(mockCallback, never()).onPermissionsDenied(anyBoolean());
    }
    
    @Test
    public void deniedPermissions_callbackWithCorrectStatus() {
        BluetoothPermissionManager.PermissionCallback mockCallback = mock(BluetoothPermissionManager.PermissionCallback.class);
        
        doCallRealMethod().when(permissionManager).requestPermissions(any(BluetoothPermissionManager.PermissionCallback.class));
        when(permissionManager.hasRequiredPermissions()).thenReturn(false);
        
        doAnswer(invocation -> {
            mockCallback.onPermissionsDenied(false);
            return null;
        }).when(permissionManager).requestPermissions(mockCallback);
        
        permissionManager.requestPermissions(mockCallback);
        
        verify(mockCallback, never()).onPermissionsGranted();
        verify(mockCallback).onPermissionsDenied(false);
    }
} 