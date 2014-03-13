package org.microg.nlp.backend.openwlanmap;

import android.content.Context;
import android.location.Location;
import android.util.Log;
import com.vwp.libwlocate.WLocate;
import org.microg.nlp.api.LocationBackendService;
import org.microg.nlp.api.LocationHelper;

public class BackendService extends LocationBackendService {
	private static final String TAG = BackendService.class.getName();
	private WLocate wLocate;

	@Override
	protected void onOpen() {
		if (wLocate == null) {
			wLocate = new MyWLocate(this);
		} else {
			wLocate.doResume();
		}
	}

	@Override
	protected void onClose() {
		if (wLocate != null) {
			wLocate.doPause();
		}
	}

	@Override
	protected Location update() {
		if (wLocate != null) {
			wLocate.wloc_request_position(WLocate.FLAG_NO_GPS_ACCESS);
		}
		return null;
	}

	private class MyWLocate extends WLocate {

		public MyWLocate(Context ctx) throws IllegalArgumentException {
			super(ctx);
		}

		@Override
		protected void wloc_return_position(int ret, double lat, double lon, float radius, short ccode, float cog) {
			Log.d(TAG, String.format("wloc_return_position ret=%d lat=%f lon=%f radius=%f ccode=%d cog=%f", ret, lat, lon, radius, ccode, cog));
			if (ret == WLOC_OK) {
				Location location = LocationHelper.create("libwlocate", lat, lon, radius);
				if (cog != -1) {
					location.setBearing(cog);
				}
				report(location);
			}
		}
	}
}
