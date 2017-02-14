package cn.imaq.realgps.xposed;

import android.app.AndroidAppHelper;
import android.content.Intent;
import de.robv.android.xposed.XposedBridge;

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
    private static final int[] prn = new int[64];
    private static final float[] snr = new float[64];
    private static final float[] elv = new float[64];
    private static final float[] azm = new float[64];
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
                    } catch (Throwable ignored) {
                    }
                }
            }).start();
        }
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
                        mask |= (1 << (prn[satNum] - 1));
                        satNum++;
                    }
                }
            }
            XposedBridge.log("Zuobihi: Data parsed, containing " + satNum + " satellites |" + prn.toString());

            Intent intent = new Intent("cn.imaq.realgps.xposed.UPDATE");
            intent.putExtra("svCount", satNum);
            intent.putExtra("prn", prn);
            intent.putExtra("snr", snr);
            intent.putExtra("elv", elv);
            intent.putExtra("azm", azm);
            intent.putExtra("mask", mask);
            AndroidAppHelper.currentApplication().sendBroadcast(intent);

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
            } catch (Throwable ignored) {
            }
            XposedBridge.log("Zuobihi-Socket: connection closed");
        }
    }

}
