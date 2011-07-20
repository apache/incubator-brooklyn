package brooklyn.example

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.jboss.JBossServer
import brooklyn.location.Location
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation

class ClusteredJBossApp extends AbstractApplication {

    DynamicWebAppCluster cluster = new DynamicWebAppCluster(displayName: "SimpleJBossCluster", initialSize: 1, 
        newEntity: { properties -> new JBossServer(properties) }, owner: this)

    public static void main(String[] args) {

        def app = new ClusteredJBossApp()
        app.cluster.setConfig(JBossServer.SUGGESTED_CLUSTER_NAME, "SimpleJBossCluster")
        app.cluster.setConfig(JBossServer.SUGGESTED_SERVER_PROFILE, "all")
        
        Collection<InetAddress> hosts = [
            Inet4Address.getByAddress((byte[]) [192, 168, 144, 243]),
            Inet4Address.getByAddress((byte[]) [192, 168, 144, 244]),
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
                    if (it instanceof JBossServer) {
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
                println "Cluster stats: ${app.cluster.getAttribute(DynamicWebAppCluster.TOTAL_REQUEST_COUNT)} requests, " + 
                        "average ${app.cluster.getAttribute(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT)} per entity"
            }
        }
        
        Thread activity = []
        activity.start {
            def rand = new Random()
            def sleep = 3000
            while (!activity.isInterrupted()) {
                def ents = app.getEntities().findAll { it instanceof JBossServer }
                ents.each {
                    def requests = rand.nextInt(5) + 1
                    if (it.getAttribute(AbstractService.SERVICE_UP)) {
                        URL url = ["http://${it.getAttribute(JavaApp.JMX_HOST)}:${it.getAttribute(JavaWebApp.HTTP_PORT)}"]
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

        println "waiting for readln then will resize the cluster"
        int input = System.in.read()
        
        if (input != 'q') {
            println "Resizing cluster"
            app.cluster.resize(2)
            println "waiting for readln then will kill the cluster"
            System.in.read()
        }
                
        // Reducing not currently supported
        // println "waiting for readln then will resize the cluster"
        // System.in.read()
        // app.cluster.resize(1)
                
        t.interrupt()
        activity.interrupt()
        
        println "shutting down..."
        app.entities.each { if (it in JavaWebApp) it.stop() }
        System.exit(0)
    }
}
