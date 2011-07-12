package brooklyn.example

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.group.Cluster
import brooklyn.entity.webapp.tomcat.TomcatCluster
import brooklyn.entity.webapp.tomcat.TomcatNode

import brooklyn.location.basic.SshMachineLocation
import com.google.common.base.Preconditions
import brooklyn.location.basic.GeneralPurposeLocation
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.Location
import brooklyn.entity.group.DynamicCluster

/**
 * Starts some tomcat nodes, on localhost, using ssh;
 * installs from scratch each time, for now, which may be overkill, but signposts.
 * then dumps JMX stats periodically.
 * 
 * @author alex
 */
public class SimpleTomcatApp extends AbstractApplication {
    DynamicCluster tc = new DynamicCluster(displayName:'MyTomcat', initialSize: 1,
        newEntity: {
            def tc = new TomcatNode()
            URL resource = SimpleTomcatApp.class.getClassLoader().getResource("hello-world.war")
            Preconditions.checkState resource != null, "Unable to locate resource hello-world.war"
            tc.setConfig(TomcatNode.WAR, resource.getPath())
            return tc;
        },
        owner: this);

    public static void main(String[] argv) {
        def app = new SimpleTomcatApp()
           //TODO:
//        app.tc.policy << new ElasticityPolicy(app.tc, TomcatCluster.REQS_PER_SEC, low:100, high:250);
        app.tc.initialSize = 2  //override initial size

        Collection<InetAddress> hosts = [
            Inet4Address.getByAddress((byte[])[192,168,144,241]),
            Inet4Address.getByAddress((byte[])[192,168,144,242]),
            Inet4Address.getByAddress((byte[])[192,168,144,243]),
            Inet4Address.getByAddress((byte[])[192,168,144,244]),
            Inet4Address.getByAddress((byte[])[192,168,144,245]),
            Inet4Address.getByAddress((byte[])[192,168,144,246])
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
                app.getEntities().each { if (it in TomcatNode) {
                        println it.toString()
                        println "Requests per second: " + it.getAttribute(TomcatNode.REQUESTS_PER_SECOND)
                        println "Error count: " + it.getAttribute(TomcatNode.ERROR_COUNT)
                    }
                }
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

        //TODO find a better way to shutdown a cluster?
        println "shutting down..."
        app.entities.each { if (it in TomcatNode) it.stop() }
        //TODO there is still an executor service running, not doing anything but not marked as a daemon,
        //so doesn't quit immediately (i think it will time out but haven't verified)
        //app shutdown should exist and handle that???

        System.exit(0)
    }

}