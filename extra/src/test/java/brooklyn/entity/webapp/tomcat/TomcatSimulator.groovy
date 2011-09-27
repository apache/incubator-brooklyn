package brooklyn.entity.webapp.tomcat

import static org.testng.Assert.*

import java.util.concurrent.Semaphore

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.location.Location
import brooklyn.test.JmxService

/**
 * A class that simulates Tomcat for the purposes of testing.
 */
public class TomcatSimulator {
    private static final int MAXIMUM_LOCKS = 1
    private static final Logger LOG = LoggerFactory.getLogger(brooklyn.entity.webapp.tomcat.TomcatServerIntegrationTest.class)
    
    private static Semaphore lock = new Semaphore(MAXIMUM_LOCKS)
    private static Collection<TomcatSimulator> activeInstances = []
    private Location location
    private EntityLocal entity
    private JmxService jmxService
    Thread httpServerThread

    TomcatSimulator(Location location, EntityLocal entity) {
        assertNotNull(location)
        assertNotNull(entity)
        this.location = location
        this.entity = entity
    }

    public void start() {
        if (lock.tryAcquire() == false)
            throw new IllegalStateException("TomcatSimulator is already running")
        synchronized (activeInstances) { activeInstances.add(this) }

        jmxService = new JmxService();

        int httpPort = new Random().nextInt(1000) + 7000
        jmxService.registerMBean "Catalina:type=Connector,port="+httpPort, stateName: "STARTED"
        jmxService.registerMBean "Catalina:type=GlobalRequestProcessor,name=http-"+httpPort,
            errorCount: 0,
            requestCount: 0,
            processingTime: 0

        entity.setAttribute(TomcatServer.HTTP_PORT, httpPort)
        entity.setAttribute(TomcatServer.HOSTNAME, jmxService.jmxHost)
        entity.setAttribute(TomcatServer.JMX_PORT, jmxService.jmxPort)
        entity.setAttribute(TomcatServer.JMX_CONTEXT, "jmxrmi")

        final ServerSocket server
        try {
            server = new ServerSocket(httpPort)
        } catch (Exception e) {
            LOG.warn "Unable to start HTTP server on ${httpPort}", e
            server = null
        }

        httpServerThread = new Thread(){
            @Override void run() {
                while(true) {
                    Socket socket = server.accept()
                    socket << "HTTP/1.0 200 OK\r\n"
                    socket << "\r\n"
                    socket << "\r\n"
                    socket.close()
                }
            }
        }
        httpServerThread.start()
    }

    public void shutdown() {
        httpServerThread.stop()
        if (jmxService) jmxService.shutdown();
        jmxService = null;
        synchronized (activeInstances) { activeInstances.remove(this) }
        lock.release()
    }

    Location getLocation() { return location }

    static boolean reset() {
        boolean wasFree = true;
        Collection<TomcatSimulator> copyActiveInstances;
        synchronized (activeInstances) { copyActiveInstances = new ArrayList(activeInstances) }
        copyActiveInstances.each { wasFree = false; it.shutdown(); }
        return wasFree
    }
}
