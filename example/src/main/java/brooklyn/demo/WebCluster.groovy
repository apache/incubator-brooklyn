package brooklyn.demo

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
import brooklyn.policy.Policy
import brooklyn.policy.ResizerPolicy

import com.google.common.base.Preconditions

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
public class WebCluster extends AbstractEntity implements Startable {
   private static final String springTravelPath
   private static final String warName = "swf-booking-mvc.war"

   private DynamicWebAppCluster cluster
   private NginxController controller
   private Policy policy

   static {
       URL resource = WebCluster.class.getClassLoader().getResource(warName)
       Preconditions.checkState resource != null, "Unable to locate resource $warName"
       springTravelPath = resource.getPath()
   }

   WebCluster(Map props, Entity owner = null) {
       super(props, owner)
       def template = { Map properties ->
               def server = new TomcatServer(properties)
               server.setConfig(JavaApp.SUGGESTED_JMX_PORT, 32199)
               server.setConfig(JavaWebApp.HTTP_PORT.configKey, 8080)
               server.setConfig(TomcatServer.SUGGESTED_SHUTDOWN_PORT, 31880)
               server.setConfig(JavaWebApp.WAR, springTravelPath)
               return server;
           }
       cluster = new DynamicWebAppCluster(newEntity:template, this)
       cluster.setConfig(DynamicCluster.INITIAL_SIZE, 0)
   }

   // FIXME: why am I implementing these?
   void start(Collection<? extends Location> locations) {
       cluster.start(locations)
       cluster.resize(1)
       
       controller = new NginxController(
           owner:this,
           cluster:cluster,
           domain:'cloudsoft.geopaas.org',
           port:8000,
           portNumberSensor:JavaWebApp.HTTP_PORT
       )

       policy = new ResizerPolicy(JavaWebApp.AVG_REQUESTS_PER_SECOND)
       policy.setMinSize(1)
       policy.setMaxSize(5)
       policy.setMetricLowerBound(10)
       policy.setMetricUpperBound(100)
       policy.setEntity(cluster)

       controller.start(locations)
   }
   void stop() {
       controller.stop()
       cluster.stop()
   }
   void restart() {
       throw new UnsupportedOperationException()
   }
}
