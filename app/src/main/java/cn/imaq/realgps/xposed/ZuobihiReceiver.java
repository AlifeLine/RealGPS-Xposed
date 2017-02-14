package cn.imaq.realgps.xposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.GpsStatus;
import android.os.Build;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.util.Random;

/**
 * Created by adn55 on 2017/2/15.
 */
public class ZuobihiReceiver extends BroadcastReceiver {

    private static double lat, lng, alt, speed, bearing, accr, time;
    private static int svCount;
    private static int[] prn;
    private static float[] snr;
    private static float[] elv;
    private static float[] azm;
    private static int mask;

    private static ZuobihiReceiver _instance;
    private static Random rand = new Random();
    static final IntentFilter intentFilter = new IntentFilter("cn.imaq.realgps.xposed.UPDATE");

    static ZuobihiReceiver getInstance() {
        if (_instance == null) {
            _instance = new ZuobihiReceiver();
        }
        return _instance;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        svCount = intent.getIntExtra("svCount", 0);
        prn = intent.getIntArrayExtra("prn");
        snr = intent.getFloatArrayExtra("snr");
        elv = intent.getFloatArrayExtra("elv");
        azm = intent.getFloatArrayExtra("azm");
        mask = intent.getIntExtra("mask", 0);
        XposedBridge.log("ZuobihiReceiver: received " + svCount + " satellites");
    }

    static synchronized GpsStatus getGpsStatus() {
        GpsStatus status = (GpsStatus) XposedHelpers.newInstance(GpsStatus.class);
        if (Build.VERSION.SDK_INT >= 24) {

        } else {
            XposedBridge.log("getGpsStatus: " + svCount + "satellites");
            XposedHelpers.callMethod(status, "setStatus", svCount, prn, snr, elv, azm, mask, mask, mask);
            XposedHelpers.callMethod(status, "setTimeToFirstFix", 5 + (rand.nextInt(9) - 4));
        }
        return status;
    }

}
