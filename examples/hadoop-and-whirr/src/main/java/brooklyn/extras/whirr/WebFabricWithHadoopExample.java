package brooklyn.extras.whirr;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.whirr.service.hadoop.HadoopCluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.StringConfigMap;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService;
import brooklyn.entity.group.DynamicFabric;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.webapp.ElasticJavaWebAppService;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.feed.http.HttpPollValue;
import brooklyn.event.feed.http.HttpPolls;
import brooklyn.extras.whirr.hadoop.WhirrHadoopCluster;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.Location;
import brooklyn.location.basic.PortRanges;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.CommandLineUtil;
import brooklyn.util.task.ParallelTask;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

/**
 * Starts hadoop in the first location supplied, and the hadoop-friendly webapp in all other locations.
 * Webapp get configured via the configure.jsp page, plus supplying the proxy command, to connect to hadoop.
 */
public class WebFabricWithHadoopExample extends AbstractApplication implements StartableApplication {

    private static final Logger log = LoggerFactory.getLogger(WebFabricWithHadoopExample.class);

    static final List<String> DEFAULT_LOCATIONS = ImmutableList.of(
        // hadoop location
        "aws-ec2:eu-west-1",
          
        //web locations
        "aws-ec2:eu-west-1",
        "aws-ec2:ap-southeast-1",
        "aws-ec2:us-west-1");

    public static final String WAR_PATH = "classpath://hello-world-hadoop-webapp.war";
    
    private WhirrHadoopCluster hadoopCluster;
    private GeoscalingDnsService geoDns;
    private DynamicFabric webFabric;
    private DynamicGroup webVms;
    
    public WebFabricWithHadoopExample() {
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
    
        GeoscalingDnsService geoDns = addChild(EntitySpec.create(GeoscalingDnsService.class)
                .displayName("GeoScaling DNS")
                .configure("username", checkNotNull(config.getFirst("brooklyn.geoscaling.username"), "username"))
                .configure("password", checkNotNull(config.getFirst("brooklyn.geoscaling.password"), "password"))
                .configure("primaryDomainName", checkNotNull(config.getFirst("brooklyn.geoscaling.primaryDomain"), "primaryDomain"))
                .configure("smartSubdomainName", "brooklyn"));
        
        webFabric = addChild(EntitySpec.create(DynamicFabric.class)
                .displayName("Web Fabric")
                .configure("factory", new ElasticJavaWebAppService.Factory())
                //specify the WAR file to use
                .configure(ElasticJavaWebAppService.ROOT_WAR, WAR_PATH)
                //load-balancer instances must run on 80 to work with GeoDNS (default is 8000)
                .configure(AbstractController.PROXY_HTTP_PORT, PortRanges.fromInteger(80))
                );
//                .policy(AutoScalerPolicy.builder()
//                        .metric(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND)
//                        .sizeRange(1, 5)
//                        .metricRange(10, 100)
//                        .build()));
        
        webVms = addChild(EntitySpec.create(DynamicGroup.class)
                .displayName("Web VMs")
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(JBoss7Server.class)));
        
        //tell GeoDNS what to monitor
        geoDns.setTargetEntityProvider(webFabric);
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        if (locations.isEmpty()) throw new IllegalStateException("location required to start "+this);
        final Location hadoopLocation = Iterables.getFirst(locations, null);
        // start hadoop in first, web in others (unless there is just one location supplied)
        final List<Location> webLocations = (locations.size() > 1) ? ImmutableList.copyOf(Iterables.skip(locations, 1)) : ImmutableList.of(hadoopLocation);

