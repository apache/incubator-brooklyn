package brooklyn.example

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.location.Location
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation

import com.google.common.base.Preconditions

/**
 * Starts some tomcat nodes, on localhost, using ssh;
 * installs from scratch each time, for now, which may be overkill, but signposts.
 * then dumps JMX stats periodically.
 * 
 * @author alex
 */
public class SimpleTomcatApp extends AbstractApplication {
    DynamicWebAppCluster tc = new DynamicWebAppCluster(displayName:'MyTomcat', initialSize:1,
    newEntity:{ properties ->
        def ts = new TomcatServer(properties)
        URL resource = SimpleTomcatApp.class.getClassLoader().getResource("hello-world.war")
        Preconditions.checkState resource != null, "Unable to locate resource hello-world.war"
        ts.setConfig(TomcatServer.WAR, resource.getPath())
        return ts;
    },
    owner:this);

    public static void main(String[] argv) {
        def app = new SimpleTomcatApp()
        //TODO:
        //        app.tc.policy << new ElasticityPolicy(app.tc, TomcatCluster.REQS_PER_SEC, low:100, high:250);
        app.tc.initialSize = 2  //override initial size

        Collection<InetAddress> hosts = [
            Inet4Address.getByAddress((byte[])[192, 168, 144, 241]),
            Inet4Address.getByAddress((byte[])[192, 168, 144, 242]),
            Inet4Address.getByAddress((byte[])[192, 168, 144, 243]),
            Inet4Address.getByAddress((byte[])[192, 168, 144, 244]),
            Inet4Address.getByAddress((byte[])[192, 168, 144, 245]),
            Inet4Address.getByAddress((byte[])[192, 168, 144, 246])
        ]
        Collection<SshMachineLocation> machines = hosts.collect {
            new SshMachineLocation(address: it, userName: "cloudsoft")
        }
        Location location = new FixedListMachineProvisioningLocation<SshMachineLocation>(machines: machines, name: "London")

        app.tc.start([location])

        Thread t = []
        t.start {
            while (!t.isInterrupted()) {
                Thread.sleep 5000
                app.getEntities().each {
                    if (it in TomcatServer) {
                        println "${it.toString()}: ${it.getAttribute(JavaWebApp.REQUEST_COUNT)} requests (" +
                                "${it.getAttribute(JavaWebApp.REQUESTS_PER_SECOND)} per second), " +
                                "${it.getAttribute(JavaWebApp.ERROR_COUNT)} errors"
                    }
                }
                println "Cluster stats: ${app.tc.getAttribute(DynamicWebAppCluster.REQUEST_COUNT)} requests, " +
                        "average ${app.tc.getAttribute(DynamicWebAppCluster.REQUEST_AVERAGE)} per entity"
            }
        }

        Thread activity = []
        activity.start {
            def rand = new Random()
            def sleep = 3000
            while (!activity.isInterrupted()) {
                def ents = app.getEntities().findAll { it instanceof TomcatServer }
                ents.each {
                    def requests = rand.nextInt(5) + 1
                    if (it.getAttribute(AbstractService.SERVICE_UP)) {
                        URL url = [
                            "http://${it.getAttribute(JavaApp.JMX_HOST)}:${it.getAttribute(JavaWebApp.HTTP_PORT)}"
                        ]
                        println "Making $requests requests to $url"
                        requests.times {
                            try {
                                URLConnection connection = url.openConnection()
                                connection.connect()
                                connection.getContentLength()
                            } catch (Exception e) {
                                // Suppress exceptions caused by shutdown
                            }
                        }
                    }
                }
                Thread.sleep sleep
            }
        }

//        println "launching a groovy shell, with 'app' set"
//        IO io = new IO()
//        def code = 0;
//        // Add a hook to display some status when shutting down...
//        addShutdownHook {
//            //
//            // FIXME: We need to configure JLine to catch CTRL-C for us... Use gshell-io's InputPipe
//            //
//
//            if (code == null) {
//                // Give the user a warning when the JVM shutdown abnormally, normal shutdown
//                // will set an exit code through the proper channels
//
//                io.err.println()
//                io.err.println('@|red WARNING:|@ Abnormal JVM shutdown detected')
//            }
//
//            io.flush()
//        }
//
//        // set up the shell
//        // TODO it insists on using ANSI which is a bother
//        Binding b = new Binding()
//        b.setVariable 'app', app
//        Groovysh shell = new Groovysh(b, io)
//
//        SecurityManager psm = System.getSecurityManager()
//        System.setSecurityManager(new NoExitSecurityManager())
//
//        try {
//            code = shell.run(new String[0])
//        } finally {
//            System.setSecurityManager(psm)
//        }

        println "waiting for readln then will kill the tomcats"
        System.in.read()
        t.interrupt()
        activity.interrupt()

        //TODO find a better way to shutdown a cluster?
        println "shutting down..."
        app.entities.each { if (it in TomcatServer) it.stop() }
        //TODO there is still an executor service running, not doing anything but not marked as a daemon,
        //so doesn't quit immediately (i think it will time out but haven't verified)
        //app shutdown should exist and handle that???

        System.exit(0)
    }

}
