package cn.imaq.realgps.xposed;

import android.location.*;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import com.alibaba.fastjson.JSON;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.List;

/**
 * Created by adn55 on 2017/2/9.
 */
public class HookPackage implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpParam) throws Throwable {
        //XposedBridge.log("Load package: " + lpParam.packageName + " by " + lpParam.processName);
        ZuobihiServer.start();

        // Providers related
        XC_MethodHook providersXC = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                List list = (List) param.getResult();
                if (!list.contains(LocationManager.GPS_PROVIDER)) {
                    list.add(LocationManager.GPS_PROVIDER);
                    param.setResult(list);
                }
            }
        };
        XposedHelpers.findAndHookMethod(LocationManager.class, "getAllProviders", providersXC);
        XposedHelpers.findAndHookMethod(LocationManager.class, "getProviders", Criteria.class, boolean.class, providersXC);
        XposedHelpers.findAndHookMethod(LocationManager.class, "getProviders", boolean.class, providersXC);
        XposedHelpers.findAndHookMethod(LocationManager.class, "getBestProvider", Criteria.class, boolean.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(LocationManager.GPS_PROVIDER);
            }
        });
        XposedHelpers.findAndHookMethod(LocationManager.class, "getProvider", String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0].equals(LocationManager.GPS_PROVIDER)) {
                    // TODO no ways yet
                    // param.setResult(null);
                }
            }
        });
        XposedHelpers.findAndHookMethod(LocationManager.class, "isProviderEnabled", String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0].equals(LocationManager.GPS_PROVIDER)) {
                    param.setResult(true);
                }
            }
        });

        // GPS related
        XposedHelpers.findAndHookMethod(LocationManager.class, "addGpsStatusListener", GpsStatus.Listener.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                // TODO add listener
            }
        });
        XposedHelpers.findAndHookMethod(LocationManager.class, "getGpsStatus", GpsStatus.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // TODO return satellites
                GpsStatus status = (GpsStatus) XposedHelpers.newInstance(GpsStatus.class);
                if (Build.VERSION.SDK_INT >= 24) {

                } else {

                }
            }
        });
        XposedHelpers.findAndHookMethod(LocationManager.class, "sendExtraCommand", String.class, String.class, Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(true);
            }
        });

        // Location related
        XposedHelpers.findAndHookMethod(LocationManager.class, "getLastKnownLocation", String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // TODO return location
            }
        });

//        hookTest(lpParam, "requestLocationUpdates", String.class, long.class, float.class, LocationListener.class);
//        hookTest(lpParam, "requestLocationUpdates", long.class, float.class, Criteria.class, LocationListener.class, Looper.class);
//        hookTest(lpParam, "requestLocationUpdates", long.class, float.class, Criteria.class, PendingIntent.class);
//        hookTest(lpParam, "requestLocationUpdates", String.class, long.class, float.class, LocationListener.class, Looper.class);
//        hookTest(lpParam, "requestLocationUpdates", String.class, long.class, float.class, PendingIntent.class);
//        hookTest(lpParam, "requestSingleUpdate", String.class, PendingIntent.class);
//        hookTest(lpParam, "requestSingleUpdate", String.class, LocationListener.class, Looper.class);
//        hookTest(lpParam, "requestSingleUpdate", Criteria.class, PendingIntent.class);
//        hookTest(lpParam, "requestSingleUpdate", Criteria.class, LocationListener.class, Looper.class);

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
