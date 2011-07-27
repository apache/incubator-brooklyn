package brooklyn.example

import java.util.concurrent.TimeUnit

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.location.Location
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.policy.ResizerPolicy
import brooklyn.util.internal.Repeater

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
            ts.setConfig(JavaWebApp.HTTP_PORT.configKey, 8080)
            ts.setConfig(TomcatServer.WAR, resource.getPath())
            return ts;
        },
        owner:this);

    public static void main(String[] argv) {
        def app = new SimpleTomcatApp()
        
        app.tc.initialSize = 2  //override initial size

        ResizerPolicy p = new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND)
        p.setMinSize(2).setMaxSize(4).setMetricLowerBound(20).setMetricUpperBound(50)
        app.tc.addPolicy p
        
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

        def nginx = new NginxController([
            "owner" : app,
            "cluster" : app.tc,
            "domain" : "localhost",
            "port" : 8000,
            "portNumberSensor" : TomcatServer.HTTP_PORT,
        ])
        nginx.start([ new LocalhostMachineProvisioningLocation(count:1) ])
  
        Thread t = []
        t.start({
            boolean activityShutdown = false;
            new Repeater("Activity logger")
                .repeat({
                    app.tc.members.each {
                        if (it in TomcatServer) {
                            if (it.getAttribute(AbstractService.SERVICE_UP)) {
                                println "${it.toString()}: ${it.getAttribute(JavaWebApp.REQUEST_COUNT)} requests (" +
                                        "${it.getAttribute(JavaWebApp.REQUESTS_PER_SECOND)} per second), " +
                                        "${it.getAttribute(JavaWebApp.ERROR_COUNT)} errors"
                            } else {
                                println "${it.toString()} status: ${it.getAttribute(AbstractService.SERVICE_STATUS)}, " +
                                        "node up: ${it.getAttribute(AbstractService.SERVICE_UP)}"
                            }
                        }
                    }
                    println "Cluster stats: ${app.tc.getAttribute(DynamicWebAppCluster.TOTAL_REQUEST_COUNT)} requests " +
                            "(${app.tc.getAttribute(DynamicWebAppCluster.TOTAL_REQUESTS_PER_SECOND)} per second), " +
                            "average ${app.tc.getAttribute(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT)} per entity"
                })
                .every(5, TimeUnit.SECONDS)
                .until({ activityShutdown })
                .run()
        })
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
//        activityShutdown = true

        println "shutting down..."
        app.tc.stop()
        nginx.stop()

        //TODO there is still an executor service running, not doing anything but not marked as a daemon,
        //so doesn't quit immediately (i think it will time out but haven't verified)
        //app shutdown should exist and handle that???

        System.exit(0)
    }

}
