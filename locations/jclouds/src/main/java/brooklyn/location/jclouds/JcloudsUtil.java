package brooklyn.location.jclouds;

import static brooklyn.util.GroovyJavaMethods.truth;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.jclouds.aws.ec2.reference.AWSEC2Constants.PROPERTY_EC2_AMI_QUERY;
import static org.jclouds.aws.ec2.reference.AWSEC2Constants.PROPERTY_EC2_CC_AMI_QUERY;
import static org.jclouds.compute.options.RunScriptOptions.Builder.overrideLoginCredentials;
import static org.jclouds.compute.util.ComputeServiceUtils.execHttpResponse;
import static org.jclouds.scriptbuilder.domain.Statements.*;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jclouds.Constants;
import org.jclouds.ContextBuilder;
import org.jclouds.aws.ec2.AWSEC2Api;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunScriptOnNodesException;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.OperatingSystem;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.compute.predicates.OperatingSystemPredicates;
import org.jclouds.docker.DockerApi;
import org.jclouds.docker.domain.Container;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.ec2.compute.domain.PasswordDataAndPrivateKey;
import org.jclouds.ec2.compute.functions.WindowsLoginCredentialsFromEncryptedData;
import org.jclouds.ec2.domain.PasswordData;
import org.jclouds.ec2.features.WindowsApi;
import org.jclouds.encryption.bouncycastle.config.BouncyCastleCryptoModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.Statements;
import org.jclouds.ssh.SshClient;
import org.jclouds.sshj.config.SshjSshClientModule;
import org.jclouds.util.Predicates2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.location.jclouds.config.BrooklynStandardJcloudsGuiceModule;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.net.Protocol;
import brooklyn.util.ssh.IptablesCommands;
import brooklyn.util.ssh.IptablesCommands.Chain;
import brooklyn.util.ssh.IptablesCommands.Policy;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.inject.Module;

public class JcloudsUtil implements JcloudsLocationConfig {
    
    // TODO Review what utility methods are needed, and what is now supported in jclouds 1.1
    
    private static final Logger LOG = LoggerFactory.getLogger(JcloudsUtil.class);
    
    public static String APT_INSTALL = "apt-get install -f -y -qq --force-yes";

    public static String installAfterUpdatingIfNotPresent(String cmd) {
       String aptInstallCmd = APT_INSTALL + " " + cmd;
       return String.format("which %s || (%s || (apt-get update && %s))", cmd, aptInstallCmd, aptInstallCmd);
    }

    public static Predicate<NodeMetadata> predicateMatchingById(final NodeMetadata node) {
        return predicateMatchingById(node.getId());
    }

    public static Predicate<NodeMetadata> predicateMatchingById(final String id) {
        Predicate<NodeMetadata> nodePredicate = new Predicate<NodeMetadata>() {
            @Override public boolean apply(NodeMetadata arg0) {
                return id.equals(arg0.getId());
            }
            @Override public String toString() {
                return "node.id=="+id;
            }
        };
        return nodePredicate;
    }

    public static Statement authorizePortInIpTables(int port) {
        // TODO gogrid rules only allow ports 22, 3389, 80 and 443.
        // the first rule will be ignored, so we have to apply this
        // directly
        return Statements.newStatementList(// just in case iptables are being used, try to open 8080
                exec("iptables -I INPUT 1 -p tcp --dport " + port + " -j ACCEPT"),//
                exec("iptables -I RH-Firewall-1-INPUT 1 -p tcp --dport " + port + " -j ACCEPT"),//
                exec("iptables-save"));
    }

    /**
     * @throws RunScriptOnNodesException
     * @throws IllegalStateException     If do not find exactly one matching node
     */
    public static ExecResponse runScriptOnNode(ComputeService computeService, NodeMetadata node, Statement statement, String scriptName) throws RunScriptOnNodesException {
        // TODO Includes workaround for NodeMetadata's equals/hashcode method being wrong.
        
        Map<? extends NodeMetadata, ExecResponse> scriptResults = computeService.runScriptOnNodesMatching(
                JcloudsUtil.predicateMatchingById(node), 
                statement,
                new RunScriptOptions().nameTask(scriptName));
        if (scriptResults.isEmpty()) {
            throw new IllegalStateException("No matching node found when executing script "+scriptName+": expected="+node);
        } else if (scriptResults.size() > 1) {
            throw new IllegalStateException("Multiple nodes matched predicate: id="+node.getId()+"; expected="+node+"; actual="+scriptResults.keySet());
        } else {
            return Iterables.getOnlyElement(scriptResults.values());
        }
    }
    
