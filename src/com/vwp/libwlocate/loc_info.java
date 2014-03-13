package com.vwp.libwlocate;

import java.util.List;

import android.net.wifi.ScanResult;

public class loc_info
{
   public static final int LOC_METHOD_NONE=0;
   public static final int LOC_METHOD_LIBWLOCATE=1;
   public static final int LOC_METHOD_GPS=2;
   
   /** describes based on which method the last location was perfomed with */
   public int lastLocMethod=LOC_METHOD_NONE;
   
   /** request data that is used for libwlocate-based location request, its member bssids is filled with valid BSSIDs also in case of GPS location */
   public wloc_req requestData;
   
   /** result of last WiFi-scan, this list is filled with valid data also in case of GPS location */
   public List<ScanResult> wifiScanResult;
   
   /** last movement speed in km per hour, if no speed could be evaluated the value is smaller than 0*/ 
   public float lastSpeed=-1;
}