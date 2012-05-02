package brooklyn.extras.whirr

import groovy.transform.InheritConstructors

import java.io.File
import java.net.InetAddress
import java.util.List

import org.apache.whirr.service.hadoop.HadoopCluster
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.basic.Entities
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService
import brooklyn.entity.group.AbstractController
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.ElasticJavaWebAppService
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.adapter.HttpResponseContext
import brooklyn.event.basic.DependentConfiguration
import brooklyn.extras.cloudfoundry.CloudFoundryJavaWebAppCluster
import brooklyn.extras.whirr.hadoop.WhirrHadoopCluster
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
import com.google.common.io.Files

/**
 * Starts hadoop in the first location supplied, and the hadoop-friendly webapp in all other locations.
 * TODO The webapp needs to be manually configured (via the configure.jsp page, plus supplying the proxy command) to connect to hadoop
 */
@InheritConstructors
public class WebFabricWithHadoopExample extends AbstractApplication {

    private static final Logger LOG = LoggerFactory.getLogger(WhirrHadoopExample.class);

    static final List<String> DEFAULT_LOCATIONS = [
        "aws-ec2:us-west-1",
        "aws-ec2:us-west-1",
        "aws-ec2:eu-west-1",
//        "aws-ec2:ap-southeast-1",
//        "aws-ec2:us-west-1",
        
        // cloudfoundry seems to have a cap on app size, this one is too big :(
        // (in any case we have no way to initiate the proxy settings from there)
//        "cloudfoundry:https://api.aws.af.cm/",
    ];

    public static final String WAR_PATH = "classpath://hello-world-hadoop-webapp.war";
            
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()
    
    WhirrHadoopCluster hadoopCluster = new WhirrHadoopCluster(this, size: 2, memory: 2048, name: "brooklyn-hadoop-example");
    
    DynamicFabric webFabric = new DynamicFabric(this, name: "Web Fabric", factory: new ElasticJavaWebAppService.Factory());
    
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

    DynamicGroup webVms = new DynamicGroup(this, name: "Web VMs");
    
    void start(Collection locations) {
        Iterator li = locations.iterator();
        if (!li.hasNext()) return;
        Location clusterLocation = li.next();
        List otherLocations = [];
        while (li.hasNext()) otherLocations << li.next();
        if (!otherLocations)
            //start web in same location if just given one 
            otherLocations << clusterLocation;

        Task starts = executionContext.submit(new ParallelTask(        
            { hadoopCluster.start([clusterLocation]) },
            { webFabric.start(otherLocations) } ));
        starts.blockUntilEnded();
        
        // collect the hadoop-site.xml and feed it to all existing and new appservers,
        // and start the proxies there
        webVms.setEntityFilter { it in JBoss7Server }
        
        PrepVmsForHadoop prepVmsForHadoop = new PrepVmsForHadoop(this);
        webVms.addPolicy(prepVmsForHadoop);
        prepVmsForHadoop.start();
        webVms.members.each { prepVmsForHadoop.setupMachine(it) }
	}    
    
    public static class PrepVmsForHadoop extends AbstractPolicy {
        WebFabricWithHadoopExample app;
        public PrepVmsForHadoop(WebFabricWithHadoopExample app) {
            this.app = app;
        }
        public void start() {
            subscriptionTracker.subscribeToMembers(entity, Startable.SERVICE_UP, 
                { SensorEvent evt -> if (evt.value) setupMachine(evt.source) } as SensorEventListener);
        }
        public void setupMachine(Entity e) {
            SshMachineLocation ssh = Iterables.getOnlyElement(e.locations);
            ssh.copyTo(new File("${System.getProperty('user.home')}/.whirr/brooklyn-hadoop-example/hadoop-site.xml"), "/tmp/hadoop-site.xml");
            
            File identity = app.hadoopCluster.clusterSpec.getPrivateKeyFile();
            if (identity == null){
              identity = File.createTempFile("hadoop", "key");
              identity.deleteOnExit();
              Files.write(app.hadoopCluster.clusterSpec.getPrivateKey(), identity, Charsets.UTF_8);
            }
            if (log.isDebugEnabled()) log.debug "http config update for {}, identity file: {}", e, identity
            ssh.copyTo(identity, "/tmp/hadoop-proxy-private-key");
            
            String user = app.hadoopCluster.clusterSpec.getClusterUser();
            InetAddress namenode = HadoopCluster.getNamenodePublicAddress(app.hadoopCluster.cluster);
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
while [ true ] do 
  date
  echo starting proxy for hadoop to """+String.format("%s@%s", user, server)+"""
  nohup """+proxyCommand+"""
  echo proxy ended
done
"""), "/tmp/hadoop-proxy-forever.sh");
            ssh.run("chmod 600 /tmp/hadoop-proxy-private-key ; chmod +w /tmp/hadoop-proxy-forever.sh ; nohup /tmp/hadoop-proxy-forevery.sh &");

            URL updateConfig = new URL(e.getAttribute(JBoss7Server.ROOT_URL)+
                    "configure.jsp?key=brooklyn.example.hadoop.site.xml.url&value=file:///tmp/hadoop-site.xml");
                
            def result = new HttpResponseContext(updateConfig.openConnection());
            if (log.isDebugEnabled()) log.debug "http config update for {} got: {}", e, result.content
        }
    }
        
    public static void main(String[] argv) {
        ArrayList args = new ArrayList(Arrays.asList(argv));
        int port = CommandLineUtil.getCommandLineOptionInt(args, "--port", 8081);
        List<Location> locations = new LocationRegistry().getLocationsById(args ?: DEFAULT_LOCATIONS)

        WebFabricWithHadoopExample app = new WebFabricWithHadoopExample(name: 'Brooklyn Global Web Fabric with Hadoop Example');
            
        BrooklynLauncher.manage(app, port)
        app.start(locations)
        Entities.dumpInfo(app)
    }
    
}