    public static final Statement APT_RUN_SCRIPT = newStatementList(//
          exec(installAfterUpdatingIfNotPresent("curl")),//
          exec("(which java && java -fullversion 2>&1|egrep -q 1.6 ) ||"),//
          execHttpResponse(URI.create("http://whirr.s3.amazonaws.com/0.2.0-incubating-SNAPSHOT/sun/java/install")),//
          exec(new StringBuilder()//
                .append("echo nameserver 208.67.222.222 >> /etc/resolv.conf\n")//
                // jeos hasn't enough room!
                .append("rm -rf /var/cache/apt /usr/lib/vmware-tools\n")//
                .append("echo \"export PATH=\\\"$JAVA_HOME/bin/:$PATH\\\"\" >> /root/.bashrc")//
                .toString()));

    public static final Statement YUM_RUN_SCRIPT = newStatementList(
          exec("which curl ||yum --nogpgcheck -y install curl"),//
          exec("(which java && java -fullversion 2>&1|egrep -q 1.6 ) ||"),//
          execHttpResponse(URI.create("http://whirr.s3.amazonaws.com/0.2.0-incubating-SNAPSHOT/sun/java/install")),//
          exec(new StringBuilder()//
                .append("echo nameserver 208.67.222.222 >> /etc/resolv.conf\n") //
                .append("echo \"export PATH=\\\"$JAVA_HOME/bin/:$PATH\\\"\" >> /root/.bashrc")//
                .toString()));

    public static final Statement ZYPPER_RUN_SCRIPT = exec(new StringBuilder()//
          .append("echo nameserver 208.67.222.222 >> /etc/resolv.conf\n")//
          .append("which curl || zypper install curl\n")//
          .append("(which java && java -fullversion 2>&1|egrep -q 1.6 ) || zypper install java-1.6.0-openjdk\n")//
          .toString());

    // Code taken from RunScriptData
    public static Statement installJavaAndCurl(OperatingSystem os) {
       if (os == null || OperatingSystemPredicates.supportsApt().apply(os))
          return APT_RUN_SCRIPT;
       else if (OperatingSystemPredicates.supportsYum().apply(os))
          return YUM_RUN_SCRIPT;
       else if (OperatingSystemPredicates.supportsZypper().apply(os))
          return ZYPPER_RUN_SCRIPT;
       else
          throw new IllegalArgumentException("don't know how to handle" + os.toString());
    }

    static Map<Map<?,?>,ComputeService> cachedComputeServices = new ConcurrentHashMap<Map<?,?>,ComputeService> ();

    private static final Object createComputeServicesMutex = new Object();

