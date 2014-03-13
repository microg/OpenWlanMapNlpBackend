package com.vwp.libwlocate;

/**
 * Internal class, used for storing request data
 */
public class wloc_req
{
   public static final int WLOC_MAX_NETWORKS=16;
   
   public byte     version,length;
   public byte[][] bssids=new byte[WLOC_MAX_NETWORKS][6];  
   public byte[]   signal=new byte[WLOC_MAX_NETWORKS];
   public int      cgiIP;
   
   wloc_req()
   {
      version=1;
      length=118;
      cgiIP=0;
   }
}

