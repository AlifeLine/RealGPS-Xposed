package cn.imaq.realgps.xposed;

import android.app.AndroidAppHelper;
import android.app.PendingIntent;
import android.content.Context;
import android.location.*;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.ServerSocket;
import java.util.*;

/**
 * Created by adn55 on 2017/2/9.
 */
public class HookPackage implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpParam) throws Throwable {
        // XposedBridge.log("Load package: " + lpParam.packageName + " by " + lpParam.processName);
        XSharedPreferences pref = new XSharedPreferences(BuildConfig.APPLICATION_ID);

        // Try to start server
        Context context = AndroidAppHelper.currentApplication();
        if (context != null) {
            try {
                int port = Integer.parseInt(pref.getString("global_port", "9244"));
                new ZuobihiServer(context, new ServerSocket(port));
                XposedBridge.log("ZuobihiServer started in process " + lpParam.processName);
            } catch (Throwable ignored) {
            }
        }

        // Check if app is enabled
        Set<String> appList = pref.getStringSet("perapp_list", Collections.<String>emptySet());
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
                context.registerReceiver(ZuobihiReceiver.getInstance(), ZuobihiReceiver.intentFilter);
                XposedBridge.log("Registered receiver for " + lpParam.packageName);
            }
        });

        // ========== HOOKS ==========
        HashMap<String, XC_MethodHook> hooks = new HashMap<>();

        // Providers related
        XC_MethodHook providerListXC = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                List list = (List) param.getResult();
                if (!list.contains(LocationManager.GPS_PROVIDER)) {
                    list.add(LocationManager.GPS_PROVIDER);
                    param.setResult(list);
                }
            }
        };
        hooks.put("getAllProviders", providerListXC);
        hooks.put("getProviders", providerListXC);
        hooks.put("getBestProvider", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(LocationManager.GPS_PROVIDER);
            }
        });
        hooks.put("getProvider", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // For devices without GPS
                if (param.args[0].equals(LocationManager.GPS_PROVIDER)) {
                    try {
                        // For Android 4.2+
                        Object prop = XposedHelpers.newInstance(
                                XposedHelpers.findClass("com.android.internal.location.ProviderProperties", null),
                                false, true, false, false, true, true, true, Criteria.POWER_HIGH, Criteria.ACCURACY_HIGH
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
                        XposedHelpers.callMethod(provider, "setPowerRequirement", Criteria.POWER_HIGH);
                        XposedHelpers.callMethod(provider, "setAccuracy", Criteria.ACCURACY_HIGH);
                        param.setResult(provider);
                    }
                }
            }
        });
        hooks.put("isProviderEnabled", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0].equals(LocationManager.GPS_PROVIDER)) {
                    param.setResult(true);
                } else if (param.args[0].equals(LocationManager.NETWORK_PROVIDER)) {
                    param.setResult(false);
                }
            }
        });

        // GPS related
        hooks.put("addGpsStatusListener", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                GpsStatus.Listener listener = (GpsStatus.Listener) param.args[0];
                if (!ZuobihiReceiver.getInstance().gpsListeners.contains(listener)) {
                    listener.onGpsStatusChanged(GpsStatus.GPS_EVENT_STARTED);
                    listener.onGpsStatusChanged(GpsStatus.GPS_EVENT_FIRST_FIX);
                    ZuobihiReceiver.getInstance().gpsListeners.add(listener);
                }
                param.setResult(true);
            }
        });
        hooks.put("removeGpsStatusListener", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                ZuobihiReceiver.getInstance().gpsListeners.remove(param.args[0]);
                param.setResult(null);
            }
        });
        hooks.put("getGpsStatus", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(ZuobihiReceiver.getInstance().getAsGpsStatus((GpsStatus) param.args[0]));
            }
        });

        // GNSS (Nougat) related
        hooks.put("registerGnssStatusCallback", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                GnssStatus.Callback callback = (GnssStatus.Callback) param.args[0];
                if (!ZuobihiReceiver.getInstance().gnssCallbacks.contains(callback)) {
                    callback.onStarted();
                    callback.onFirstFix(ZuobihiReceiver.getInstance().ttff);
                    ZuobihiReceiver.getInstance().gnssCallbacks.add(callback);
                }
                param.setResult(true);
            }
        });
        hooks.put("unregisterGnssStatusCallback", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                ZuobihiReceiver.getInstance().gnssCallbacks.remove(param.args[0]);
                param.setResult(null);
            }
        });
        XC_MethodHook returnZero = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(0);
            }
        };
        hooks.put("getGnssYearOfHardware", returnZero);
        hooks.put("getGnssBatchSize", returnZero);

        // Location related
        hooks.put("getLastLocation", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(ZuobihiReceiver.getInstance().getAsLocation(LocationManager.GPS_PROVIDER));
            }
        });
        hooks.put("getLastKnownLocation", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(ZuobihiReceiver.getInstance().getAsLocation((String) param.args[0]));
            }
        });
        hooks.put("requestLocationUpdates", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String provider = LocationManager.GPS_PROVIDER;
                if (param.args[0] instanceof String) {
                    provider = (String) param.args[0];
                }
                for (int i = 1; i < param.args.length; i++) {
                    if (param.args[i] instanceof LocationListener) {
                        ((LocationListener) param.args[i]).onProviderEnabled(provider);
                        ZuobihiReceiver.getInstance().addListener(provider, (LocationListener) param.args[i]);
                        break;
                    } else if (param.args[i] instanceof PendingIntent) {
                        // TODO ZuobihiReceiver.getInstance().addPendingIntent(provider, (PendingIntent) param.args[i]);
                        break;
                    }
                }
                param.setResult(null);
            }
        });
        hooks.put("requestSingleUpdate", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                String provider = LocationManager.GPS_PROVIDER;
                if (param.args[0] instanceof String) {
                    provider = (String) param.args[0];
                }
                if (param.args[1] instanceof LocationListener) {
                    ((LocationListener) param.args[1]).onLocationChanged(ZuobihiReceiver.getInstance().getAsLocation(provider));
                } else if (param.args[1] instanceof PendingIntent) {
                    // TODO PendingIntent
                }
                param.setResult(null);
            }
        });
        hooks.put("removeUpdates", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0] instanceof LocationListener) {
                    ZuobihiReceiver.getInstance().removeListener((LocationListener) param.args[0]);
                } else if (param.args[0] instanceof PendingIntent) {
                    // TODO ZuobihiReceiver.getInstance().removePendingIntent((PendingIntent) param.args[0]);
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
        XC_MethodHook returnTrue = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(true);
            }
        };
        XC_MethodHook returnFalse = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(false);
            }
        };
        hooks.put("addNmeaListener", returnTrue);
        hooks.put("removeNmeaListener", returnNull);
        hooks.put("addProximityAlert", returnNull);
        hooks.put("removeProximityAlert", returnNull);
        hooks.put("addGeofence", returnNull);
        hooks.put("removeGeofence", returnNull);
        hooks.put("removeAllGeofences", returnNull);
        hooks.put("addGpsMeasurementListener", returnTrue);
        hooks.put("removeGpsMeasurementListener", returnNull);
        hooks.put("addGpsNavigationMessageListener", returnTrue);
        hooks.put("removeGpsNavigationMessageListener", returnNull);
        hooks.put("sendNiResponse", returnTrue);
        hooks.put("sendExtraCommand", returnTrue);
        // Nougat
        hooks.put("registerGnssMeasurementsCallback", returnTrue);
        hooks.put("unregisterGnssMeasurementsCallback", returnNull);
        hooks.put("registerGnssNavigationMessageCallback", returnTrue);
        hooks.put("unregisterGnssNavigationMessageCallback", returnNull);
        hooks.put("registerGnssBatchedLocationCallback", returnFalse);
        hooks.put("unregisterGnssBatchedLocationCallback", returnFalse);
        hooks.put("flushGnssBatch", returnNull);

        for (Method method : LocationManager.class.getDeclaredMethods()) {
            XC_MethodHook hook = hooks.get(method.getName());
            if (hook != null && Modifier.isPublic(method.getModifiers())) {
                XposedBridge.hookMethod(method, hook);
            }
        }

        // Forbid network scanning
        if (pref.getBoolean("network_forbid", false) && containsBAT(lpParam.classLoader)) {
            XposedBridge.log(lpParam.packageName + " contains BAT, hooking WLAN/Cellular scanning");
            XC_MethodHook returnEmptyList = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(Collections.emptyList());
                }
            };
            XposedHelpers.findAndHookMethod(WifiManager.class, "startScan", returnTrue);
            XposedHelpers.findAndHookMethod(WifiManager.class, "getScanResults", returnEmptyList);
            XposedHelpers.findAndHookMethod(TelephonyManager.class, "getCellLocation", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(CellLocation.getEmpty());
                }
            });
            XposedHelpers.findAndHookMethod(TelephonyManager.class, "getNeighboringCellInfo", returnEmptyList);
            XposedHelpers.findAndHookMethod(TelephonyManager.class, "listen", PhoneStateListener.class, int.class, returnNull);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                XposedHelpers.findAndHookMethod(TelephonyManager.class, "getAllCellInfo", returnEmptyList);
            }
        }
    }

    private boolean containsBAT(ClassLoader loader) {
        try {
            XposedHelpers.findClass("com.amap.api.location.AMapLocationClient", loader);
            return true;
        } catch (Exception ignored) {
        }
        try {
            XposedHelpers.findClass("com.baidu.location.LocationClient", loader);
            return true;
        } catch (Exception ignored) {
        }
        try {
            XposedHelpers.findClass("com.tencent.map.geolocation.TencentLocationRequest", loader);
            return true;
        } catch (Exception ignored) {
        }
        return false;
    }

}
