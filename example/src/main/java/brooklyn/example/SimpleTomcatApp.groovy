package brooklyn.example

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.group.Cluster
import brooklyn.entity.webapp.tomcat.TomcatCluster
import brooklyn.entity.webapp.tomcat.TomcatNode
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.EntityNavigationUtils
import brooklyn.location.basic.SshMachineProvisioner
import brooklyn.location.basic.SshMachine
import com.google.common.base.Preconditions

/**
 * Starts some tomcat nodes, on localhost, using ssh;
 * installs from scratch each time, for now, which may be overkill, but signposts.
 * then dumps JMX stats periodically.
 * 
 * @author alex
 */
public class SimpleTomcatApp extends AbstractApplication {
    Cluster tc = new TomcatCluster(displayName:'MyTomcat', initialSize:3, this);

    public static void main(String[] argv) {
        def app = new SimpleTomcatApp()
        URL resource = SimpleTomcatApp.class.getClassLoader().getResource("hello-world.war")
        Preconditions.checkState resource != null, "Unable to locate resource hello-world.war"
        app.tc.template.war = resource.getPath()
           //TODO:
//        app.tc.policy << new ElasticityPolicy(app.tc, TomcatCluster.REQS_PER_SEC, low:100, high:250);
        app.tc.initialSize = 2  //override initial size

        Collection<InetAddress> hosts = [
            Inet4Address.getByAddress((byte[])[192,168,2,241]),
            Inet4Address.getByAddress((byte[])[192,168,2,242])
        ]
        Collection<SshMachine> machines = hosts.collect { new SshMachine(it, "cloudsoft") }

        EntityNavigationUtils.dump(app, "before start:  ")
        app.tc.start([ new SshMachineLocation(name:'london', provisioner:new SshMachineProvisioner(machines)) ])
        EntityNavigationUtils.dump(app, "after start:  ")

        Thread t = []
        t.start {
            while (!t.isInterrupted()) {
                Thread.sleep 5000
                app.getEntities().each { if (it in TomcatNode) {
                        println ""+it+": "+it.jmxTool?.getChildrenAttributesWithTotal("Catalina:type=GlobalRequestProcessor,name=\"*\"")
                        println "Requests per second: " + it.activity.getValue(TomcatNode.REQUESTS_PER_SECOND)
                        println "Error count: " + it.activity.getValue(TomcatNode.ERROR_COUNT)
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
        app.entities.each { if (it in TomcatNode) it.shutdown() }
        //TODO there is still an executor service running, not doing anything but not marked as a daemon,
        //so doesn't quit immediately (i think it will time out but haven't verified)
        //app shutdown should exist and handle that???

        System.exit(0)
    }

}