package org.microg.nlp.backend.openwlanmap;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Environment;
import android.util.Log;

public class Configuration {
	private static String TAG = Configuration.class.getName();

	public static boolean networkAllowed;

	public static String dbLocation = Environment.getExternalStorageDirectory().getAbsolutePath() + "/.nogapps/openwifimap.db";

	public static float assumedAccuracy;
	
	public static ConfigChangedListener listener = new ConfigChangedListener();
	

	public static void fillFromPrefs(SharedPreferences sharedPrefs) {

		networkAllowed = sharedPrefs.getBoolean("networkAllowed", false);
		Log.d(TAG, "Network allowed: " + networkAllowed);

		dbLocation = sharedPrefs.getString("databaseLocation", Environment.getExternalStorageDirectory().getAbsolutePath()
				+ "/.nogapps/openwifimap.db");

		try {
			assumedAccuracy = Float.parseFloat(sharedPrefs.getString("assumedAccuracy", "50"));
		} catch (NumberFormatException e) {
			assumedAccuracy = 50;
		}
	}
	
	private static class ConfigChangedListener implements OnSharedPreferenceChangeListener {
		
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
				String key) {
			fillFromPrefs(sharedPreferences);
		}
	}
	
}
