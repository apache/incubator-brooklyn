package com.cloudsoftcorp.monterey.brooklyn.example

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable

import java.util.Collection
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.event.basic.DependentConfiguration
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
                def server = new MontereyTomcatServer(properties, network)
                server.setConfig(JavaApp.SUGGESTED_JMX_PORT, 32199)
                server.setConfig(JavaWebApp.HTTP_PORT.configKey, 8080)
                server.setConfig(TomcatServer.SUGGESTED_SHUTDOWN_PORT, 31880)
                server.setConfig(AbstractService.ENVIRONMENT, [ CONFIG_DIR : { "${server.setup.runDir}" } ])
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

public class MontereyTomcatServer extends TomcatServer {
    MontereyNetwork network

    public MontereyTomcatServer(Map properties=[:], MontereyNetwork network) {
        super(properties)
        
        this.network = network
    }
    
    public void configure() {
        injectConfig("config.properties", getMontereyProperties(network))
    }

    public String getMontereyProperties(MontereyNetwork network) {
        Task property = DependentConfiguration.attributeWhenReady(network, MontereyNetwork.MANAGEMENT_URL)
        def exec = getExecutionContext()
        exec.submit((Task)property)
        String montereyManagementUrl = property.get()
        """
montereyManagementUrl=${montereyManagementUrl}
montereyUser=guest
montereyPassword=password
montereyLocation=GB-LND
"""
    }
}
