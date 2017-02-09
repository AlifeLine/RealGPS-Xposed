package cn.imaq.realgps.xposed;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;

/**
 * Created by adn55 on 2017/2/10.
 */
public class HookInit implements IXposedHookZygoteInit {

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        XposedBridge.log("initZygote");
        ZuobihiServer.start();
        XposedBridge.log("RealGPS for Xposed started.");
    }

}
