package brooklyn.example

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.group.Cluster
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.jboss.JBossCluster
import brooklyn.entity.webapp.jboss.JBossNode
import brooklyn.location.Location
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation

class ClusteredJBossApp extends AbstractApplication {

    Cluster cluster = new JBossCluster(displayName: "SimpleJBossCluster", initialSize: 2, owner: this)

    public static void main(String[] args) {

        def app = new ClusteredJBossApp()
        app.cluster.setConfig(JBossNode.SUGGESTED_CLUSTER_NAME, "SimpleJBossCluster")
        app.cluster.setConfig(JBossNode.SUGGESTED_SERVER_PROFILE, "all")
        
        Collection<InetAddress> hosts = [
            Inet4Address.getByAddress((byte[]) [192, 168, 144, 245]),
            Inet4Address.getByAddress((byte[]) [192, 168, 144, 246])
        ]
        Collection<SshMachineLocation> machines = hosts.collect {
            new SshMachineLocation(address: it, userName: "cloudsoft")
        }
        Location location = new FixedListMachineProvisioningLocation<SshMachineLocation>(machines: machines, name: "London")

        app.cluster.start([location])

        Thread t = []
        t.start {
            while (!t.isInterrupted()) {
                Thread.sleep 5000
                app.getEntities().each {
                    if (it instanceof JBossNode) {
                        println "${it.toString()}: ${it.getAttribute(JavaWebApp.REQUEST_COUNT)} requests (" +
                                "${it.getAttribute(JavaWebApp.REQUESTS_PER_SECOND)} per second), " +
                                "${it.getAttribute(JavaWebApp.ERROR_COUNT)} errors"
                    }
                }
            }
        }
        
        Thread activity = []
        activity.start {
            def ents = app.getEntities().findAll { it instanceof JBossNode }
            def rand = new Random()
            while (!activity.isInterrupted()) {
                def sleep = 3000
                ents.each {
                    def requests = rand.nextInt(5) + 1
                    if (it.getAttribute(JavaWebApp.NODE_UP)) {
                        URL url = ["http://${it.getAttribute(JavaWebApp.JMX_HOST)}:${it.getAttribute(JavaWebApp.HTTP_PORT)}"]
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

        println "waiting for readln then will kill the cluster"
        System.in.read()
        t.interrupt()
        activity.interrupt()
        
        println "shutting down..."
        app.entities.each { if (it in JavaWebApp) it.stop() }
        System.exit(0)
    }
}
