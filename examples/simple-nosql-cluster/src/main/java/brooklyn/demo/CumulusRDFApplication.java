/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.demo;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.nosql.cassandra.CassandraCluster;
import brooklyn.entity.nosql.cassandra.CassandraNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.webapp.tomcat.TomcatServer;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.util.CommandLineUtil;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.text.TemplateProcessor;

import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;

/** CumulusRDF application with Cassandra cluster. */
public class CumulusRDFApplication extends AbstractApplication {

    private static final Logger log = LoggerFactory.getLogger(CumulusRDFApplication.class);

    @CatalogConfig(label="Cumulus Configuration File (URL)", priority=1)
    public static final ConfigKey<String> CUMULUS_RDF_CONFIG_URL = ConfigKeys.newConfigKey(
        "cumulus.config.url", "URL for the YAML configuration file for CumulusRDF", "classpath://cumulus.yaml");

    @CatalogConfig(label="Cassandra Thrift Port", priority=1)
    public static final ConfigKey<Integer> CASSANDRA_THRIFT_PORT = ConfigKeys.newConfigKey(
        "cumulus.cassandra.port", "Port to contact the Cassandra cluster on", 9160);

    @CatalogConfig(label="Cassandra Cluster Size", priority=1)
    public static final ConfigKey<Integer> CASSANDRA_CLUSTER_SIZE = ConfigKeys.newConfigKey(
        "cumulus.cassandra.cluster.size", "Initial size of the Cassandra cluster", 2);

    public static final String DEFAULT_LOCATION = "aws-ec2:eu-west-1";

    private Effector<Void> cumulusConfig = Effectors.effector(Void.class, "cumulusConfig")
            .description("Configure the CumulusRDF web application")
            .buildAbstract();

    private CassandraCluster cassandra;
    private TomcatServer tomcat;

    /** Create entities. */
    public void init() {
        // The cassandra cluster entity
        cassandra = addChild(EntitySpec.create(CassandraCluster.class)
                .configure("initialSize", getConfig(CASSANDRA_CLUSTER_SIZE))
                .configure("clusterName", "CumulusRDF")
                .configure("memberSpec", EntitySpec.create(CassandraNode.class)
                        .configure("jmxAgentMode", UsesJmx.JmxAgentModes.JMX_RMI_CUSTOM_AGENT)
                        .configure("jmxPort", "11099+")
                        .configure("rmiServerPort", "9001+")
                        .configure("thriftPort", getConfig(CASSANDRA_THRIFT_PORT))));

        // The tomcat server entity
        tomcat = addChild(EntitySpec.create(TomcatServer.class)
                .configure("version", "7.0.42")
                .configure("jmxAgentMode", UsesJmx.JmxAgentModes.JMX_RMI_CUSTOM_AGENT)
                .configure("jmxPort", "11099+")
                .configure("rmiServerPort", "9001+")
                .configure("war", "classpath://cumulusrdf.war")
                .configure("javaSysProps", MutableMap.of("cumulusrdf.config-file", "/tmp/cumulus.yaml")));

        // Add an effector to tomcat to reconfigure with a new YAML config file
        ((EntityInternal) tomcat).getMutableEntityType().addEffector(cumulusConfig, new EffectorBody<Void>() {
            private HostAndPort clusterEndpoint;

            @Override
            public Void call(ConfigBag parameters) {
                String hostname = cassandra.getAttribute(CassandraCluster.HOSTNAME);
                Integer thriftPort = cassandra.getAttribute(CassandraCluster.THRIFT_PORT);
                HostAndPort currentEndpoint = HostAndPort.fromParts(hostname, thriftPort);

                // Check if the cluster access point has changed
                if (!currentEndpoint.equals(clusterEndpoint)) {
                    log.info("Setting cluster endpoint to {}", currentEndpoint.toString());
                    clusterEndpoint = currentEndpoint;

                    // Process the YAML template given in the application config
                    String url = Entities.getRequiredUrlConfig(CumulusRDFApplication.this, CUMULUS_RDF_CONFIG_URL);
                    Map<String, Object> config = MutableMap.<String, Object>of("cassandraHostname", clusterEndpoint.getHostText(), "cassandraThriftPort", clusterEndpoint.getPort());
                    String contents = TemplateProcessor.processTemplateContents(new ResourceUtils(this).getResourceAsString(url), config);

                    // Copy the file contents to the remote machine
                    DynamicTasks.queue(SshEffectorTasks.put("/tmp/cumulus.yaml").contents(contents));
                }

                return null;
            }
        });

        subscribe(cassandra, CassandraCluster.HOSTNAME, new SensorEventListener<String>() {
            @Override
            public void onEvent(SensorEvent<String> event) {
                // Reconfigure the CumulusRDF application and restart tomcat if necessary
                tomcat.invoke(cumulusConfig, MutableMap.<String, Object>of());
                if (tomcat.getAttribute(Startable.SERVICE_UP)) {
                    tomcat.restart();
                }
            }
        });
    }

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(StartableApplication.class, CumulusRDFApplication.class).displayName("CumulusRDF application with Cassandra cluster"))
                .webconsolePort(port)
                .location(location)
                .start();

        Entities.dumpInfo(launcher.getApplications());
    }
}
