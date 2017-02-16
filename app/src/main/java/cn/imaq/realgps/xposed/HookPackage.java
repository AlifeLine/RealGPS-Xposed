package cn.imaq.realgps.xposed;

import android.app.AndroidAppHelper;
import android.app.PendingIntent;
import android.content.Context;
import android.location.*;
import android.os.Build;
import android.os.Bundle;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.net.ServerSocket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by adn55 on 2017/2/9.
 */
public class HookPackage implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpParam) throws Throwable {
        // XposedBridge.log("Load package: " + lpParam.packageName + " by " + lpParam.processName);

        // Try to start server
        Context context = AndroidAppHelper.currentApplication();
        if (context != null) {
            try {
                new ZuobihiServer(context, new ServerSocket(9244));
                XposedBridge.log("ZuobihiServer started in process " + lpParam.processName);
            } catch (Throwable ignored) {
            }
        }

        // Check if app is enabled
        XSharedPreferences pref = new XSharedPreferences(BuildConfig.APPLICATION_ID);
        Set<String> appList = pref.getStringSet("perapp_list", new HashSet<String>());
        if (!pref.getBoolean("global_switch", true) ||
                (pref.getBoolean("perapp_switch", false) &&
                        !appList.contains(lpParam.packageName) && !appList.contains(lpParam.processName))) {
            return;
        }

