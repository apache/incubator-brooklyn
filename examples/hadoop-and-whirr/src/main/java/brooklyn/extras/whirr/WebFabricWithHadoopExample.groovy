package brooklyn.extras.whirr

import groovy.transform.InheritConstructors

import org.apache.whirr.service.hadoop.HadoopCluster
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.basic.DynamicGroupImpl
import brooklyn.entity.basic.Entities
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.group.DynamicFabricImpl
import brooklyn.entity.proxy.AbstractController
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.ElasticJavaWebAppService
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.adapter.HttpResponseContext
import brooklyn.event.basic.DependentConfiguration
import brooklyn.extras.cloudfoundry.CloudFoundryJavaWebAppCluster
import brooklyn.extras.whirr.hadoop.WhirrHadoopCluster
import brooklyn.extras.whirr.hadoop.WhirrHadoopClusterImpl
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry
import brooklyn.location.basic.SshMachineLocation
import brooklyn.management.Task
import brooklyn.policy.basic.AbstractPolicy
import brooklyn.util.CommandLineUtil
import brooklyn.util.task.ParallelTask

import com.google.common.base.Charsets
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.google.common.io.Files

/**
 * Starts hadoop in the first location supplied, and the hadoop-friendly webapp in all other locations.
 * Webapp get configured via the configure.jsp page, plus supplying the proxy command, to connect to hadoop.
 */
@InheritConstructors
public class WebFabricWithHadoopExample extends AbstractApplication {

    private static final Logger log = LoggerFactory.getLogger(WebFabricWithHadoopExample.class);

    static final List<String> DEFAULT_LOCATIONS = [
        // hadoop location
        "aws-ec2:eu-west-1",
          
        //web locations
        "aws-ec2:eu-west-1",
        "aws-ec2:ap-southeast-1",
        "aws-ec2:us-west-1",
        
        // cloudfoundry seems to have a timeout in upload time
        // (in any case we don't have a clean way to initiate the proxy settings in there)
//        "cloudfoundry:https://api.aws.af.cm/",
    ];

    public static final String WAR_PATH = "classpath://hello-world-hadoop-webapp.war";
            
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()
    
    WhirrHadoopCluster hadoopCluster = new WhirrHadoopClusterImpl(this, size: 2, memory: 2048, name: "Whirr Hadoop Cluster");
    { hadoopCluster.addRecipeLine("whirr.hadoop.version=1.0.2"); }
    
    DynamicFabric webFabric = new DynamicFabricImpl(this, name: "Web Fabric", factory: new ElasticJavaWebAppService.Factory());
    
    GeoscalingDnsService geoDns = new GeoscalingDnsService(this, name: "GeoScaling DNS",
            username: config.getFirst("brooklyn.geoscaling.username", failIfNone:true),
            password: config.getFirst("brooklyn.geoscaling.password", failIfNone:true),
            primaryDomainName: config.getFirst("brooklyn.geoscaling.primaryDomain", failIfNone:true),
            smartSubdomainName: 'brooklyn');
    
    {
        //specify the WAR file to use
        webFabric.setConfig(ElasticJavaWebAppService.ROOT_WAR, WAR_PATH);
        //load-balancer instances must run on 80 to work with GeoDNS (default is 8000)
        webFabric.setConfig(AbstractController.PROXY_HTTP_PORT, 80);
        //CloudFoundry requires to be told what URL it should listen to, which is chosen by the GeoDNS service
        webFabric.setConfig(CloudFoundryJavaWebAppCluster.HOSTNAME_TO_USE_FOR_URL,
            DependentConfiguration.attributeWhenReady(geoDns, Attributes.HOSTNAME));

        //tell GeoDNS what to monitor
        geoDns.setTargetEntityProvider(webFabric);
    }

    DynamicGroup webVms = new DynamicGroupImpl(this, name: "Web VMs", { it in JBoss7Server });
    
    void start(Collection locations) {
        Location hadoopLocation = Iterables.getFirst(locations, null);
        if (hadoopLocation==null) throw new IllegalStateException("location required to start $this");
        // start hadoop in first, web in others (unless there is just one location supplied)
        List<Location> webLocations = Lists.newArrayList(Iterables.skip(locations, 1)) ?: [hadoopLocation];

        Task starts = executionContext.submit(new ParallelTask(        
                {   webFabric.start(webLocations)  },
                {   hadoopCluster.start([hadoopLocation]); 
                    // collect the hadoop-site.xml and feed it to all existing and new appservers,
                    // and start the proxies there
                    PrepVmsForHadoop.newPolicyFromGroupToHadoop(webVms, hadoopCluster);
                } ));
        starts.blockUntilEnded();
	}    
    
