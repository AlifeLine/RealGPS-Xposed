package cn.imaq.realgps.xposed;

import android.app.PendingIntent;
import android.location.*;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import com.alibaba.fastjson.JSON;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by adn55 on 2017/2/9.
 */
public class XposedHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpParam) throws Throwable {
        XposedBridge.log("Load package: " + lpParam.packageName + " by " + lpParam.processName);
        hookTest(lpParam, "addGpsStatusListener", GpsStatus.Listener.class);
        hookTest(lpParam, "getAllProviders");
        hookTest(lpParam, "getBestProvider", Criteria.class, boolean.class);
        hookTest(lpParam, "getGpsStatus", GpsStatus.class);
        hookTest(lpParam, "getLastKnownLocation", String.class);
        hookTest(lpParam, "getProvider", String.class);
        hookTest(lpParam, "getProviders", Criteria.class, boolean.class);
        hookTest(lpParam, "getProviders", boolean.class);
        hookTest(lpParam, "isProviderEnabled", String.class);
        hookTest(lpParam, "requestLocationUpdates", String.class, long.class, float.class, LocationListener.class);
        hookTest(lpParam, "requestLocationUpdates", long.class, float.class, Criteria.class, LocationListener.class, Looper.class);
        hookTest(lpParam, "requestLocationUpdates", long.class, float.class, Criteria.class, PendingIntent.class);
        hookTest(lpParam, "requestLocationUpdates", String.class, long.class, float.class, LocationListener.class, Looper.class);
        hookTest(lpParam, "requestLocationUpdates", String.class, long.class, float.class, PendingIntent.class);
        hookTest(lpParam, "requestSingleUpdate", String.class, PendingIntent.class);
        hookTest(lpParam, "requestSingleUpdate", String.class, LocationListener.class, Looper.class);
        hookTest(lpParam, "requestSingleUpdate", Criteria.class, PendingIntent.class);
        hookTest(lpParam, "requestSingleUpdate", Criteria.class, LocationListener.class, Looper.class);
        hookTest(lpParam, "sendExtraCommand", String.class, String.class, Bundle.class);
        if (Build.VERSION.SDK_INT >= 24) {
            hookTest(lpParam, "addNmeaListener", OnNmeaMessageListener.class, Handler.class);
            hookTest(lpParam, "registerGnssMeasurementsCallback", GnssMeasurementsEvent.Callback.class, Handler.class);
            hookTest(lpParam, "registerGnssNavigationMessageCallback", GnssNavigationMessage.Callback.class, Handler.class);
            hookTest(lpParam, "registerGnssStatusCallback", GnssStatus.Callback.class, Handler.class);
        }
    }

    private void hookTest(final XC_LoadPackage.LoadPackageParam lpParam, final String methodName, Object... paramTypes) {
        try {
            Object[] typesCallback = new Object[paramTypes.length + 1];
            System.arraycopy(paramTypes, 0, typesCallback, 0, paramTypes.length);
            typesCallback[paramTypes.length] = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log(methodName + " called by " + lpParam.packageName +
                            "(args: " + JSON.toJSONString(param.args) + ", result: " + JSON.toJSONString(param.getResultOrThrowable()) + ")");
                }
            };
            XposedHelpers.findAndHookMethod(LocationManager.class, methodName, typesCallback);
        } catch (Throwable ignored) {
        }
    }
}
