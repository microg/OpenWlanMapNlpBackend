package com.vwp.libwlocate;

import java.io.*; 
import java.net.*;
import java.util.*;
import android.content.*;
import android.net.wifi.*;
import android.location.*;
import android.os.*;



/**
 * Internal class, used for receiving the result
 */
class wloc_res
{
   public byte  version,length;
   public byte  result,iresult;
   public short quality;
   public byte  cres6,cres7,cres8;
   public int   lat,lon;    
   public short ccode;
   public short wres34,wres56,wres78;
}



/**
 * Internal class, used for storing the position information
 */
class wloc_position
{
   wloc_position()
   {   
   }
   
   double lat,lon;
   short  quality;
   short  ccode;
}



/**
 * Geopositioning/location class to evaluate the current position without using the standard location mechanisms
 * where the privacy rules are not clear. It tries to evaluate the current geographic position by using GPs and
 * - in case this fails or GPS is not available - by using other parameters like surrounding WLAN networks.<BR><BR>
 * 
 * The usage is quite simple: create an own class that inherits from WLocate and overwrite method
 * wloc_return_position(). Call wloc_request_position() to start position evaluation. The resulting is returned via
 * overwritten method wloc_return_position() asynchronously.<BR><BR>
 *
 * Beside of that it is recommended to call the doPause() and doResume() methods whenever an onPause() and onResume()
 * event occurs in main Activity to avoid Exceptions caused by the WiFi-receiver.
 */
public class WLocate implements Runnable
{
   public static final int FLAG_NO_NET_ACCESS =0x0001; /** Don't perform any network accesses to evaluate the position data, this option disables the WLAN_based position retrieval */
   public static final int FLAG_NO_GPS_ACCESS =0x0002; /** Don't use a GPS device to evaluate the position data, this option disables the WLAN_based position retrieval */
   public static final int FLAG_NO_IP_LOCATION=0x0004; /** Don't send a request to the server for IP-based location in case no WLANs are available */
   
   public static final int WLOC_OK=0;               /** Result code for position request, given position information are OK */
   public static final int WLOC_CONNECTION_ERROR=1; /** Result code for position request, a connection error occured, no position information are available */
   public static final int WLOC_SERVER_ERROR=2;     
   public static final int WLOC_LOCATION_ERROR=3;   /** Result code for position request, the position could not be evaluated, no position information are available */
   public static final int WLOC_ERROR=100;          /** Result code for position request, an unknown error occured, no position information are available */
   
   private static final int WLOC_RESULT_OK=1;
//   private static final int WLOC_RESULT_ERROR=2;
//   private static final int WLOC_RESULT_IERROR=3;
   
   private Location            lastLocation=null;
   private LocationManager     location;
   private GPSLocationListener locationListener;
   private GPSStatusListener   statusListener;
   private WifiManager         wifi;
   private WifiReceiver        receiverWifi = new WifiReceiver();
   private boolean             GPSAvailable=false,scanStarted=false;
   private double              m_lat,m_lon;
   private float               m_radius=1.0f,m_speed=-1.0f,m_cog=-1.0f;
   private int                 scanFlags;
   private long                lastLocationMillis=0;
   private Context             ctx;
   private loc_info            locationInfo=new loc_info();
   private Thread              netThread=null;
   private WLocate             me;


   /**
    * Constructor for WLocate class, this constructor has to be overwritten by inheriting class
    * @param ctx current context, hand over Activity object here
    */
   public WLocate(Context ctx)
   throws IllegalArgumentException
   {      
      wifi = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
      this.ctx=ctx;
      //startGPSLocation();
      me=this;
      doResume();
   }

   
   
   private void startGPSLocation()
   {
      location= (LocationManager)ctx.getSystemService(Context.LOCATION_SERVICE);
      locationListener = new GPSLocationListener();
      location.requestLocationUpdates(LocationManager.GPS_PROVIDER,350,0,(LocationListener)locationListener);
      statusListener=new GPSStatusListener();
      location.addGpsStatusListener(statusListener);
   }
      
