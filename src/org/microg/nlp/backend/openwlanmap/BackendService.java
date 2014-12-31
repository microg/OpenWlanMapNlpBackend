package org.microg.nlp.backend.openwlanmap;

import java.util.ArrayList;
import java.util.List;

import org.microg.nlp.api.LocationBackendService;
import org.microg.nlp.api.LocationHelper;
import org.microg.nlp.backend.openwlanmap.local.WifiLocationFile;
import org.microg.nlp.backend.openwlanmap.local.WifiReceiver;
import org.microg.nlp.backend.openwlanmap.local.WifiReceiver.WifiReceivedCallback;

import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Log;

import com.vwp.libwlocate.WLocate;

public class BackendService extends LocationBackendService {
	private static final String TAG = BackendService.class.getName();
	private WLocate wLocate;
	private WifiLocationFile wifiLocationFile;
	private WifiReceiver wifiReceiver;
	private boolean networkAllowed;

	@Override
	protected void onOpen() {
		Log.d(TAG, "onOpen");

		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		Configuration.fillFromPrefs(sharedPrefs);
		sharedPrefs.registerOnSharedPreferenceChangeListener(Configuration.listener);

		setOperatingMode();
	}

	@Override
	protected void onClose() {
		if (Configuration.debugEnabled) Log.d(TAG, "onClose");

		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		sharedPrefs.unregisterOnSharedPreferenceChangeListener(Configuration.listener);

		cleanupOperatingMode();

	}

    private void setOperatingMode() {
		this.networkAllowed = Configuration.networkAllowed;
		if (this.networkAllowed) {
			if (wLocate == null) {
				wLocate = new MyWLocate(this);
			} else {
				wLocate.doResume();
			}
		} else {
			openDatabase();
			if (wifiReceiver == null) {
				wifiReceiver = new WifiReceiver(this, new WifiDBResolver());
			}
			registerReceiver(wifiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
		}
    }

    private void cleanupOperatingMode() {
		if (wLocate != null) {
			wLocate.doPause();
		}
		if (wifiReceiver != null) {
			unregisterReceiver(wifiReceiver);
		}
    }

	@Override
	protected Location update() {
		if (Configuration.debugEnabled) Log.d(TAG, "update");

		if (this.networkAllowed != Configuration.networkAllowed) {
			if (Configuration.debugEnabled) Log.d(TAG, "Network allowed changed");
        		cleanupOperatingMode();
        		setOperatingMode();
		}
		if (wLocate != null) {
			if (Configuration.debugEnabled) Log.d(TAG, "Requesting location from net");
			wLocate.wloc_request_position(WLocate.FLAG_NO_GPS_ACCESS);
			return null;
		}

		if (wifiReceiver != null) {
			if (Configuration.debugEnabled) Log.d(TAG, "Requesting location from db");
			wifiReceiver.startScan();
		}

		return null;
	}

	private void openDatabase() {
		if (wifiLocationFile == null) {
			wifiLocationFile = new WifiLocationFile();
		}
	}

	private class MyWLocate extends WLocate {

		public MyWLocate(Context ctx) throws IllegalArgumentException {
			super(ctx);
		}

		@Override
		protected void wloc_return_position(int ret, double lat, double lon, float radius, short ccode, float cog) {
			if (Configuration.debugEnabled) Log.d(TAG, String.format("wloc_return_position ret=%d lat=%f lon=%f radius=%f ccode=%d cog=%f", ret, lat, lon, radius, ccode, cog));
			if (ret == WLOC_OK) {
				Location location = LocationHelper.create("libwlocate", lat, lon, radius);
				if (cog != -1) {
					location.setBearing(cog);
				}
				report(location);
			}
		}
	}

	private class WifiDBResolver implements WifiReceivedCallback {

		@Override
		public void process(List<String> foundBssids) {

			if (foundBssids == null || foundBssids.isEmpty()) {
				return;
			}
			if (wifiLocationFile != null) {

				List<Location> locations = new ArrayList<Location>(foundBssids.size());

				for (String bssid : foundBssids) {
					Location result = wifiLocationFile.query(bssid);
					if (result != null) {
						locations.add(result);
					}
				}

				if (locations.isEmpty()) {
					return;
				}

				//TODO fix LocationHelper:average to not calculate with null values
				//TODO sort out wifis obviously in the wrong spot
				Location avgLoc = LocationHelper.average("owm", locations);

				if (avgLoc == null) {
					Log.e(TAG, "Averaging locations did not work.");
					return;
				}

				if (Configuration.debugEnabled) Log.d(TAG, "Reporting location: " + avgLoc.toString());
				report(avgLoc);
			}
		}
	}
}
