/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.demo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.effector.core.EffectorBody;
import org.apache.brooklyn.effector.core.Effectors;
import org.apache.brooklyn.entity.core.AbstractApplication;
import org.apache.brooklyn.entity.core.Entities;
import org.apache.brooklyn.entity.core.EntityInternal;
import org.apache.brooklyn.entity.core.StartableApplication;
import org.apache.brooklyn.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.entity.nosql.cassandra.CassandraDatacenter;
import org.apache.brooklyn.entity.nosql.cassandra.CassandraFabric;
import org.apache.brooklyn.entity.nosql.cassandra.CassandraNode;
import org.apache.brooklyn.entity.trait.Startable;
import org.apache.brooklyn.entity.webapp.JavaWebAppService;
import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;

import brooklyn.entity.java.UsesJava;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.software.SshEffectorTasks;

import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.location.basic.PortRanges;
import org.apache.brooklyn.policy.ha.ServiceFailureDetector;
import org.apache.brooklyn.policy.ha.ServiceReplacer;
import org.apache.brooklyn.policy.ha.ServiceRestarter;
import org.apache.brooklyn.sensor.core.DependentConfiguration;
import org.apache.brooklyn.util.CommandLineUtil;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.text.TemplateProcessor;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.text.StringEscapes.JavaStringEscapes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.net.HostAndPort;

