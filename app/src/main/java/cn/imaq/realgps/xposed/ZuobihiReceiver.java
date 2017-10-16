package cn.imaq.realgps.xposed;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.*;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.ListIterator;
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
    int ttff = 1000 + rand.nextInt(9000);
    private Bundle gpsExtras = new Bundle();
    private LinkedList<ListenerWrapper> listenerWrappers = new LinkedList<>();
    LinkedList<GpsStatus.Listener> gpsListeners = new LinkedList<>();
    LinkedList<GnssStatus.Callback> gnssCallbacks = new LinkedList<>();

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
        gpsExtras.putInt("satellites", svCount);
        if (svCount > 0) {
            for (ListenerWrapper wrapper : listenerWrappers) {
                if (wrapper.listener != null) {
                    wrapper.listener.onLocationChanged(getAsLocation(wrapper.provider));
                    // XposedBridge.log("LISTENER_NOTIFIED type=" + wrapper.provider + " " + wrapper.listener.getClass().getName());
                }
                // TODO PendingIntent
            }
            for (GpsStatus.Listener listener : gpsListeners) {
                // XposedBridge.log("GPS Listener notified: " + listener);
                listener.onGpsStatusChanged(GpsStatus.GPS_EVENT_SATELLITE_STATUS);
            }
            for (GnssStatus.Callback callback : gnssCallbacks) {
                // XposedBridge.log("GNSS Callback notified: " + callback);
                callback.onSatelliteStatusChanged(getAsGnssStatus());
            }
        }
    }

    Location getAsLocation(String provider) {
        if (time == 0) {
            return null;
        }
        Location location = new Location(provider);
        location.setLatitude(lat);
        location.setLongitude(lng);
        location.setAltitude(alt);
        location.setSpeed(speed);
        location.setBearing(bearing);
        location.setAccuracy(accr);
        location.setTime(time);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }
        if (provider.equals(LocationManager.GPS_PROVIDER)) {
            location.setExtras(gpsExtras);
        }
        return location;
    }

    GpsStatus getAsGpsStatus(GpsStatus status) {
        if (status == null) {
            status = (GpsStatus) XposedHelpers.newInstance(GpsStatus.class);
        }
        if (Build.VERSION.SDK_INT < 24) {
            XposedHelpers.callMethod(status, "setStatus", svCount, prn, snr, elv, azm, mask, mask, mask);
        } else {
            XposedHelpers.callMethod(status, "setStatus", svCount, getSvidWithFlags(), snr, elv, azm);
        }
        XposedHelpers.callMethod(status, "setTimeToFirstFix", ttff);
        return status;
    }

    private int[] getSvidWithFlags() {
        if (prn == null) {
            return null;
        }
        int[] svidWithFlags = new int[prn.length];
        int svidShift = 7, consShift = 3;
        for (int i = 0; i < prn.length; i++) {
            if (prn[i] >= 1 && prn[i] <= 32) { // GPS
                svidWithFlags[i] = prn[i];
                svidWithFlags[i] = (svidWithFlags[i] << svidShift) + (GnssStatus.CONSTELLATION_GPS << consShift) + 7;
            } else if (prn[i] >= 65 && prn[i] <= 96) { // GLONASS
                svidWithFlags[i] = prn[i] - 64;
                svidWithFlags[i] = (svidWithFlags[i] << svidShift) + (GnssStatus.CONSTELLATION_GLONASS << consShift) + 7;
            } else if (prn[i] >= 193 && prn[i] <= 200) { // QZSS
                svidWithFlags[i] = prn[i];
                svidWithFlags[i] = (svidWithFlags[i] << svidShift) + (GnssStatus.CONSTELLATION_QZSS << consShift) + 7;
            } else if (prn[i] >= 201 && prn[i] <= 235) { // BeiDou
                svidWithFlags[i] = prn[i] - 200;
                svidWithFlags[i] = (svidWithFlags[i] << svidShift) + (GnssStatus.CONSTELLATION_BEIDOU << consShift) + 7;
            } else if (prn[i] >= 301 && prn[i] <= 336) { // Galileo
                svidWithFlags[i] = prn[i] - 300;
                svidWithFlags[i] = (svidWithFlags[i] << svidShift) + (GnssStatus.CONSTELLATION_GALILEO << consShift) + 7;
            } else {
                svidWithFlags[i] = prn[i];
                svidWithFlags[i] = (svidWithFlags[i] << svidShift) + (GnssStatus.CONSTELLATION_UNKNOWN << consShift) + 7;
            }
        }
        return svidWithFlags;
    }

    private GnssStatus getAsGnssStatus() {
        return (GnssStatus) XposedHelpers.newInstance(GnssStatus.class, svCount, getSvidWithFlags(), snr, elv, azm);
    }

    void addListener(String provider, LocationListener listener) {
        listenerWrappers.add(new ListenerWrapper(provider, listener));
    }

    void removeListener(LocationListener listener) {
        for (ListIterator<ListenerWrapper> itr = listenerWrappers.listIterator(); itr.hasNext(); ) {
            ListenerWrapper wrapper = itr.next();
            if (wrapper.listener != null && wrapper.listener.equals(listener)) {
                itr.remove();
                break;
            }
        }
    }

    void addPendingIntent(String provider, PendingIntent intent) {
        listenerWrappers.add(new ListenerWrapper(provider, intent));
    }

    void removePendingIntent(PendingIntent intent) {
        for (int i = 0; i < listenerWrappers.size(); i++) {
            ListenerWrapper wrapper = listenerWrappers.get(i);
            if (wrapper.listener != null && wrapper.intent.equals(intent)) {
                listenerWrappers.remove(i);
                break;
            }
        }
    }

    private class ListenerWrapper {
        String provider;
        LocationListener listener;
        PendingIntent intent;

        ListenerWrapper(String provider, LocationListener listener) {
            this.provider = provider;
            this.listener = listener;
        }

        ListenerWrapper(String provider, PendingIntent intent) {
            this.provider = provider;
            this.intent = intent;
        }
    }

}
