package org.microg.nlp.backend.openwlanmap.local;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiReceiver extends BroadcastReceiver {

	private boolean scanStarted = false;
	private WifiManager wifi;
	private String TAG = WifiReceiver.class.getName();
	private boolean DEBUG = true;
	private WifiReceivedCallback callback;

	public WifiReceiver(Context ctx, WifiReceivedCallback aCallback) {
		wifi = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
		callback = aCallback;
	}

	public void onReceive(Context c, Intent intent) {
		if (!isScanStarted())
			return;
		setScanStarted(false);
		List<ScanResult> configs = wifi.getScanResults();

		if (DEBUG) Log.d(TAG, "Got " + configs.size() + " wifi access points");

		if (configs.size() > 0) {

			List<String> foundBssids = new ArrayList<String>(configs.size());

			for (ScanResult config : configs) {
				// some strange devices use a dot instead of :
				final String canonicalBSSID = config.BSSID.toUpperCase(Locale.US).replace(".",":");
				// ignore APs that have _nomap suffix on SSID
				if (config.SSID.endsWith("_nomap")) {
					if (DEBUG) Log.d(TAG, "Ignoring AP '" + config.SSID + "' BSSID: " + canonicalBSSID);
				} else {
					foundBssids.add(canonicalBSSID);
				}
			}

			callback.process(foundBssids);
		}

	}

	public boolean isScanStarted() {
		return scanStarted;
	}

	public void setScanStarted(boolean scanStarted) {
		this.scanStarted = scanStarted;
	}


	public interface WifiReceivedCallback {

		void process(List<String> foundBssids);

	}

	public void startScan() {
		setScanStarted(true);
		if (!wifi.isWifiEnabled() && !wifi.isScanAlwaysAvailable()) {
			Log.d(TAG, "Wifi is disabled and we can't scan either. Not doing anything.");
		}
		wifi.startScan();
	}
}
