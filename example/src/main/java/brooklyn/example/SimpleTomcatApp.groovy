package brooklyn.example

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.group.Cluster
import brooklyn.entity.webapp.tomcat.TomcatCluster
import brooklyn.entity.webapp.tomcat.TomcatNode
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.EntityNavigationUtils

/**
 * Starts some tomcat nodes, on localhost, using ssh;
 * installs from scratch each time, for now, which may be overkill, but signposts.
 * then dumps JMX stats periodically.
 * 
 * @author alex
 */
public class SimpleTomcatApp extends AbstractApplication {
    Cluster tc = new TomcatCluster(displayName:'MyTomcat', initialSize:3, owner:this);

    public static void main(String[] argv) {
        def app = new SimpleTomcatApp()
        app.tc.war = "resources/hello-world.war"
           //TODO:
//        app.tc.policy << new ElasticityPolicy(app.tc, TomcatCluster.REQS_PER_SEC, low:100, high:250);
        app.tc.initialSize = 2  //override initial size
        
        EntityNavigationUtils.dump(app, "before start:  ")
        app.start location:new SshMachineLocation(name:'london', host:'localhost')
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