    public static ComputeService findComputeService(ConfigBag conf) {
        return findComputeService(conf, true);
    }
    public static ComputeService findComputeService(ConfigBag conf, boolean allowReuse) {
        String provider = checkNotNull(conf.get(CLOUD_PROVIDER), "provider must not be null");
        String identity = checkNotNull(conf.get(ACCESS_IDENTITY), "identity must not be null");
        String credential = checkNotNull(conf.get(ACCESS_CREDENTIAL), "credential must not be null");
        
        Properties properties = new Properties();
        properties.setProperty(Constants.PROPERTY_TRUST_ALL_CERTS, Boolean.toString(true));
        properties.setProperty(Constants.PROPERTY_RELAX_HOSTNAME, Boolean.toString(true));
        properties.setProperty("jclouds.ssh.max-retries", conf.getStringKey("jclouds.ssh.max-retries") != null ? 
                conf.getStringKey("jclouds.ssh.max-retries").toString() : "50");
        // Enable aws-ec2 lazy image fetching, if given a specific imageId; otherwise customize for specific owners; or all as a last resort
        // See https://issues.apache.org/jira/browse/WHIRR-416
        if ("aws-ec2".equals(provider)) {
            // TODO convert AWS-only flags to config keys
            if (truth(conf.get(IMAGE_ID))) {
                properties.setProperty(PROPERTY_EC2_AMI_QUERY, "");
                properties.setProperty(PROPERTY_EC2_CC_AMI_QUERY, "");
            } else if (truth(conf.getStringKey("imageOwner"))) {
                properties.setProperty(PROPERTY_EC2_AMI_QUERY, "owner-id="+conf.getStringKey("imageOwner")+";state=available;image-type=machine");
            } else if (truth(conf.getStringKey("anyOwner"))) {
                // set `anyOwner: true` to override the default query (which is restricted to certain owners as per below), 
                // allowing the AMI query to bind to any machine
                // (note however, we sometimes pick defaults in JcloudsLocationFactory);
                // (and be careful, this can give a LOT of data back, taking several minutes,
                // and requiring extra memory allocated on the command-line)
                properties.setProperty(PROPERTY_EC2_AMI_QUERY, "state=available;image-type=machine");
                /*
                 * by default the following filters are applied:
                 * Filter.1.Name=owner-id&Filter.1.Value.1=137112412989&
                 * Filter.1.Value.2=063491364108&
                 * Filter.1.Value.3=099720109477&
                 * Filter.1.Value.4=411009282317&
                 * Filter.2.Name=state&Filter.2.Value.1=available&
                 * Filter.3.Name=image-type&Filter.3.Value.1=machine&
                 */
            }
        }

        // FIXME Deprecated mechanism, should have a ConfigKey for overrides
        Map<String, Object> extra = Maps.filterKeys(conf.getAllConfig(), Predicates.containsPattern("^jclouds\\."));
        if (extra.size() > 0) {
            LOG.warn("Jclouds using deprecated property overrides: "+Entities.sanitize(extra));
        }
        properties.putAll(extra);

        String endpoint = conf.get(CLOUD_ENDPOINT);
        if (!truth(endpoint)) endpoint = getDeprecatedProperty(conf, Constants.PROPERTY_ENDPOINT);
        if (truth(endpoint)) properties.setProperty(Constants.PROPERTY_ENDPOINT, endpoint);

        Map<?,?> cacheKey = MutableMap.builder()
                .putAll(properties)
                .put("provider", provider)
                .put("identity", identity)
                .put("credential", credential)
                .putIfNotNull("endpoint", endpoint)
                .build()
                .toImmutable();

        if (allowReuse) {
            ComputeService result = cachedComputeServices.get(cacheKey);
            if (result!=null) {
                LOG.debug("jclouds ComputeService cache hit for compute service, for "+Entities.sanitize(properties));
                return result;
            }
            LOG.debug("jclouds ComputeService cache miss for compute service, creating, for "+Entities.sanitize(properties));
        }

        Iterable<Module> modules = ImmutableSet.<Module> of(
                new SshjSshClientModule(), 
                new SLF4JLoggingModule(),
                new BouncyCastleCryptoModule(),
                new BrooklynStandardJcloudsGuiceModule());

        // Synchronizing to avoid deadlock from sun.reflect.annotation.AnnotationType.
        // See https://github.com/brooklyncentral/brooklyn/issues/974
        ComputeServiceContext computeServiceContext;
        synchronized (createComputeServicesMutex) {
            computeServiceContext = ContextBuilder.newBuilder(provider)
                    .modules(modules)
                    .credentials(identity, credential)
                    .overrides(properties)
                    .build(ComputeServiceContext.class);
        }
        final ComputeService computeService = computeServiceContext.getComputeService();
        if (allowReuse) {
            synchronized (cachedComputeServices) {
                ComputeService result = cachedComputeServices.get(cacheKey);
                if (result != null) {
                    LOG.debug("jclouds ComputeService cache recovery for compute service, for "+Entities.sanitize(cacheKey));
                    //keep the old one, discard the new one
                    computeService.getContext().close();
                    return result;
                }
                LOG.debug("jclouds ComputeService created "+computeService+", adding to cache, for "+Entities.sanitize(properties));
                cachedComputeServices.put(cacheKey, computeService);
            }
        }
        return computeService;
     }
     
     protected static String getDeprecatedProperty(ConfigBag conf, String key) {
        if (conf.containsKey(key)) {
            LOG.warn("Jclouds using deprecated brooklyn-jclouds property "+key+": "+Entities.sanitize(conf.getAllConfig()));
            return (String) conf.getStringKey(key);
        } else {
            return null;
        }
    }

    // Do this so that if there's a problem with our USERNAME's ssh key, we can still get in to check
     // TODO Once we're really confident there are not going to be regular problems, then delete this
     public static Statement addAuthorizedKeysToRoot(File publicKeyFile) throws IOException {
         String publicKey = Files.toString(publicKeyFile, Charsets.UTF_8);
         return addAuthorizedKeysToRoot(publicKey);
     }
     
     public static Statement addAuthorizedKeysToRoot(String publicKey) {
         return newStatementList(
                 appendFile("/root/.ssh/authorized_keys", Splitter.on('\n').split(publicKey)),
                 interpret("chmod 600 /root/.ssh/authorized_keys"));
    }

    public static String getFirstReachableAddress(ComputeServiceContext context, NodeMetadata node) {
        // To pick the address, it relies on jclouds `sshForNode().apply(Node)` to check all IPs of node (private+public), 
        // to find one that is reachable. It does `openSocketFinder.findOpenSocketOnNode(node, node.getLoginPort(), ...)`.
        // This keeps trying for time org.jclouds.compute.reference.ComputeServiceConstants.Timeouts.portOpen.
        // TODO Want to configure this timeout here.
        //
        // TODO We could perhaps instead just set `templateOptions.blockOnPort(loginPort, 120)`, but need
        // to be careful to only set that if config WAIT_FOR_SSHABLE is true. For some advanced networking examples
        // (e.g. using DNAT on CloudStack), the brooklyn machine won't be able to reach the VM until some additional
        // setup steps have been done. See links from Andrea:
        //     https://github.com/jclouds/jclouds/pull/895
        //     https://issues.apache.org/jira/browse/WHIRR-420
        //     jclouds.ssh.max-retries
        //     jclouds.ssh.retry-auth

        final SshClient client = context.utils().sshForNode().apply(node);
        return client.getHostAddress();
    }
    