    public static class PrepVmsForHadoop extends AbstractPolicy {
        private static final Logger log = LoggerFactory.getLogger(WebFabricWithHadoopExample.class);
        
        WhirrHadoopCluster hadoopCluster;
        Set<String> configuredIds = []
        
        public PrepVmsForHadoop(WhirrHadoopCluster hadoopCluster) {
            this.hadoopCluster = hadoopCluster;
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
        
        public void start() {
            subscriptionTracker.subscribeToMembers(entity, Startable.SERVICE_UP, 
                { SensorEvent evt -> 
                    log.debug "hadoop set up policy recieved {}", evt
                    if (evt.value) setupMachine(evt.source) } as SensorEventListener);
        }
        public void setupMachine(Entity e) {
            try {
                if (log.isDebugEnabled()) log.debug "setting up machine for hadoop at {}", e
                if (!e.getAttribute(Startable.SERVICE_UP)) return;
                if (!configuredIds.add(e.id)) return;
                SshMachineLocation ssh = Iterables.getOnlyElement(e.locations);
                //would prefer to extract content from HadoopNameNodeClusterActionHandler (but that class would need refactoring)
                ssh.copyTo(new File("${System.getProperty('user.home')}/.whirr/"+hadoopCluster.clusterSpec.clusterName+"/hadoop-site.xml"), "/tmp/hadoop-site.xml");

                File identity = hadoopCluster.clusterSpec.getPrivateKeyFile();
                if (identity == null){
                    identity = File.createTempFile("hadoop", "key");
                    identity.deleteOnExit();
                    Files.write(hadoopCluster.clusterSpec.getPrivateKey(), identity, Charsets.UTF_8);
                }
                if (log.isDebugEnabled()) log.debug "http config update for {}, identity file: {}", e, identity
                ssh.copyTo(identity, "/tmp/hadoop-proxy-private-key");

                //copied from HadoopProxy, would prefer to reference (but again refactoring there is needed) 
                String user = hadoopCluster.clusterSpec.getClusterUser();
                InetAddress namenode = HadoopCluster.getNamenodePublicAddress(hadoopCluster.cluster);
                String server = namenode.getHostName();
                String proxyCommand = [ "ssh",
                    "-i", "/tmp/hadoop-proxy-private-key",
                    "-o", "ConnectTimeout=10",
                    "-o", "ServerAliveInterval=60",
                    "-o", "StrictHostKeyChecking=no",
                    "-o", "UserKnownHostsFile=/dev/null",
                    "-o", "StrictHostKeyChecking=no",
                    "-N",
                    "-D 6666",
                    String.format("%s@%s", user, server) ].join(" ");
                if (log.isDebugEnabled()) log.debug "http config update for {}, proxy command: {}", e, proxyCommand

                ssh.copyTo(new StringReader("""
while [ true ] ; do 
  date
  echo starting proxy for hadoop to """+String.format("%s@%s", user, server)+"""
  nohup """+proxyCommand+"""
  echo proxy ended
done
"""), "/tmp/hadoop-proxy-forever.sh");
                ssh.run("chmod 600 /tmp/hadoop-proxy-private-key ; chmod +x /tmp/hadoop-proxy-forever.sh ; nohup /tmp/hadoop-proxy-forever.sh &");

                URL updateConfig = new URL(e.getAttribute(JBoss7Server.ROOT_URL)+
                        "configure.jsp?key=brooklyn.example.hadoop.site.xml.url&value=file:///tmp/hadoop-site.xml");
                    
                def result = new HttpResponseContext(updateConfig.openConnection());
                if (log.isDebugEnabled()) log.debug "http config update for {} got: {}", e, result.content
            } catch (Exception err) {
                log.warn "unable to configure {} for hadoop: {}", e, err
                configuredIds.remove(e.id);
            }
        }
    }
        
    public static void main(String[] argv) {
        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = new LocationRegistry().getLocationsById(args ?: DEFAULT_LOCATIONS)
        log.info("starting WebFabricWithHadoop, locations {}, mgmt on port {}", locations, port)

        WebFabricWithHadoopExample app = new WebFabricWithHadoopExample(name: 'Brooklyn Global Web Fabric with Hadoop Example');
            
        BrooklynLauncher.manage(app, port)
        app.start(locations)
        Entities.dumpInfo(app)
    }
    
}
