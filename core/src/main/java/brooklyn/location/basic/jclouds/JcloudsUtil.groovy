package brooklyn.location.basic.jclouds;

import static org.jclouds.aws.ec2.reference.AWSEC2Constants.PROPERTY_EC2_AMI_QUERY
import static org.jclouds.aws.ec2.reference.AWSEC2Constants.PROPERTY_EC2_CC_AMI_QUERY
import static org.jclouds.compute.util.ComputeServiceUtils.execHttpResponse
import static org.jclouds.scriptbuilder.domain.Statements.appendFile
import static org.jclouds.scriptbuilder.domain.Statements.exec
import static org.jclouds.scriptbuilder.domain.Statements.interpret
import static org.jclouds.scriptbuilder.domain.Statements.newStatementList

import java.io.File
import java.io.IOException
import java.net.URI
import java.util.Map
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

import org.jclouds.Constants
import org.jclouds.aws.ec2.AWSEC2Client
import org.jclouds.compute.ComputeService
import org.jclouds.compute.ComputeServiceContext
import org.jclouds.compute.ComputeServiceContextFactory
import org.jclouds.compute.RunScriptOnNodesException
import org.jclouds.compute.domain.ExecResponse
import org.jclouds.compute.domain.NodeMetadata
import org.jclouds.compute.domain.OperatingSystem
import org.jclouds.compute.options.RunScriptOptions
import org.jclouds.compute.predicates.OperatingSystemPredicates
import org.jclouds.compute.predicates.RetryIfSocketNotYetOpen
import org.jclouds.compute.reference.ComputeServiceConstants
import org.jclouds.compute.reference.ComputeServiceConstants.Timeouts
import org.jclouds.compute.util.ComputeServiceUtils
import org.jclouds.domain.LoginCredentials
import org.jclouds.ec2.compute.domain.PasswordDataAndPrivateKey
import org.jclouds.ec2.compute.functions.WindowsLoginCredentialsFromEncryptedData
import org.jclouds.ec2.domain.PasswordData
import org.jclouds.ec2.services.WindowsClient
import org.jclouds.encryption.bouncycastle.config.BouncyCastleCryptoModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule
import org.jclouds.net.IPSocket
import org.jclouds.predicates.InetSocketAddressConnect
import org.jclouds.predicates.RetryablePredicate
import org.jclouds.scriptbuilder.domain.Statement
import org.jclouds.scriptbuilder.domain.Statements
import org.jclouds.sshj.config.SshjSshClientModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.Entities

import com.google.common.base.Charsets
import com.google.common.base.Predicate
import com.google.common.base.Splitter
import com.google.common.base.Strings
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import com.google.common.io.Files
import com.google.inject.Module