   /**
   * Send pause-information to active WLocate object. This method should be called out of the main Activity
   * whenever an onPause() event occurs to avoid leakted IntentReceiver exceptions caused by the receiver
   * when it is still registered after application switched over to pause state
   */
   public void doPause()
   {
      ctx.unregisterReceiver(receiverWifi);
   }
   
   
   
   /**
   * Send resume-information to active WLocate object. This method has to be called out of the main Activity
   * whenever an onResume() event occurs and in case the doPause() method is used to reactivate the WiFi
   * receiver
   */
   public void doResume()
   {
      ctx.registerReceiver(receiverWifi, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));            
   }
   
   
   
   /**
    * Start position evaluation process, the result is returned via method wloc_return_position()
    * that may be called asynchronously
    * @param flags specifies how the position has to be evaluated using the FLAG_NO_xxx_ACCESS-values
    */
   public void wloc_request_position(int flags)
   {
      scanFlags=flags;
      scanStarted=true;
      if ((!wifi.isWifiEnabled()) && (!GPSAvailable))
       wloc_return_position(WLOC_LOCATION_ERROR,0.0,0.0,0.0f,(short)0);         
      wifi.startScan();
   }

   
   
   public loc_info last_location_info()
   {
      return locationInfo;
   }   

   
   
   private int get_position(wloc_req request,wloc_position position)
   {
      Socket wlocSocket;
      DataInputStream  din;
      DataOutputStream dout;
      int              i;
      wloc_res         result=new wloc_res();
      
      try
      {
         wlocSocket=new Socket("api.openwlanmap.org",10443);
         dout=new DataOutputStream(wlocSocket.getOutputStream());
         dout.write(locationInfo.requestData.version);
         dout.write(locationInfo.requestData.length);
         for (i=0; i<wloc_req.WLOC_MAX_NETWORKS; i++)
          dout.write(locationInfo.requestData.bssids[i],0,6);
         for (i=0; i<wloc_req.WLOC_MAX_NETWORKS; i++)
          dout.writeByte(locationInfo.requestData.signal[i]);
         dout.writeInt(locationInfo.requestData.cgiIP);
         dout.flush();

         din=new DataInputStream(wlocSocket.getInputStream());
         result.version=din.readByte();
         result.length=din.readByte();
         result.result=din.readByte();
         result.iresult=din.readByte();
         result.quality=din.readByte();
         if (result.quality<0) result.quality+=255;
         if (result.quality>100) result.quality=100;
         result.cres6=din.readByte();
         result.cres7=din.readByte();
         result.cres7=din.readByte();
         result.lat=din.readInt();
         result.lon=din.readInt();
         result.ccode=din.readShort();
         result.wres34=din.readShort();
         result.wres56=din.readShort();
         result.wres78=din.readShort();
         if (result.result!=WLOC_RESULT_OK)
         {
            wlocSocket.close();
            return WLOC_LOCATION_ERROR;
         }
         position.lat=result.lat/10000000.0;
         position.lon=result.lon/10000000.0;
         position.quality=result.quality;
      }
      catch (Exception e)
      {
         e.printStackTrace();
         return WLOC_CONNECTION_ERROR;
      }
      return WLOC_OK;
   }

   
   
   class WifiReceiver extends BroadcastReceiver 
   {
      public void onReceive(Context c, Intent intent) 
      {
         int           netCnt=0;
         
         if (!scanStarted) return;
         scanStarted=false;
         List<ScanResult> configs=wifi.getScanResults();
         if (configs==null) return;
         locationInfo.wifiScanResult=configs;
         locationInfo.requestData=new wloc_req();
         if (configs.size()>0) for (ScanResult config : configs) 
         {            
            String bssidStr[];

            config.BSSID=config.BSSID.toUpperCase(Locale.US).replace(".",":"); // some strange devices use a dot instead of :
            bssidStr=config.BSSID.split(":");
            locationInfo.requestData.bssids[netCnt][0]=(byte)Integer.parseInt(bssidStr[0],16);
            locationInfo.requestData.bssids[netCnt][1]=(byte)Integer.parseInt(bssidStr[1],16);
            locationInfo.requestData.bssids[netCnt][2]=(byte)Integer.parseInt(bssidStr[2],16);
            locationInfo.requestData.bssids[netCnt][3]=(byte)Integer.parseInt(bssidStr[3],16);
            locationInfo.requestData.bssids[netCnt][4]=(byte)Integer.parseInt(bssidStr[4],16);
            locationInfo.requestData.bssids[netCnt][5]=(byte)Integer.parseInt(bssidStr[5],16);
            locationInfo.requestData.signal[netCnt]=(byte)Math.abs(config.level);               
            
            netCnt++;
            if (netCnt>=wloc_req.WLOC_MAX_NETWORKS) break;   
         }        
         locationInfo.lastLocMethod=loc_info.LOC_METHOD_NONE;
         locationInfo.lastSpeed=-1.0f;
         if (GPSAvailable) GPSAvailable=(SystemClock.elapsedRealtime()-lastLocationMillis) < 7500;
         if (!GPSAvailable)
         {
            if ((scanFlags & FLAG_NO_NET_ACCESS)!=0)
            {
           	   wloc_return_position(WLOC_LOCATION_ERROR,0.0,0.0,(float)0.0,(short)0);
           	   wloc_return_position(WLOC_LOCATION_ERROR,0.0,0.0,(float)0.0,(short)0,(float)-1.0);
            }
            else if ((configs.size()>0) || ((scanFlags & FLAG_NO_IP_LOCATION)==0))
            {
               if ((netThread!=null) && (netThread.isAlive())) return;
               netThread=new Thread(me);
               netThread.start();
            }
            else
            {
               wloc_return_position(WLOC_LOCATION_ERROR,0.0,0.0,(float)0.0,(short)0);
               wloc_return_position(WLOC_LOCATION_ERROR,0.0,0.0,(float)0.0,(short)0,(float)-1.0);
            }
         }
         else
         {
            // TODO: disable GPS in case NO_GPS_FLAG is set and re-enable it on next call without this option
            if ((scanFlags & FLAG_NO_GPS_ACCESS)!=0)
            {
               wloc_return_position(WLOC_LOCATION_ERROR,0.0,0.0,(float)0.0,(short)0);
               wloc_return_position(WLOC_LOCATION_ERROR,0.0,0.0,(float)0.0,(short)0,(float)-1.0);
            }
            else
            {
               locationInfo.lastSpeed=m_speed;
               locationInfo.lastLocMethod=loc_info.LOC_METHOD_GPS;               
               wloc_return_position(WLOC_OK,m_lat,m_lon,m_radius,(short)0);         
               wloc_return_position(WLOC_OK,m_lat,m_lon,m_radius,(short)0,m_cog);         
            }
         }         
      }
   }

   
   
   public void run()
   {
      int           ret=WLOC_LOCATION_ERROR;
      wloc_position pos=null;
      
      pos=new wloc_position();
      ret=get_position(locationInfo.requestData,pos);
      locationInfo.lastLocMethod=loc_info.LOC_METHOD_LIBWLOCATE;
      if (pos.quality<=0)
      {
         wloc_return_position(ret,pos.lat,pos.lon,10000.0f,pos.ccode);
         wloc_return_position(ret,pos.lat,pos.lon,10000.0f,pos.ccode,(float)-1.0);
      }
      else
      {
         wloc_return_position(ret,pos.lat,pos.lon,120-pos.quality,pos.ccode);
         wloc_return_position(ret,pos.lat,pos.lon,120-pos.quality,pos.ccode,(float)-1.0);
      }
   }
   
   
   /**
    * This method is called as soon as a result of a position evaluation request is available.
    * Thus this method should be overwritten by the inheriting class to receive the results there.
    * @param ret the return code that informs if the location evaluation request could be fulfilled
    *        successfully or not. Only in case this parameter is equal to WLOC_OK all the other
    *        ones can be used, elsewhere no position information could be retrieved.
    * @param lat the latitude of the current position 
    * @param lon the latitude of the current position 
    * @param radius the accuracy of the position information, this radius specifies the range around
    *        the given latitude and longitude information of the real position. The smaller this value
    *        is the more accurate the given position information is.
    * @param ccode code of the country where the current position is located within, in case the
    *        country is not known, 0 is returned. The country code can be converted to a text that
    *        specifies the country by calling wloc_get_country_from_code()
    */
   protected void wloc_return_position(int ret,double lat,double lon,float radius,short ccode)
   {
      
   }

   
   
   /**
    * This method is called as soon as a result of a position evaluation request is available.
    * Thus this method should be overwritten by the inheriting class to receive the results there.
    * @param ret the return code that informs if the location evaluation request could be fulfilled
    *        successfully or not. Only in case this parameter is equal to WLOC_OK all the other
    *        ones can be used, elsewhere no position information could be retrieved.
    * @param lat the latitude of the current position 
    * @param lon the latitude of the current position 
    * @param radius the accuracy of the position information, this radius specifies the range around
    *        the given latitude and longitude information of the real position. The smaller this value
    *        is the more accurate the given position information is.
    * @param ccode code of the country where the current position is located within, in case the
    *        country is not known, 0 is returned. The country code can be converted to a text that
    *        specifies the country by calling wloc_get_country_from_code()
    * @param cog the actual course over ground / bearing in range 0.0..360.0 degrees. This value is not
    *        always available, in case the current course over ground is not known or could not evaluated
    *        -1.0 is returned here.
    */
   protected void wloc_return_position(int ret,double lat,double lon,float radius,short ccode,float cog)
   {
      
   }

   
   
   /**
    * Convert a country code to a more easy to read short text that specifies a country.
    * @param ccode the country code to be converted
    * @return the short text that names the country or an empty string an unknown country
    *         specifier was given or the country code was 0
    */
   public String wloc_get_country_from_code(short ccode)
   {
      switch (ccode)
      {
         case 1:
            return "DE";
         case 2:
            return "AT";
         case 3:
            return "CH";
         case 4:
            return "NL";
         case 5:
            return "BE";
         case 6:
            return "LU";
         case 7:
            return "NO";
         case 8:
            return "SE";
         case 9:
            return "DK";
         case 10:
            return "AF";
         case 12:
            return "AL";
         case 13:
            return "DZ";
         case 17:
            return "AN";
         case 18:
            return "AG";
         case 19:
            return "AR";
         case 20:
            return "AM";
         case 21:
            return "AU";
         case 23:
            return "BS";
         case 24:
            return "BH";
         case 25:
            return "BD";
         case 26:
            return "BB";
         case 27:
            return "BY";
         case 28:
            return "BZ";
         case 29:
            return "BJ";
         case 30:
            return "BM";
         case 32:
            return "BO";
         case 33:
            return "BA";
         case 36:
            return "BR";
         case 37:
            return "BN";
         case 38:
            return "BG";
         case 43:
            return "CA";
         case 44:
            return "CV";
         case 47:
            return "CL";
         case 48:
            return "CN";
         case 49:
            return "CO";
         case 52:
            return "CR";
         case 53:
            return "HR";
         case 55:
            return "CY";
         case 56:
            return "CZ";
         case 59:
            return "DO";
         case 60:
            return "EC";
         case 61:
            return "EG";
         case 66:
            return "ET";
         case 68:
            return "FI";
         case 69:
            return "FR";
         case 73:
            return "GH";
         case 75:
            return "GR";
         case 76:
            return "GL";
         case 77:
            return "GD";
         case 78:
            return "GU";
         case 79:
            return "GT";
         case 82:
            return "HT";
         case 83:
            return "HN";
         case 84:
            return "HK";
         case 85:
            return "HU";
         case 86:
            return "IS";
         case 87:
            return "IN";
         case 88:
            return "ID";
         case 89:
            return "IR";
         case 90:
            return "IQ";
         case 91:
            return "IE";
         case 93:
            return "IT";
         case 94:
            return "JM";
         case 95:
            return "JP";
         case 97:
            return "JO";
         case 98:
            return "KZ";
         case 99:
            return "KE";
         case 102:
            return "KR";
         case 103:
            return "KW";
         case 104:
            return "KG";
         case 105:
            return "LA";
         case 106:
            return "LV";
         case 107:
            return "LB";
         case 108:
            return "LS";
         case 111:
            return "LT";
         case 115:
            return "MY";
         case 116:
            return "MV";
         case 118:
            return "MT";
         case 119:
            return "MQ";
         case 121:
            return "MU";
         case 123:
            return "MX";
         case 124:
            return "MC";
         case 125:
            return "MN";
         case 126:
            return "MA";
         case 127:
            return "MZ";
         case 131:
            return "NZ";
         case 133:
            return "NI";
         case 135:
            return "NG";
         case 137:
            return "OM";
         case 138:
            return "PK";
         case 141:
            return "PA";
         case 142:
            return "PY";
         case 144:
            return "PE";
         case 145:
            return "PH";
         case 147:
            return "PL";
         case 148:
            return "PT";
         case 149:
            return "PR";
         case 150:
            return "QA";
         case 151:
            return "RO";
         case 152:
            return "RU";
         case 155:
            return "SM";
         case 157:
            return "SA";
         case 158:
            return "SN";
         case 161:
            return "SG";
         case 162:
            return "SK";
         case 163:
            return "SI";
         case 166:
            return "ZA";
         case 167:
            return "ES";
         case 168:
            return "LK";
         case 169:
            return "SD";
         case 170:
            return "SR";
         case 172:
            return "SY";
         case 173:
            return "TW";
         case 174:
            return "TJ";
         case 175:
            return "TZ";
         case 176:
            return "TH";
         case 179:
            return "TT";
         case 180:
            return "TN";
         case 181:
            return "TR";
         case 182:
            return "TM";
         case 185:
            return "UA";
         case 186:
            return "AE";
         case 187:
            return "UK";
         case 188:
            return "US";
         case 189:
            return "UY";
         case 191:
            return "VE";
         case 192:
            return "VN";
         case 195:
            return "ZM";
         case 196:
            return "ZW";
         default:
            return "";
      }
   }

   
   
   private class GPSStatusListener implements GpsStatus.Listener 
   {
      
      public void onGpsStatusChanged(int event) 
      {
         switch (event) 
         {
            case GpsStatus.GPS_EVENT_SATELLITE_STATUS:
               if (lastLocation != null)
               {
                  GPSAvailable=(SystemClock.elapsedRealtime()-lastLocationMillis) < 3500;
               }
               break;
            case GpsStatus.GPS_EVENT_FIRST_FIX:
               // Do something.
               GPSAvailable=true;
               break;
            case GpsStatus.GPS_EVENT_STOPPED:
               GPSAvailable=false;
               break;
         }
      }
   }   
    
   
   private class GPSLocationListener implements LocationListener 
   {
      public void onLocationChanged(Location gLocation) 
      {
         if (location == null) return;
         lastLocationMillis = SystemClock.elapsedRealtime();
         lastLocation =new Location(gLocation);         
//         GPSAvailable=true;
         m_lat=gLocation.getLatitude();
         m_lon=gLocation.getLongitude();
         m_cog=gLocation.getBearing(); // course over ground/orientation
         if (gLocation.hasSpeed()) m_speed=gLocation.getSpeed(); //m/sec
         else m_speed=-1;
         if (gLocation.hasAccuracy()) m_radius=gLocation.getAccuracy();
         else m_radius=-1;
      }

      public void onStatusChanged(String provider, int status, Bundle extras)
      {
         if ((provider!=null) && (provider.equalsIgnoreCase(LocationManager.GPS_PROVIDER)))
         {
            if (status!=LocationProvider.AVAILABLE) GPSAvailable=false;
         }
      }

      public void onProviderEnabled(String provider)
      {
      }

      public void onProviderDisabled(String provider)
      {
         GPSAvailable=false;
      }
   };
   
}


