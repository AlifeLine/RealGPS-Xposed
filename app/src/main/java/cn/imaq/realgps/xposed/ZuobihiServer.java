package cn.imaq.realgps.xposed;

import de.robv.android.xposed.XposedBridge;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by adn55 on 2017/2/10.
 */
public class ZuobihiServer {

    private static ServerSocket ssocket;

    public static void start() throws Exception {
        ssocket.close();
        ssocket = new ServerSocket(9244);
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Socket client = ssocket.accept();
                        new Thread(new ServerThread(client)).start();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private static class ServerThread implements Runnable {
        private Socket socket;

        ServerThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                InputStream input = socket.getInputStream();
                byte[] buf = new byte[2048];
                String s = "";
                while (input.read(buf) >= 0) {
                    s += new String(buf);
                }
                XposedBridge.log("Socket: " + s);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