        // Register broadcast receiver
        XposedBridge.hookAllConstructors(LocationManager.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Context context = AndroidAppHelper.currentApplication();
                if (context == null) {
                    context = (Context) param.args[0];
                }
                context.registerReceiver(
                        ZuobihiReceiver.getInstance(),
                        ZuobihiReceiver.intentFilter
                );
                XposedBridge.log("Registered receiver for " + lpParam.packageName);
            }
        });

        HashMap<String, XC_MethodHook> hooks = new HashMap<>(16);

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
        XposedHelpers.findAndHookMethod(LocationManager.class, "getProviders", providersXC);
        XposedBridge.hookAllMethods(LocationManager.class, "getProviders", providersXC);
        XposedHelpers.findAndHookMethod(LocationManager.class, "getBestProvider", Criteria.class, boolean.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(LocationManager.GPS_PROVIDER);
            }
        });
        XposedHelpers.findAndHookMethod(LocationManager.class, "getProvider", String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                // For devices without GPS
                if (param.args[0].equals(LocationManager.GPS_PROVIDER) && param.getResult() == null) {
                    try {
                        // For Android 4.2+
                        Object prop = XposedHelpers.newInstance(
                                XposedHelpers.findClass("com.android.internal.location.ProviderProperties", null),
                                false, true, false, false, true, true, true, Criteria.POWER_MEDIUM, Criteria.ACCURACY_HIGH
                        );
                        param.setResult(XposedHelpers.newInstance(LocationProvider.class, LocationManager.GPS_PROVIDER, prop));
                    } catch (Throwable t) {
                        // For Android 4.2-
                        Object provider = XposedHelpers.newInstance(
                                XposedHelpers.findClass("com.android.internal.location.DummyLocationProvider", null),
                                LocationManager.GPS_PROVIDER, XposedHelpers.getObjectField(param.thisObject, "mService")
                        );
                        XposedHelpers.callMethod(provider, "setRequiresNetwork", false);
                        XposedHelpers.callMethod(provider, "setRequiresSatellite", true);
                        XposedHelpers.callMethod(provider, "setRequiresCell", false);
                        XposedHelpers.callMethod(provider, "setHasMonetaryCost", false);
                        XposedHelpers.callMethod(provider, "setSupportsAltitude", true);
                        XposedHelpers.callMethod(provider, "setSupportsSpeed", true);
                        XposedHelpers.callMethod(provider, "setSupportsBearing", true);
                        XposedHelpers.callMethod(provider, "setPowerRequirement", Criteria.POWER_MEDIUM);
                        XposedHelpers.callMethod(provider, "setAccuracy", Criteria.ACCURACY_HIGH);
                        param.setResult(provider);
                    }
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
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                ZuobihiReceiver.getInstance().gpsListeners.add((GpsStatus.Listener) param.args[0]);
                param.setResult(true);
            }
        });
        XposedHelpers.findAndHookMethod(LocationManager.class, "removeGpsStatusListener", GpsStatus.Listener.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                ZuobihiReceiver.getInstance().gpsListeners.remove(param.args[0]);
                param.setResult(null);
            }
        });
        XposedHelpers.findAndHookMethod(LocationManager.class, "getGpsStatus", GpsStatus.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(ZuobihiReceiver.getInstance().getAsGpsStatus());
            }
        });

        // Location related
        XposedHelpers.findAndHookMethod(LocationManager.class, "getLastKnownLocation", String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(ZuobihiReceiver.getInstance().getAsLocation((String) param.args[0]));
            }
        });
        XposedBridge.hookAllMethods(LocationManager.class, "requestLocationUpdates", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                for (int i = 1; i < param.args.length; i++) {
                    if (param.args[i] instanceof LocationListener) {
                        XposedBridge.log(lpParam.packageName + " REQ_LISTENER " + param.args[i].getClass().getCanonicalName());
                        ZuobihiReceiver.getInstance().locationListeners.add((LocationListener) param.args[i]);
                        break;
                    } else if (param.args[i] instanceof PendingIntent) {
                        // TODO PendingIntent
                        break;
                    }
                }
                param.setResult(null);
            }
        });
        XposedBridge.hookAllMethods(LocationManager.class, "requestSingleUpdate", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[1] instanceof LocationListener) {
                    String provider = LocationManager.GPS_PROVIDER;
                    if (param.args[0] instanceof String) {
                        provider = (String) param.args[0];
                    }
                    ((LocationListener) param.args[1]).onLocationChanged(ZuobihiReceiver.getInstance().getAsLocation(provider));
                } else if (param.args[1] instanceof PendingIntent) {
                    // TODO PendingIntent
                }
                param.setResult(null);
            }
        });
        XposedBridge.hookAllMethods(LocationManager.class, "removeUpdates", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0] instanceof LocationListener) {
                    ZuobihiReceiver.getInstance().locationListeners.remove(param.args[0]);
                } else if (param.args[0] instanceof PendingIntent) {
                    // TODO PendingIntent
                }
                param.setResult(null);
            }
        });

        // No use
        XC_MethodHook returnNull = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(null);
            }
        };
        XposedBridge.hookAllMethods(LocationManager.class, "addNmeaListener", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(false);
            }
        });
        XposedBridge.hookAllMethods(LocationManager.class, "removeNmeaListener", returnNull);
        XposedHelpers.findAndHookMethod(LocationManager.class, "addProximityAlert", double.class, double.class, float.class, long.class, PendingIntent.class, returnNull);
        XposedHelpers.findAndHookMethod(LocationManager.class, "removeProximityAlert", PendingIntent.class, returnNull);
        XposedBridge.hookAllMethods(LocationManager.class, "addGeofence", returnNull);
        XposedHelpers.findAndHookMethod(LocationManager.class, "sendExtraCommand", String.class, String.class, Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(true);
            }
        });

        if (Build.VERSION.SDK_INT >= 24) {
//            hookTest(lpParam, "addNmeaListener", OnNmeaMessageListener.class, Handler.class);
//            hookTest(lpParam, "registerGnssMeasurementsCallback", GnssMeasurementsEvent.Callback.class, Handler.class);
//            hookTest(lpParam, "registerGnssNavigationMessageCallback", GnssNavigationMessage.Callback.class, Handler.class);
//            hookTest(lpParam, "registerGnssStatusCallback", GnssStatus.Callback.class, Handler.class);
        }
    }

}
