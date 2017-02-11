package cn.imaq.realgps.xposed;

import android.location.GpsStatus;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
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

    private static double lat, lng, alt, speed, bearing, accr, time;
    private static ArrayList<ZuobihiSatellite> satsMain = new ArrayList<>();
    private static ArrayList<ZuobihiSatellite> satsBack = new ArrayList<>();
    private static boolean usingSatsMain = true;

    private static GpsStatus gpsStatus = (GpsStatus) XposedHelpers.newInstance(GpsStatus.class);

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

    private static boolean parseData(String s) {
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

                int satNum = 0;
                ArrayList<ZuobihiSatellite> sats = getSatsListForUpdate();
                Matcher satMatcher = satPattern.matcher(s);
                while (satMatcher.find()) {
                    if (satMatcher.groupCount() == 4) {
                        satNum++;
                        while (sats.size() < satNum) {
                            sats.add(new ZuobihiSatellite());
                        }
                        ZuobihiSatellite sat = sats.get(satNum - 1);
                        sat.valid = true;
                        sat.PRN = Integer.parseInt(satMatcher.group(1));
                        sat.snr = Float.parseFloat(satMatcher.group(2));
                        sat.elv = Float.parseFloat(satMatcher.group(3));
                        sat.azm = Float.parseFloat(satMatcher.group(4));
                    }
                }
                for (int i = satNum; i < sats.size(); i++) {
                    sats.get(i).valid = false;
                }
                usingSatsMain = !usingSatsMain;
            }
            XposedBridge.log("Zuobihi: Data parsed, satsMain: " + satsMain.size() + ", satsBack: " + satsBack.size() + ", now using " + (usingSatsMain ? "satsMain" : "satsBack"));
            return true;
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static ArrayList<ZuobihiSatellite> getSatsListForUpdate() {
        return usingSatsMain ? satsMain : satsBack;
    }

    static ArrayList<ZuobihiSatellite> getSatsListForStatus() {
        return usingSatsMain ? satsBack : satsMain;
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