        Task<List<?>> starts = getExecutionContext().submit(new ParallelTask(
                new Runnable() {
                    public void run() {
                        webFabric.start(webLocations);
                    }
                },
                new Runnable() {
                    public void run() {
                        hadoopCluster.start(ImmutableList.of(hadoopLocation)); 
                        // collect the hadoop-site.xml and feed it to all existing and new appservers,
                        // and start the proxies there
                        PrepVmsForHadoop.newPolicyFromGroupToHadoop(webVms, hadoopCluster);
                    }
                }));
        starts.blockUntilEnded();
    }    
    
    public static class PrepVmsForHadoop extends AbstractPolicy {
        WhirrHadoopCluster hadoopCluster;
        Set<String> configuredIds = Sets.newLinkedHashSet();
        
        public PrepVmsForHadoop(WhirrHadoopCluster hadoopCluster) {
            this.hadoopCluster = hadoopCluster;
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
        
        public void start() {
            subscribeToMembers((Group)entity, Startable.SERVICE_UP, new SensorEventListener<Boolean>() {
                @Override public void onEvent(SensorEvent<Boolean> event) {
                    log.debug("hadoop set up policy recieved {}", event);
                    if (event.getValue() != null) {
                        setupMachine(event.getSource());
                    }
                }});
        }
        
        public void setupMachine(Entity e) {
            try {
                if (log.isDebugEnabled()) log.debug("setting up machine for hadoop at {}", e);
                if (!e.getAttribute(Startable.SERVICE_UP)) return;
                if (!configuredIds.add(e.getId())) return;
                SshMachineLocation ssh = (SshMachineLocation) Iterables.getOnlyElement(e.getLocations());
                // TODO would prefer to extract content from HadoopNameNodeClusterActionHandler (but that class would need refactoring)
                ssh.copyTo(new File(System.getProperty("user.home")+"/.whirr/"+hadoopCluster.getClusterSpec().getClusterName()+"/hadoop-site.xml"), "/tmp/hadoop-site.xml");

                File identity = hadoopCluster.getClusterSpec().getPrivateKeyFile();
                if (identity == null){
                    identity = File.createTempFile("hadoop", "key");
                    identity.deleteOnExit();
                    Files.write(hadoopCluster.getClusterSpec().getPrivateKey(), identity, Charsets.UTF_8);
                }
                if (log.isDebugEnabled()) log.debug("http config update for {}, identity file: {}", e, identity);
                ssh.copyTo(identity, "/tmp/hadoop-proxy-private-key");

                //copied from HadoopProxy, would prefer to reference (but again refactoring there is needed) 
                String user = hadoopCluster.getClusterSpec().getClusterUser();
                InetAddress namenode = HadoopCluster.getNamenodePublicAddress(hadoopCluster.getCluster());
                String server = namenode.getHostName();
                String proxyCommand = Joiner.on(" ").join(ImmutableList.of(
                        "ssh",
                        "-i", "/tmp/hadoop-proxy-private-key",
                        "-o", "ConnectTimeout=10",
                        "-o", "ServerAliveInterval=60",
                        "-o", "StrictHostKeyChecking=no",
                        "-o", "UserKnownHostsFile=/dev/null",
                        "-o", "StrictHostKeyChecking=no",
                        "-N",
                        "-D 6666",
                        String.format("%s@%s", user, server)));
                if (log.isDebugEnabled()) log.debug("http config update for {}, proxy command: {}", e, proxyCommand);

                String hadoopProxyForeverContent = 
                        "while [ true ] ; do"+"\n"+ 
                        "    date"+"\n"+
                        "    echo starting proxy for hadoop to "+String.format("%s@%s", user, server)+"\n"+
                        "    nohup "+proxyCommand+"\n"+
                        "    echo proxy ended"+"\n"+
                        "done"+"\n";
                
                ssh.copyTo(new StringReader(hadoopProxyForeverContent), "/tmp/hadoop-proxy-forever.sh");
                  
                ssh.run("chmod 600 /tmp/hadoop-proxy-private-key ; chmod +x /tmp/hadoop-proxy-forever.sh ; nohup /tmp/hadoop-proxy-forever.sh &");

                URI updateConfigUri = new URI(e.getAttribute(JBoss7Server.ROOT_URL)+
                        "configure.jsp?key=brooklyn.example.hadoop.site.xml.url&value=file:///tmp/hadoop-site.xml");
                
                HttpPollValue result = HttpPolls.executeSimpleGet(updateConfigUri);
                if (log.isDebugEnabled()) log.debug("http config update for {} got: {}, {}", new Object[] {e, result.getResponseCode(), new String(result.getContent())});
            } catch (Exception err) {
                log.warn("unable to configure "+e+" for hadoop", err);
                configuredIds.remove(e.getId());
            }
        }
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", Joiner.on(",").join(DEFAULT_LOCATIONS));

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpecs.appSpec(StartableApplication.class)
                        .displayName("Brooklyn Global Web Fabric with Hadoop Example")
                        .impl(WebFabricWithHadoopExample.class))
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
}
