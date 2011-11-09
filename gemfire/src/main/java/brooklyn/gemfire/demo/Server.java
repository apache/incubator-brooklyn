package brooklyn.gemfire.demo;

import com.gemstone.gemfire.cache.*;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Server {
    
    private HttpServer httpServer;
    private int port;

    public Server( int port, HttpHandler handler ) throws IOException {
        this.port = port;
        InetSocketAddress address = new InetSocketAddress(port);
        httpServer = HttpServer.create(address, 0);
        httpServer.createContext("/", handler);
        httpServer.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        httpServer.start();
        System.out.println("Server is listening on port:" +port );
    }

    public void stop() {
        System.out.println("Stopping server");
        httpServer.stop(0);
    }

    static Cache initializeCache( String configFile, String logFile, String licenseFile ) {
        Cache cache = new CacheFactory().set("log-file", logFile)
                                        .set("cache-xml-file", configFile)
                                        .set("mcast-port", "0")
                                        .set("license-file",licenseFile)
                                        .create();
        return cache;
    }

    public static void main(String args[]) throws Exception {

        int port = Integer.parseInt(args[0]);
        String configFile = args[1];
        String logFile = args[2];
        String licenseFile = args[3];

        Cache cache = initializeCache(configFile, logFile, licenseFile);

        HubManager manager = new HubManager(cache);
        HttpHandler handler = new GeneralRequestHandler(manager, manager);
        Server server = new Server(port, handler);
        server.start();
    }

}