public class JcloudsUtil {
    
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
                .append('echo \"export PATH=\\\"\\$JAVA_HOME/bin/:\\$PATH\\\"\" >> /root/.bashrc')//
                .toString()));

    public static final Statement YUM_RUN_SCRIPT = newStatementList(
          exec("which curl ||yum --nogpgcheck -y install curl"),//
          exec("(which java && java -fullversion 2>&1|egrep -q 1.6 ) ||"),//
          execHttpResponse(URI.create("http://whirr.s3.amazonaws.com/0.2.0-incubating-SNAPSHOT/sun/java/install")),//
          exec(new StringBuilder()//
                .append("echo nameserver 208.67.222.222 >> /etc/resolv.conf\n") //
                .append('echo \"export PATH=\\\"\\$JAVA_HOME/bin/:\\$PATH\\\"\" >> /root/.bashrc')//
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

    static Map<Properties,ComputeService> cachedComputeServices = new ConcurrentHashMap<Properties,ComputeService> ();
     
    public static ComputeService buildOrFindComputeService(Map<String,? extends Object> conf) {
        return buildComputeService(conf, [:], true)
    }
    public static ComputeService buildOrFindComputeService(Map<String,? extends Object> conf, Map unusedConf) {
        return buildComputeService(conf, unusedConf, true);
    }
    
    public static ComputeService buildComputeService(Map<String,? extends Object> conf) {
        return buildComputeService(conf, [:]);
    }
    public static ComputeService buildComputeService(Map<String,? extends Object> conf, Map unusedConf) {
        return buildComputeService(conf, unusedConf, false);
    }
    public static ComputeService buildComputeService(Map<String,? extends Object> conf, Map unusedConf, boolean allowReuse) {
        Properties properties = new Properties();
        properties.setProperty(Constants.PROPERTY_PROVIDER, conf.provider); unusedConf.remove("provider");
        properties.setProperty(Constants.PROPERTY_IDENTITY, conf.identity); unusedConf.remove("identity");
        properties.setProperty(Constants.PROPERTY_CREDENTIAL, conf.credential); unusedConf.remove("credential");
        properties.setProperty(Constants.PROPERTY_TRUST_ALL_CERTS, Boolean.toString(true))
        properties.setProperty(Constants.PROPERTY_RELAX_HOSTNAME, Boolean.toString(true))
                
        // Enable aws-ec2 lazy image fetching, if given a specific imageId; otherwise customize for specific owners; or all as a last resort
        // See https://issues.apache.org/jira/browse/WHIRR-416
        if ("aws-ec2".equals(conf.provider)) {
            if (conf.imageId) {
                properties.setProperty(PROPERTY_EC2_AMI_QUERY, "")
                properties.setProperty(PROPERTY_EC2_CC_AMI_QUERY, "")
            } else if (conf.imageOwner) {
                unusedConf.remove("imageOwner");
                properties.setProperty(PROPERTY_EC2_AMI_QUERY, "owner-id="+conf.imageOwner+";state=available;image-type=machine")
            } else if (conf.anyOwner) {
                // set `anyOwner: true` to override the default query (which is restricted to certain owners as per below), 
                // allowing the AMI query to bind to any machine
                // (note however, we sometimes pick defaults in JcloudsLocationFactory);
                // (and be careful, this can give a LOT of data back, taking several minutes,
                // and requiring extra memory allocated on the command-line)
                unusedConf.remove("anyOwner");
                properties.setProperty(PROPERTY_EC2_AMI_QUERY, "state=available;image-type=machine")
            
                // by default the following filters are applied:
//            Filter.1.Name=owner-id&Filter.1.Value.1=137112412989&
//            Filter.1.Value.2=063491364108&
//            Filter.1.Value.3=099720109477&
//            Filter.1.Value.4=411009282317&
//            Filter.2.Name=state&Filter.2.Value.1=available&
//            Filter.3.Name=image-type&Filter.3.Value.1=machine&
            }
        }

        String endpoint = unusedConf.remove(Constants.PROPERTY_ENDPOINT);
        if (endpoint) properties.setProperty(Constants.PROPERTY_ENDPOINT, endpoint);

        if (allowReuse) {
            ComputeService result = cachedComputeServices.get(properties);
            if (result!=null) {
                LOG.debug("jclouds ComputeService cache hit for compute service, for "+Entities.sanitize(properties));
                return result;
            }
            LOG.debug("jclouds ComputeService cache miss for compute service, creating, for "+Entities.sanitize(properties));
        }
        
        Iterable<Module> modules = ImmutableSet.<Module> of(
                new SshjSshClientModule(), 
                new SLF4JLoggingModule(),
                new BouncyCastleCryptoModule());
        
        ComputeServiceContextFactory computeServiceFactory = new ComputeServiceContextFactory();
        
        ComputeService computeService = computeServiceFactory
                .createContext(conf.provider, modules, properties)
                .getComputeService();
                
        if (allowReuse) {
            synchronized (cachedComputeServices) {
                ComputeService result = cachedComputeServices.get(properties);
                if (result) {
                    LOG.debug("jclouds ComputeService cache recovery for compute service, for "+Entities.sanitize(properties));
                    //keep the old one, discard the new one
                    computeService.getContext().close();
                    return result;
                }
                LOG.debug("jclouds ComputeService created "+computeService+", adding to cache, for "+Entities.sanitize(properties));
                cachedComputeServices.put(properties, computeService);
            }
        }
        
        return computeService;
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

    public static String getFirstReachableAddress(NodeMetadata node) {
        // Notes from Adrian:
        //   In Whirr, DnsUtil does this sort of stuff so could take from there?
        //   For validating result, could use guava's InternetDomainName.isValidLenient(ip) or InetAddresses.isInetAddress(ip)
        //   He also mentioned context.utils.sshForNode

        InetSocketAddressConnect inetSocketAddressConnect = new InetSocketAddressConnect();
        Timeouts timeouts = new ComputeServiceConstants.Timeouts();
        IPSocket reachableSocketOnNode = ComputeServiceUtils.findReachableSocketOnNode(new RetryIfSocketNotYetOpen(inetSocketAddressConnect, timeouts), node, 22);
        return (reachableSocketOnNode != null) ? reachableSocketOnNode.getAddress() : null;
    }
    
    /**
     * Returns the IP address for a node which should be used by other nodes to
     * contact it. When using a VPN, this could be a private address. It could
     * also be a public one. The method tries to guess what will work.
     */
	//TODO this method doesn't have enough info; better to have a method on the target, eg MachineLocation.getHostnameForUseFrom(Location source)
    public static String getNodeAddress(NodeMetadata node) {
        String addr = JcloudsUtil.getFirstReachableAddress(node);

        if (addr != null) {
            return addr;
        } else if (node.getPublicAddresses().size() > 0) {
            String publicAddr = Iterables.get(node.getPublicAddresses(), 0);
            LOG.warn("No reachable address found for node; using " + publicAddr);
            return publicAddr;
        } else {
            throw new IllegalStateException("Could not discover a suitable address for " + node);
        }
    }
    
    // Suggest at least 15 minutes for timeout
    public static String waitForPasswordOnAws(ComputeService computeService, final NodeMetadata node, long timeout, TimeUnit timeUnit) throws TimeoutException {
        //TODO For jclouds 1.5, switch to this:        
        //     final WindowsClient client = computeServiceContext.unwrap(EC2ApiMetadata.CONTEXT_TOKEN).getApi().getWindowsServices();
        
        ComputeServiceContext computeServiceContext = computeService.getContext();
        AWSEC2Client ec2Client = AWSEC2Client.class.cast(computeServiceContext.getProviderSpecificContext().getApi());
        final WindowsClient client = ec2Client.getWindowsServices();
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
        RetryablePredicate<String> passwordReadyRetryable = new RetryablePredicate<String>(passwordReady, timeUnit.toMillis(timeout), 10*1000, TimeUnit.MILLISECONDS);
        boolean ready = passwordReadyRetryable.apply(node.getProviderId());
        if (!ready) throw new TimeoutException("Password not available for "+node+" in region "+region+" after "+timeout+" "+timeUnit.name());

        // Now pull together Amazon's encrypted password blob, and the private key that jclouds generated
        PasswordDataAndPrivateKey dataAndKey = new PasswordDataAndPrivateKey(
                client.getPasswordDataInRegion(region, node.getProviderId()),
                node.getCredentials().getPrivateKey());

        // And apply it to the decryption function
        WindowsLoginCredentialsFromEncryptedData f = computeServiceContext.getUtils().getInjector().getInstance(WindowsLoginCredentialsFromEncryptedData.class);
        LoginCredentials credentials = f.apply(dataAndKey);

        return credentials.getPassword();
    }
}
