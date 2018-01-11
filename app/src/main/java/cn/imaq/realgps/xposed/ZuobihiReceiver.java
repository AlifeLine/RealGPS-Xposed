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
import de.robv.android.xposed.XposedHelpers;

import java.util.*;

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
    private float[] freq;
    private int mask;

    private Random rand = new Random();
    int ttff = 1000 + rand.nextInt(9000);
    private Bundle gpsExtras = new Bundle();
    private List<ListenerWrapper> listenerWrappers = new LinkedList<>();
    Set<GpsStatus.Listener> gpsListeners = new HashSet<>();
    Set<GnssStatus.Callback> gnssCallbacks = new HashSet<>();

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
        freq = new float[svCount];
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
        int SVID_SHIFT = XposedHelpers.getStaticIntField(GnssStatus.class, "SVID_SHIFT_WIDTH");
        int CONSTELLATION_SHIFT = XposedHelpers.getStaticIntField(GnssStatus.class, "CONSTELLATION_TYPE_SHIFT_WIDTH");
        int[] svidWithFlags = new int[prn.length];
        for (int i = 0; i < prn.length; i++) {
            if (prn[i] >= 1 && prn[i] <= 32) { // GPS
                svidWithFlags[i] = prn[i];
                svidWithFlags[i] = (svidWithFlags[i] << SVID_SHIFT) + (GnssStatus.CONSTELLATION_GPS << CONSTELLATION_SHIFT) + 7;
            } else if (prn[i] >= 65 && prn[i] <= 96) { // GLONASS
                svidWithFlags[i] = prn[i] - 64;
                svidWithFlags[i] = (svidWithFlags[i] << SVID_SHIFT) + (GnssStatus.CONSTELLATION_GLONASS << CONSTELLATION_SHIFT) + 7;
            } else if (prn[i] >= 193 && prn[i] <= 200) { // QZSS
                svidWithFlags[i] = prn[i];
                svidWithFlags[i] = (svidWithFlags[i] << SVID_SHIFT) + (GnssStatus.CONSTELLATION_QZSS << CONSTELLATION_SHIFT) + 7;
            } else if (prn[i] >= 201 && prn[i] <= 235) { // BeiDou
                svidWithFlags[i] = prn[i] - 200;
                svidWithFlags[i] = (svidWithFlags[i] << SVID_SHIFT) + (GnssStatus.CONSTELLATION_BEIDOU << CONSTELLATION_SHIFT) + 7;
            } else if (prn[i] >= 301 && prn[i] <= 336) { // Galileo
                svidWithFlags[i] = prn[i] - 300;
                svidWithFlags[i] = (svidWithFlags[i] << SVID_SHIFT) + (GnssStatus.CONSTELLATION_GALILEO << CONSTELLATION_SHIFT) + 7;
            } else {
                svidWithFlags[i] = prn[i];
                svidWithFlags[i] = (svidWithFlags[i] << SVID_SHIFT) + (GnssStatus.CONSTELLATION_UNKNOWN << CONSTELLATION_SHIFT) + 7;
            }
        }
        return svidWithFlags;
    }

    private GnssStatus getAsGnssStatus() {
        if (Build.VERSION.SDK_INT < 26) {
            return (GnssStatus) XposedHelpers.newInstance(GnssStatus.class, svCount, getSvidWithFlags(), snr, elv, azm);
        } else {
            return (GnssStatus) XposedHelpers.newInstance(GnssStatus.class, svCount, getSvidWithFlags(), snr, elv, azm, freq);
        }
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ListenerWrapper that = (ListenerWrapper) o;

            if (!provider.equals(that.provider)) return false;
            if (listener != null ? !listener.equals(that.listener) : that.listener != null) return false;
            return intent != null ? intent.equals(that.intent) : that.intent == null;
        }

        @Override
        public int hashCode() {
            int result = provider.hashCode();
            result = 31 * result + (listener != null ? listener.hashCode() : 0);
            result = 31 * result + (intent != null ? intent.hashCode() : 0);
            return result;
        }
    }

}
