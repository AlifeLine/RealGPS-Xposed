package cn.imaq.realgps.xposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by adn55 on 2017/2/15.
 */
public class ZuobihiReceiver extends BroadcastReceiver {

    static String action = BuildConfig.APPLICATION_ID + ".UPDATE";
    static final IntentFilter intentFilter = new IntentFilter(action);

    private double lat, lng, alt;
    private float speed, bearing, accr;
    private long time;

    private int svCount;
    private int[] prn;
    private float[] snr;
    private float[] elv;
    private float[] azm;
    private int mask;

    private Random rand = new Random();
    private int ttff = 1 + rand.nextInt(9);
    private GpsStatus gpsStatus = (GpsStatus) XposedHelpers.newInstance(GpsStatus.class);
    private Location location = new Location(LocationManager.GPS_PROVIDER);
    ArrayList<GpsStatus.Listener> gpsListeners = new ArrayList<>();
    ArrayList<LocationListener> locationListeners = new ArrayList<>();

    private static ZuobihiReceiver _instance;

    static ZuobihiReceiver getInstance() {
        if (_instance == null) {
            _instance = new ZuobihiReceiver();
        }
        return _instance;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // LOC
        lat = intent.getDoubleExtra("lat", 0);
        lng = intent.getDoubleExtra("lng", 0);
        alt = intent.getDoubleExtra("alt", 0);
        speed = intent.getFloatExtra("speed", 0);
        bearing = intent.getFloatExtra("bearing", 0);
        accr = intent.getFloatExtra("accr", 0);
        time = intent.getLongExtra("time", 0);
        // SAT
        svCount = intent.getIntExtra("svCount", 0);
        prn = intent.getIntArrayExtra("prn");
        snr = intent.getFloatArrayExtra("snr");
        elv = intent.getFloatArrayExtra("elv");
        azm = intent.getFloatArrayExtra("azm");
        mask = intent.getIntExtra("mask", 0);
        // XposedBridge.log("ZuobihiReceiver: received " + svCount + " satellites");
        // notify listeners
        if (svCount > 0) {
            for (LocationListener listener : locationListeners) {
                listener.onLocationChanged(getAsLocation(LocationManager.GPS_PROVIDER));
                XposedBridge.log("LISTENER_NOTIFIED " + listener.getClass().getCanonicalName());
            }
            for (GpsStatus.Listener listener : gpsListeners) {
                listener.onGpsStatusChanged(GpsStatus.GPS_EVENT_SATELLITE_STATUS);
            }
        }
    }

    Location getAsLocation(String provider) {
        location.setProvider(provider);
        location.setLatitude(lat);
        location.setLongitude(lng);
        location.setAltitude(alt);
        location.setSpeed(speed);
        location.setBearing(bearing);
        location.setAccuracy(accr);
        location.setTime(time);
        return location;
    }

    GpsStatus getAsGpsStatus() {
        if (Build.VERSION.SDK_INT >= 24) {

        } else {
            XposedHelpers.callMethod(gpsStatus, "setStatus", svCount, prn, snr, elv, azm, mask, mask, mask);
            XposedHelpers.callMethod(gpsStatus, "setTimeToFirstFix", ttff);
        }
        return gpsStatus;
    }

}
