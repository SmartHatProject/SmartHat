package com.team12.smarthat.utils;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;


import java.util.ArrayList;
import java.util.List;

public class PermissionUtils {
  /**not granted required permission list*/
  public static List<String> getRequiredPermissions(Context context) {
 List<String> requiredPermissions = new ArrayList<>();

if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH)
!= PackageManager.PERMISSION_GRANTED) {
requiredPermissions.add(Manifest.permission.BLUETOOTH);
  }
 if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN)

  != PackageManager.PERMISSION_GRANTED) { requiredPermissions.add(Manifest.permission.BLUETOOTH_ADMIN);}

 //  required for android 12+, os 31+
   if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
  if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
 != PackageManager.PERMISSION_GRANTED) {

     requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT);
   }
   
   // need this for ble scanning on android 12+
   if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
   != PackageManager.PERMISSION_GRANTED) {
     requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN);
   }
        }

// not checking location permissions here anymore handled separately in MainActivity
// for case "while using app" support

//android 13+ api 33+

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
    {
if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
   != PackageManager.PERMISSION_GRANTED) {
  requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS);
            }}

 return requiredPermissions;}
}