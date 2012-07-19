package brooklyn.extras.whirr

import groovy.transform.InheritConstructors

import java.io.File
import java.net.InetAddress
import java.util.List

import org.apache.log4j.lf5.util.StreamUtils;
import org.apache.whirr.service.hadoop.HadoopCluster
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.basic.Entities
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.adapter.HttpResponseContext
import brooklyn.extras.whirr.hadoop.WhirrHadoopCluster
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry
import brooklyn.location.basic.SshMachineLocation
import brooklyn.management.Task
import brooklyn.policy.autoscaling.AutoScalerPolicy
import brooklyn.policy.basic.AbstractPolicy
import brooklyn.util.CommandLineUtil
import brooklyn.util.ResourceUtils;

import com.google.common.base.Charsets
import com.google.common.collect.Iterables
import com.google.common.io.Files

/**
 * Starts hadoop and a webapp using hadoop in the location supplied (just one location),
 * configuring the webapp to connect to hadoop
 */
@InheritConstructors
public class WebClusterWithHadoopExample extends AbstractApplication {

    private static final Logger log = LoggerFactory.getLogger(WebClusterWithHadoopExample.class);

    static final List<String> DEFAULT_LOCATIONS = [
        // note the 1d: we need our machines to be in the same availability zone 
        // so they can see each other on the internal IPs (where hadoop binds)
        // (cross-site magic is shown in the hadoop fabric example)
        // ((also note some availability zones don't work, that's amazon for you...))
        // most other clouds "just work" :)
        "aws-ec2:us-east-1d",
    ];

    public static final String WAR_PATH = "classpath://hello-world-hadoop-webapp.war";
            
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()
    
    WhirrHadoopCluster hadoopCluster = new WhirrHadoopCluster(this, size: 2, memory: 2048, name: "Whirr Hadoop Cluster");
    {
        // specify hadoop version (1.0.2 has a nice, smaller hadoop client jar)
        hadoopCluster.addRecipeLine("whirr.hadoop.version=1.0.2"); 
        // for this example we'll allow access from anywhere
        hadoopCluster.addRecipeLine("whirr.client-cidrs=0.0.0.0/0");
        hadoopCluster.addRecipeLine("whirr.firewall-rules=8020,8021,50010");
    }
    
    ControlledDynamicWebAppCluster webCluster = new ControlledDynamicWebAppCluster(this, war: WAR_PATH);
    {
        webCluster.addPolicy(AutoScalerPolicy.builder()
                .metric(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND)
                .sizeRange(1, 5)
                .metricRange(10, 100)
                .build())
    }
        
    DynamicGroup webVms = new DynamicGroup(this, name: "Web VMs", { it in JBoss7Server });
    
    void start(Collection locations) {
        Iterables.getOnlyElement(locations);
        log.debug("starting "+this);
        super.start(locations);
        
        // start the web cluster, and hadoop cluster, in the single location (above)
        // then register a policy to configure the appservers to use hadoop
        log.debug("started "+this+", now starting policy");
        PrepVmsForHadoop.newPolicyFromGroupToHadoop(webVms, hadoopCluster);
    }
    
    public static class PrepVmsForHadoop extends AbstractPolicy {
        private static final Logger log = LoggerFactory.getLogger(WebClusterWithHadoopExample.class);
        
        WhirrHadoopCluster hadoopCluster;
        String hadoopSiteXmlContents;
        Set<String> configuredIds = [];
        
        public PrepVmsForHadoop(WhirrHadoopCluster hadoopCluster) {
            this.hadoopCluster = hadoopCluster;
        }
        
        public void start() {
            //read the contents, and disable socks proxy since we're in the same cluster
            hadoopSiteXmlContents = new File("${System.getProperty('user.home')}/.whirr/"+
                hadoopCluster.clusterSpec.clusterName+"/hadoop-site.xml").text
            hadoopSiteXmlContents = hadoopSiteXmlContents.replaceAll("<\\?xml.*\\?>\\s*", "");
            hadoopSiteXmlContents = hadoopSiteXmlContents.replaceAll("hadoop.socks.server", "ignore.hadoop.socks.server");
            
            subscriptionTracker.subscribeToMembers(entity, Startable.SERVICE_UP, 
                { SensorEvent evt -> 
                    log.debug "hadoop set up policy recieved {}", evt
                    if (evt.value && !configuredIds.contains(evt.source.id)) 
                        setupMachine(evt.source) 
                } as SensorEventListener);
        }
        
        public void setupMachine(Entity e) {
            try {
                if (!e.getAttribute(Startable.SERVICE_UP)) return;
                if (!configuredIds.add(e.id)) return;
                if (log.isDebugEnabled()) log.debug "setting up machine for hadoop at {}", e

                URL updateConfig = new URL(e.getAttribute(JBoss7Server.ROOT_URL)+
                        "configure.jsp?"+
                        "key=brooklyn.example.hadoop.site.xml.contents"+"&"+
                        "value="+URLEncoder.encode(hadoopSiteXmlContents));
                def result = new HttpResponseContext(updateConfig.openConnection());
                if (log.isDebugEnabled()) log.debug "http config update for {} got: {}", e, result.content
            } catch (Exception err) {
                log.warn "unable to configure {} for hadoop: {}", e, err
                configuredIds.remove(e.id);
            }
        }
        
        public static PrepVmsForHadoop newPolicyFromGroupToHadoop(DynamicGroup target, WhirrHadoopCluster hadoopCluster) {
            log.debug "creating policy for hadoop clusters target {} hadoop ", target, hadoopCluster
            PrepVmsForHadoop prepVmsForHadoop = new PrepVmsForHadoop(hadoopCluster);
            target.addPolicy(prepVmsForHadoop);
            prepVmsForHadoop.start();
            log.debug "running policy over existing members {}", target.members
            target.members.each { prepVmsForHadoop.setupMachine(it) }
            return prepVmsForHadoop;
        }
    }
        
    public static void main(String[] argv) {
        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = new LocationRegistry().getLocationsById(args ?: DEFAULT_LOCATIONS)
        log.info("starting WebClusterWithHadoop, locations {}, mgmt on port {}", locations, port)

        WebClusterWithHadoopExample app = new WebClusterWithHadoopExample(name: 'Brooklyn Global Web Fabric with Hadoop Example');
            
        BrooklynLauncher.manage(app, port)
        app.start(locations)
        Entities.dumpInfo(app)
    }
    
}
