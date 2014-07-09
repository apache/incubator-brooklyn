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
package brooklyn.extras.whirr;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.http.impl.client.DefaultHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.StringConfigMap;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.extras.whirr.hadoop.WhirrHadoopCluster;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.Location;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.CommandLineUtil;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

/**
 * Starts hadoop and a webapp using hadoop in the location supplied (just one location),
 * configuring the webapp to connect to hadoop
 */
public class WebClusterWithHadoopExample extends AbstractApplication implements StartableApplication {

    private static final Logger log = LoggerFactory.getLogger(WebClusterWithHadoopExample.class);

    static final List<String> DEFAULT_LOCATIONS = ImmutableList.of(
        // note the 1d: we need our machines to be in the same availability zone 
        // so they can see each other on the internal IPs (where hadoop binds)
        // (cross-site magic is shown in the hadoop fabric example)
        // ((also note some availability zones don't work, that's amazon for you...))
        // most other clouds "just work" :)
        "aws-ec2:us-east-1c");

    public static final String WAR_PATH = "classpath://hello-world-hadoop-webapp.war";
    
    private WhirrHadoopCluster hadoopCluster;
    private ControlledDynamicWebAppCluster webCluster;
    private DynamicGroup webVms;
    
    public WebClusterWithHadoopExample() {
    }
    
    @Override
    public void init() {
        StringConfigMap config = getManagementContext().getConfig();
    
        hadoopCluster = addChild(EntitySpec.create(WhirrHadoopCluster.class)
                .configure("size", 2)
                .configure("memory", 2048)
                .configure("name", "Whirr Hadoop Cluster"));

        // TODO Can't just set RECIPE config in spec, because that overwrites defaults in WhirrHadoopCluster!
        // specify hadoop version (1.0.2 has a nice, smaller hadoop client jar)
        hadoopCluster.addRecipeLine("whirr.hadoop.version=1.0.2"); 
        // for this example we'll allow access from anywhere
        hadoopCluster.addRecipeLine("whirr.client-cidrs=0.0.0.0/0");
        hadoopCluster.addRecipeLine("whirr.firewall-rules=8020,8021,50010");
    
        webCluster = addChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("war", WAR_PATH)
                .policy(AutoScalerPolicy.builder()
                        .metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_LAST_PER_NODE)
                        .sizeRange(1, 5)
                        .metricRange(10, 100)
                        .build()));
        
        webVms = addChild(EntitySpec.create(DynamicGroup.class)
                .displayName("Web VMs")
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(JBoss7Server.class)));
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        Iterables.getOnlyElement(locations);
        log.debug("starting "+this);
        super.start(locations);
        
        // start the web cluster, and hadoop cluster, in the single location (above)
        // then register a policy to configure the appservers to use hadoop
        log.debug("started "+this+", now starting policy");
        PrepVmsForHadoop.newPolicyFromGroupToHadoop(webVms, hadoopCluster);
    }
    
    public static class PrepVmsForHadoop extends AbstractPolicy {
        WhirrHadoopCluster hadoopCluster;
        String hadoopSiteXmlContents;
        Set<String> configuredIds = Sets.newLinkedHashSet();
        
        public PrepVmsForHadoop(WhirrHadoopCluster hadoopCluster) {
            this.hadoopCluster = hadoopCluster;
        }
        
        public void start() {
            //read the contents, and disable socks proxy since we're in the same cluster
            try {
                File hadoopSiteXmlFile = new File(System.getProperty("user.home")+"/.whirr/"+
                        hadoopCluster.getClusterSpec().getClusterName()+"/hadoop-site.xml");
                hadoopSiteXmlContents = Files.toString(hadoopSiteXmlFile, Charsets.UTF_8);
                hadoopSiteXmlContents = hadoopSiteXmlContents.replaceAll("<\\?xml.*\\?>\\s*", "");
                hadoopSiteXmlContents = hadoopSiteXmlContents.replaceAll("hadoop.socks.server", "ignore.hadoop.socks.server");
                
                subscribeToMembers((Group)entity, Startable.SERVICE_UP, new SensorEventListener<Boolean>() {
                    @Override public void onEvent(SensorEvent<Boolean> event) {
                        log.debug("hadoop set up policy recieved {}", event);
                        if (event.getValue() != null && !configuredIds.contains(event.getSource().getId())) 
                            setupMachine(event.getSource());
                    }});
            } catch (IOException e) {
                throw Exceptions.propagate(e);
            }
        }
        
        public void setupMachine(Entity e) {
            try {
                if (!e.getAttribute(Startable.SERVICE_UP)) return;
                if (!configuredIds.add(e.getId())) return;
                if (log.isDebugEnabled()) log.debug("setting up machine for hadoop at {}", e);

                URI updateConfigUri = new URI(e.getAttribute(JBoss7Server.ROOT_URL)+
                        "configure.jsp?"+
                        "key=brooklyn.example.hadoop.site.xml.contents"+"&"+
                        "value="+URLEncoder.encode(hadoopSiteXmlContents));
                
                HttpToolResponse result = HttpTool.httpGet(new DefaultHttpClient(), updateConfigUri, ImmutableMap.<String,String>of());
                if (log.isDebugEnabled()) log.debug("http config update for {} got: {}, {}", new Object[] {e, result.getResponseCode(), new String(result.getContent())});
            } catch (Exception err) {
                log.warn("unable to configure "+e+" for hadoop", err);
                configuredIds.remove(e.getId());
            }
        }
        
        public static PrepVmsForHadoop newPolicyFromGroupToHadoop(DynamicGroup target, WhirrHadoopCluster hadoopCluster) {
            log.debug("creating policy for hadoop clusters target {} hadoop ", target, hadoopCluster);
            PrepVmsForHadoop prepVmsForHadoop = new PrepVmsForHadoop(hadoopCluster);
            target.addPolicy(prepVmsForHadoop);
            prepVmsForHadoop.start();
            log.debug("running policy over existing members {}", target.getMembers());
            for (Entity member : target.getMembers()) {
                prepVmsForHadoop.setupMachine(member);
            }
            return prepVmsForHadoop;
        }
    }
        
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", Joiner.on(",").join(DEFAULT_LOCATIONS));

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(StartableApplication.class, WebClusterWithHadoopExample.class)
                        .displayName("Brooklyn Global Web Fabric with Hadoop Example"))
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
}