/** CumulusRDF application with Cassandra cluster. */
@Catalog(name="Cumulus RDF Application", description="CumulusRDF Application on a Tomcat server using a multi-region Cassandra fabric")
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

    @CatalogConfig(label="Multi-region Fabric", priority=1)
    public static final ConfigKey<Boolean> MULTI_REGION_FABRIC = ConfigKeys.newConfigKey(
        "cumulus.cassandra.fabric", "Deploy a multi-region Cassandra fabric", false);

    // TODO Fails when given two locations
    // public static final String DEFAULT_LOCATIONS = "[ jclouds:aws-ec2:us-east-1,jclouds:rackspace-cloudservers-uk ]";
    public static final String DEFAULT_LOCATIONS = "jclouds:aws-ec2:us-east-1";

    private Effector<Void> cumulusConfig = Effectors.effector(Void.class, "cumulusConfig")
            .description("Configure the CumulusRDF web application")
            .buildAbstract();

    private Entity cassandra;
    private TomcatServer webapp;
    private HostAndPort endpoint;
    private final Object endpointMutex = new Object();

    /**
     * Create the application entities:
     * <ul>
     * <li>A {@link CassandraFabric} of {@link CassandraDatacenter}s containing {@link CassandraNode}s
     * <li>A {@link TomcatServer}
     * </ul>
     */
    @Override
    public void initApp() {
        // Cassandra cluster
        EntitySpec<CassandraDatacenter> clusterSpec = EntitySpec.create(CassandraDatacenter.class)
                .configure(CassandraDatacenter.MEMBER_SPEC, EntitySpec.create(CassandraNode.class)
                        //FIXME can probably use JMXMP_AND_RMI now, to deploy to GCE and elsewhere
                        .configure(UsesJmx.JMX_AGENT_MODE, UsesJmx.JmxAgentModes.JMX_RMI_CUSTOM_AGENT)
                        .configure(UsesJmx.JMX_PORT, PortRanges.fromString("11099+"))
                        .configure(UsesJmx.RMI_REGISTRY_PORT, PortRanges.fromString("9001+"))
                        .configure(CassandraNode.THRIFT_PORT, PortRanges.fromInteger(getConfig(CASSANDRA_THRIFT_PORT)))
                        .enricher(EnricherSpec.create(ServiceFailureDetector.class))
                        .policy(PolicySpec.create(ServiceRestarter.class)
                                .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, ServiceFailureDetector.ENTITY_FAILED)))
                .policy(PolicySpec.create(ServiceReplacer.class)
                        .configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, ServiceRestarter.ENTITY_RESTART_FAILED));

        if (getConfig(MULTI_REGION_FABRIC)) {
            cassandra = addChild(EntitySpec.create(CassandraFabric.class)
                    .configure(CassandraDatacenter.CLUSTER_NAME, "Brooklyn")
                    .configure(CassandraDatacenter.INITIAL_SIZE, getConfig(CASSANDRA_CLUSTER_SIZE)) // per location
                    .configure(CassandraDatacenter.ENDPOINT_SNITCH_NAME, "org.apache.brooklyn.entity.nosql.cassandra.customsnitch.MultiCloudSnitch")
                    .configure(CassandraNode.CUSTOM_SNITCH_JAR_URL, "classpath://org/apache/brooklyn/entity/nosql/cassandra/cassandra-multicloud-snitch.jar")
                    .configure(CassandraFabric.MEMBER_SPEC, clusterSpec));
        } else {
            cassandra = addChild(EntitySpec.create(clusterSpec)
                    .configure(CassandraDatacenter.CLUSTER_NAME, "Brooklyn")
                    .configure(CassandraDatacenter.INITIAL_SIZE, getConfig(CASSANDRA_CLUSTER_SIZE)));
        }

        // Tomcat web-app server
        webapp = addChild(EntitySpec.create(TomcatServer.class)
                .configure(UsesJmx.JMX_AGENT_MODE, UsesJmx.JmxAgentModes.JMX_RMI_CUSTOM_AGENT)
                .configure(UsesJmx.JMX_PORT, PortRanges.fromString("11099+"))
                .configure(UsesJmx.RMI_REGISTRY_PORT, PortRanges.fromString("9001+"))
                .configure(JavaWebAppService.ROOT_WAR, "https://cumulusrdf.googlecode.com/svn/wiki/downloads/cumulusrdf-1.0.1.war")
                .configure(UsesJava.JAVA_SYSPROPS, MutableMap.of("cumulusrdf.config-file", "/tmp/cumulus.yaml")));

        // Add an effector to tomcat to reconfigure with a new YAML config file
        ((EntityInternal) webapp).getMutableEntityType().addEffector(cumulusConfig, new EffectorBody<Void>() {
            @Override
            public Void call(ConfigBag parameters) {
                // Process the YAML template given in the application config
                String url = Entities.getRequiredUrlConfig(CumulusRDFApplication.this, CUMULUS_RDF_CONFIG_URL);
                Map<String, Object> config;
                synchronized (endpointMutex) {
                    config = MutableMap.<String, Object>of("cassandraHostname", endpoint.getHostText(), "cassandraThriftPort", endpoint.getPort());
                }
                String contents = TemplateProcessor.processTemplateContents(ResourceUtils.create(CumulusRDFApplication.this).getResourceAsString(url), config);
                // Copy the file contents to the remote machine
                return DynamicTasks.queue(SshEffectorTasks.put("/tmp/cumulus.yaml").contents(contents)).get();
            }
        });

        // Listen for HOSTNAME changes from the Cassandra fabric to show at least one node is available
        subscribe(cassandra, CassandraDatacenter.HOSTNAME, new SensorEventListener<String>() {
            @Override
            public void onEvent(SensorEvent<String> event) {
                if (Strings.isNonBlank(event.getValue())) {
                    synchronized (endpointMutex) {
                        String hostname = Entities.submit(CumulusRDFApplication.this, DependentConfiguration.attributeWhenReady(cassandra, CassandraDatacenter.HOSTNAME)).getUnchecked();
                        Integer thriftPort = Entities.submit(CumulusRDFApplication.this, DependentConfiguration.attributeWhenReady(cassandra, CassandraDatacenter.THRIFT_PORT)).getUnchecked();
                        HostAndPort current = HostAndPort.fromParts(hostname, thriftPort);

                        // Check if the cluster access point has changed
                        if (!current.equals(endpoint)) {
                            log.info("Setting cluster endpoint to {}", current.toString());
                            endpoint = current;

                            // Reconfigure the CumulusRDF application and restart tomcat if necessary
                            webapp.invoke(cumulusConfig, MutableMap.<String, Object>of());
                            if (webapp.getAttribute(Startable.SERVICE_UP)) {
                                webapp.restart();
                            }
                        }
                    }
                }
            }
        });
    }

    /**
     * Controls the startup locations for the webapp and the cassandra fabric.
     *
     * @see AbstractApplication#start(Collection)
     */
    @Override
    public void start(Collection<? extends Location> locations) {
        addLocations(locations);

        // The web application only needs to run in one location, use the first
        // TODO use a multi-region web cluster
        Collection<? extends Location> first = MutableList.copyOf(Iterables.limit(locations, 1));

        ServiceStateLogic.setExpectedState(this, Lifecycle.STARTING);
        try {
            Entities.invokeEffector(this, cassandra, Startable.START, MutableMap.of("locations", locations)).getUnchecked();
            Entities.invokeEffector(this, webapp, Startable.START, MutableMap.of("locations", first)).getUnchecked();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        } finally {
            ServiceStateLogic.setExpectedState(this, Lifecycle.RUNNING);
        }
        log.info("Started CumulusRDF in " + locations);
    }


    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String locations = CommandLineUtil.getCommandLineOption(args, "--locations", DEFAULT_LOCATIONS);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(StartableApplication.class, CumulusRDFApplication.class).displayName("CumulusRDF application using Cassandra"))
                .webconsolePort(port)
                .locations(Strings.isBlank(locations) ? ImmutableList.<String>of() : JavaStringEscapes.unwrapJsonishListIfPossible(locations))
                .start();

        Entities.dumpInfo(launcher.getApplications());
    }
}
