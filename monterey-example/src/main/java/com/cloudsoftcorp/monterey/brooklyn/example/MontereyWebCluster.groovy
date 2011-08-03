package com.cloudsoftcorp.monterey.brooklyn.example

import static brooklyn.event.basic.DependentConfiguration.*

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable

import java.util.Collection
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.location.Location
import brooklyn.management.Task

import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyNetwork

/**
 * This group contains all the sub-groups and entities that go in to a single location.
 *
 * These are:
 * <ul>
 * <li>a {@link DynamicCluster} of {@link JavaWebApp}s
 * <li>a cluster controller
 * <li>a {@link Policy} to resize the DynamicCluster
 * </ul>
 */
public class MontereyWebCluster extends AbstractEntity implements Startable {
    DynamicWebAppCluster cluster
    NginxController controller

    MontereyWebCluster(Map props=[:], Entity owner=null, MontereyNetwork network) {
        super(props, owner)
        def template = { Map properties ->
                def server = new TomcatServer(properties)
                server.setConfig(JavaApp.SUGGESTED_JMX_PORT, 32199)
                server.setConfig(JavaWebApp.HTTP_PORT.configKey, 8080)
                server.setConfig(TomcatServer.SUGGESTED_SHUTDOWN_PORT, 31880)
		        server.setConfig(TomcatServer.PROPERTY_FILES.subKey("MONTEREY_CONFIG"),
                    [
                        montereyManagementUrl : attributePostProcessedWhenReady(network, MontereyNetwork.MANAGEMENT_URL, { it }, { it.toString() }),
                        montereyUser : attributePostProcessedWhenReady(network, MontereyNetwork.STATUS, { it }, { network.clientCredential.username }),
                        montereyPassword : attributePostProcessedWhenReady(network, MontereyNetwork.STATUS, { it }, { network.clientCredential.password }),
                        montereyLocation : "GB-EDH"
                    ])
                return server;
            }
        cluster = new DynamicWebAppCluster(newEntity:template, this)
        cluster.setConfig(DynamicCluster.INITIAL_SIZE, 0)
    
        setAttribute(SERVICE_UP, false)
    }
    
    void start(Collection<Location> locations) {
        cluster.start(locations)
        cluster.resize(1)

        controller = new NginxController(
            owner : this,
            cluster : cluster,
            domain : "brooklyn.geopaas.org",
            port : 8000,
            portNumberSensor : JavaWebApp.HTTP_PORT,
        )

        controller.start(locations)

        setAttribute(SERVICE_UP, true)
    }

    void stop() {
        controller.stop()
        cluster.stop()

        setAttribute(SERVICE_UP, false)
    }

    void restart() {
        throw new UnsupportedOperationException()
    }
}
