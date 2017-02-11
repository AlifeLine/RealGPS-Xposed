package cn.imaq.realgps.xposed;

import android.location.GpsStatus;
import android.os.Build;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by adn55 on 2017/2/10.
 */
public class ZuobihiServer {

    private static ServerSocket ssocket;
    private static Socket csocket;

    private static final Pattern locPattern = Pattern.compile("\\[LOC:(.*?),(.*?),(.*?),(.*?),(.*?),(.*?),(.*?);");
    private static final Pattern satPattern = Pattern.compile("SAT:(.*?),(.*?),(.*?),(.*?);");
    private static final Random rand = new Random();

    private static double lat, lng, alt, speed, bearing, accr, time;
    private static int satNum = 0;
    private static int[] prn = new int[64];
    private static float[] snr = new float[64];
    private static float[] elv = new float[64];
    private static float[] azm = new float[64];
    private static int mask;

    static void start() {
        if (ssocket == null || ssocket.isClosed()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ssocket = new ServerSocket(9244);
                        while (true) {
                            Socket socket = ssocket.accept();
                            // close old connection
                            if (csocket != null) {
                                csocket.close();
                                XposedBridge.log("Zuobihi-Socket: closed old connection");
                            }
                            csocket = socket;
                            new Thread(new ServerThread()).start();
                        }
                    } catch (Throwable t) {
                        t.printStackTrace();
                    }
                }
            }).start();
        }
    }

    static synchronized GpsStatus getGpsStatus() {
        GpsStatus status = (GpsStatus) XposedHelpers.newInstance(GpsStatus.class);
        if (Build.VERSION.SDK_INT >= 24) {

        } else {
            XposedHelpers.callMethod(status, "setStatus", satNum, prn, snr, elv, azm, mask, mask, mask);
            XposedHelpers.callMethod(status, "setTimeToFirstFix", 5 + (rand.nextInt(9) - 4));
        }
        return status;
    }

    private static synchronized boolean parseData(String s) {
        // [LOC:$lat,$lng,$alt,$speed,$bearing,$accr,$time;
        // SAT:${sat.PRN},${sat.snr},${sat.elv},${sat.azm};]
        try {
            s = s.substring(0, s.indexOf(']'));
            Matcher locMatcher = locPattern.matcher(s);
            if (locMatcher.find() && locMatcher.groupCount() == 7) {
                lat = Double.parseDouble(locMatcher.group(1));
                lng = Double.parseDouble(locMatcher.group(2));
                alt = Double.parseDouble(locMatcher.group(3));
                speed = Double.parseDouble(locMatcher.group(4));
                bearing = Double.parseDouble(locMatcher.group(5));
                accr = Double.parseDouble(locMatcher.group(6));
                time = Long.parseLong(locMatcher.group(7));

                satNum = mask = 0;
                Matcher satMatcher = satPattern.matcher(s);
                while (satMatcher.find()) {
                    if (satMatcher.groupCount() == 4) {
                        prn[satNum] = Integer.parseInt(satMatcher.group(1));
                        snr[satNum] = Float.parseFloat(satMatcher.group(2));
                        elv[satNum] = Float.parseFloat(satMatcher.group(3));
                        azm[satNum] = Float.parseFloat(satMatcher.group(4));
                        mask |= (1 << prn[satNum]);
                        satNum++;
                    }
                }
            }
            XposedBridge.log("Zuobihi: Data parsed, containing " + satNum + " satellites");
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static class ServerThread implements Runnable {
        @Override
        public void run() {
            XposedBridge.log("Zuobihi-Socket: open new connection");
            try {
                InputStream input = csocket.getInputStream();
                byte[] buf = new byte[4096];
                String data = "";
                while (true) {
                    int len = input.read(buf);
                    if (len < 0) {
                        break;
                    }
                    String s = new String(buf, 0, len);
                    if (!data.isEmpty() || s.startsWith("[")) {
                        data += s;
                        if (parseData(data) || data.length() > 8192) {
                            data = "";
                        }
                    }
                    // XposedBridge.log("Zuobihi-Socket-Data: " + data);
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
            XposedBridge.log("Zuobihi-Socket: connection closed");
        }
    }

}
