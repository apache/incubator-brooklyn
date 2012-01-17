package brooklyn.util;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class NetworkUtils {

    public static final int MIN_PORT_NUMBER = 1;
    public static final int MAX_PORT_NUMBER = 65535;
    
    public static boolean isPortAvailable(int port) {
        if (port < MIN_PORT_NUMBER || port > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException("Invalid start port: " + port);
        }
        try {
            Socket s = new Socket(InetAddress.getByName("localhost"), port);
            try {
                s.close();
            } catch (Exception e) {}
            return false;
        } catch (Exception e) {
            //expected - shouldn't be able to connect
        }
        //despite http://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
        //(recommending the following) it isn't 100% reliable (e.g. nginx will happily coexist with ss+ds)
        //so we also do the above check
        ServerSocket ss = null;
        DatagramSocket ds = null;
        try {
            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            ds = new DatagramSocket(port);
            ds.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (ds != null) {
                ds.close();
            }

            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException e) {
                    /* should not be thrown */
                }
            }
        }
    }
    
}