    // Suggest at least 15 minutes for timeout
    public static String waitForPasswordOnAws(ComputeService computeService, final NodeMetadata node, long timeout, TimeUnit timeUnit) throws TimeoutException {
        ComputeServiceContext computeServiceContext = computeService.getContext();
        AWSEC2Api ec2Client = computeServiceContext.unwrapApi(AWSEC2Api.class);
        final WindowsApi client = ec2Client.getWindowsApi().get();
        final String region = node.getLocation().getParent().getId();
      
        // The Administrator password will take some time before it is ready - Amazon says sometimes 15 minutes.
        // So we create a predicate that tests if the password is ready, and wrap it in a retryable predicate.
        Predicate<String> passwordReady = new Predicate<String>() {
            @Override public boolean apply(String s) {
                if (Strings.isNullOrEmpty(s)) return false;
                PasswordData data = client.getPasswordDataInRegion(region, s);
                if (data == null) return false;
                return !Strings.isNullOrEmpty(data.getPasswordData());
            }
        };
        
        LOG.info("Waiting for password, for "+node.getProviderId()+":"+node.getId());
        Predicate passwordReadyRetryable = Predicates2.retry(passwordReady, timeUnit.toMillis(timeout), 10*1000, TimeUnit.MILLISECONDS);
        boolean ready = passwordReadyRetryable.apply(node.getProviderId());
        if (!ready) throw new TimeoutException("Password not available for "+node+" in region "+region+" after "+timeout+" "+timeUnit.name());

        // Now pull together Amazon's encrypted password blob, and the private key that jclouds generated
        PasswordDataAndPrivateKey dataAndKey = new PasswordDataAndPrivateKey(
                client.getPasswordDataInRegion(region, node.getProviderId()),
                node.getCredentials().getPrivateKey());

        // And apply it to the decryption function
        WindowsLoginCredentialsFromEncryptedData f = computeServiceContext.utils().injector().getInstance(WindowsLoginCredentialsFromEncryptedData.class);
        LoginCredentials credentials = f.apply(dataAndKey);

        return credentials.getPassword();
    }

    public static Map<Integer, Integer> dockerPortMappingsFor(JcloudsLocation docker, String containerId) {
        ComputeServiceContext context = null;
        try {
            context = ContextBuilder.newBuilder("docker")
                    .endpoint(docker.getEndpoint())
                    .credentials("docker", "docker")
                    .modules(ImmutableSet.<Module>of(new SLF4JLoggingModule(), new SshjSshClientModule()))
                    .build(ComputeServiceContext.class);
            DockerApi api = context.unwrapApi(DockerApi.class);
            Container container = api.getRemoteApi().inspectContainer(containerId);
            Map<Integer, Integer> portMappings = Maps.newLinkedHashMap();
            Map<String, List<Map<String, String>>> ports = container.getNetworkSettings().getPorts();
            if (ports == null) ports = ImmutableMap.<String, List<Map<String,String>>>of();
            
            LOG.debug("Docker will forward these ports {}", ports);
            for (Map.Entry<String, List<Map<String, String>>> entrySet : ports.entrySet()) {
                String containerPort = Iterables.get(Splitter.on("/").split(entrySet.getKey()), 0);
                String hostPort = Iterables.getOnlyElement(Iterables.transform(entrySet.getValue(),
                        new Function<Map<String, String>, String>() {
                            @Override
                            public String apply(Map<String, String> hostIpAndPort) {
                                return hostIpAndPort.get("HostPort");
                            }
                        }));
                portMappings.put(Integer.parseInt(containerPort), Integer.parseInt(hostPort));
            }
            return portMappings;
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    public static void mapSecurityGroupRuleToIpTables(ComputeService computeService, NodeMetadata node,
            LoginCredentials credentials, String networkInterface, Iterable<Integer> ports) {
        for (Integer port : ports) {
            String insertIptableRule = IptablesCommands.insertIptablesRule(Chain.INPUT, networkInterface, 
                    Protocol.TCP, port, Policy.ACCEPT);
            Statement statement = Statements.newStatementList(exec(insertIptableRule));
            ExecResponse response = computeService.runScriptOnNode(node.getId(), statement,
                    overrideLoginCredentials(credentials).runAsRoot(false));
            if (response.getExitStatus() != 0) {
                String msg = String.format("Cannot insert the iptables rule for port %d. Error: %s", port,
                        response.getError());
                LOG.error(msg);
                throw new RuntimeException(msg);
            }
        }
    }
}
