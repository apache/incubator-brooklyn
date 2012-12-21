package brooklyn.location.basic.jclouds;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.jclouds.compute.options.RunScriptOptions.Builder.overrideLoginCredentials;
import static org.jclouds.scriptbuilder.domain.Statements.exec;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.jclouds.Constants;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.callables.RunScriptOnNode;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.compute.domain.OperatingSystem;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.domain.TemplateBuilderSpec;
import org.jclouds.compute.options.RunScriptOptions;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.domain.Credentials;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.ec2.compute.options.EC2TemplateOptions;
import org.jclouds.openstack.nova.v2_0.compute.options.NovaTemplateOptions;
import org.jclouds.scriptbuilder.domain.InterpretableStatement;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.Statements;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.OsDetails;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.BasicOsDetails;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.basic.jclouds.templates.PortableTemplateBuilder;
import brooklyn.util.KeyValueParser;
import brooklyn.util.MutableMap;
import brooklyn.util.Time;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.internal.Repeater;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * For provisioning and managing VMs in a particular provider/region, using jclouds.
 * 
 * Configuration flags include the following:
 *  - provider (e.g. "aws-ec2")
 *  - providerLocationId (e.g. "eu-west-1")
 *  - defaultImageId
 *  
 *  - user (defaults to "root" or other known superuser)
 *  - publicKeyFile
 *  - privateKeyFile
 *  - privateKeyPasspharse
 *  
 *  - loginUser (if should initially login as someone other that root / default VM superuser)
 *  - loginUser.privateKeyFile
 *  - loginUser.privateKeyPasspharse
 *  
 *  - customizer (implementation of {@link JcloudsLocationCustomizer})
 *  - customizers (collection of {@link JcloudsLocationCustomizer} instances)
 *  
 *  // deprecated
 *  - sshPublicKey
 *  - sshPrivateKey
 *  - rootSshPrivateKey (@Beta)
 *  - rootSshPublicKey (@Beta)
 *  - rootSshPublicKeyData (@Beta; calls templateOptions.authorizePublicKey())
 *  - dontCreateUser (otherwise if user != root, then creates this user)
 * 
 * The flags can also includes values passed straight through to jclouds; to the TemplateBuilder:
 *  - minRam
 *  - hardwareId
 *  - imageSize
 *  - imageId
 *  - imageDescriptionRegex
 *  - imageNameRegex
 *  - imageDescriptionPattern (deprecated: use imageDescriptionRegex)
 *  - imageNamePattern (deprecated: use imageNameRegex)
 * 
 * And flag values passed to TemplateOptions:
 *  - securityGroups (for ec2)
 *  - inboundPorts
 *  - userMetadata
 *  - runAsRoot
 *  - overrideLoginUser
 */
public class JcloudsLocation extends AbstractLocation implements MachineProvisioningLocation<SshMachineLocation> {

    // TODO Needs a big overhaul of how config is being managed, and what the property names are (particularly for private-keys)

    // TODO After converting from Groovy to Java, this is now very bad code! It relies entirely on putting 
    // things into and taking them out of maps; it's not type-safe, and it's thus very error-prone.
    // In Groovy, that's considered ok but not in Java. 
    
    public static final Logger LOG = LoggerFactory.getLogger(JcloudsLocation.class);
    
    public static final String ROOT_USERNAME = "root";
    /** these userNames are known to be the preferred/required logins in some common/default images 
     *  where root@ is not allowed to log in */  
    public static final List<String> ROOT_ALIASES = ImmutableList.of("ubuntu", "ec2-user");
    public static final List<String> NON_ADDABLE_USERS = ImmutableList.<String>builder().add(ROOT_USERNAME).addAll(ROOT_ALIASES).build();
    
    public static final int START_SSHABLE_TIMEOUT = 5*60*1000;

    private final Map<String,Map<String, ? extends Object>> tagMapping = Maps.newLinkedHashMap();
    private final Map<JcloudsSshMachineLocation,String> vmInstanceIds = Maps.newLinkedHashMap();

    @SetFromFlag
    private File localTempDir;

    public JcloudsLocation(Map conf) {
        super(conf);
    }
    
    public JcloudsLocation(String identity, String credential, String providerLocationId) {
        this(MutableMap.of("identity", identity, "credential", credential, "providerLocationId", providerLocationId));
    }
    
    protected void configure(Map properties) {
        super.configure(properties);
        if (!truth(name)) {
            name = (String) elvis(getConf().get("providerLocationId"),
                    truth(getConf().get(Constants.PROPERTY_ENDPOINT)) ? 
                            getConf().get("provider")+":"+getConf().get(Constants.PROPERTY_ENDPOINT) :
                            elvis(getConf().get("provider"), "default"));
        }
    }
    
    @Override
    public String toString() {
        Object identity = getConf().get("identity");
        return getClass().getSimpleName()+"["+name+":"+(identity != null ? identity : null)+"]";
    }
    
    public String getProvider() {
        return (String) getConf().get("provider");
    }
    
    public Map getConf() { return leftoverProperties; }
    
    public void setTagMapping(Map<String,Map<String, ? extends Object>> val) {
        tagMapping.clear();
        tagMapping.putAll(val);
    }

    // TODO Delete this? In groovy it was wrong so presumably never called?!
    public void setDefaultImageId(String val) {
        getConf().put("defaultImageId", val);
    }
    
    // TODO Decide on semantics. If I give "TomcatServer" and "Ubuntu", then must I get back an image that matches both?
    // Currently, just takes first match that it finds...
    public Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        Map<String,Object> result = Maps.newLinkedHashMap();
        Collection<String> unmatchedTags = Lists.newArrayList();
        for (String it : tags) {
            if (truth(tagMapping.get(it)) && !truth(result)) {
                result.putAll(tagMapping.get(it));
            } else {
                unmatchedTags.add(it);
            }
        }
        if (unmatchedTags.size() > 0) {
            LOG.debug("Location {}, failed to match provisioning tags {}", this, unmatchedTags);
        }
        return result;
    }
    
    public static class BrooklynJcloudsSetupHolder {
        // TODO this could use an external immutable pattern (unused kept internal, used for logging)
        final JcloudsLocation instance;
        public final Map allconf = Maps.newLinkedHashMap();
        public final Map unusedConf = Maps.newLinkedHashMap();
        
        Object _callerContext = null;
        
        LoginCredentials customCredentials;
        
        public BrooklynJcloudsSetupHolder(JcloudsLocation instance) {
            this.instance = instance;
            useConfig(instance.getConf());
        }
        
        @SuppressWarnings({ "unchecked", "rawtypes" })
        BrooklynJcloudsSetupHolder useConfig(Map flags) {
            allconf.putAll(flags);
            unusedConf.putAll(flags);
            return this;
        }
        
        
        private Object get(String key) {
            unusedConf.remove(key);
            return allconf.get(key);
        }
        private boolean use(String key) {
            unusedConf.remove(key);
            return truth(allconf.get(key));
        }

        @SuppressWarnings("unchecked")
        private boolean setKeyFromKey(String targetKey, String key) {
            if (!use(key)) return false;
            Object value = get(key);
            allconf.put(targetKey, value);
            return true;
        }

        @SuppressWarnings("unchecked")
        private <T> T setKeyToValue(String key, T value) {
            allconf.put(key, value);
            return value;
        }

        String provider;
        String providerLocationId;
        
        String user;
        String privateKeyData, privateKeyPassphrase, publicKeyData, password;
        /** @deprecated */
        String sshPublicKeyData, sshPrivateKeyData;
        List<JcloudsLocationCustomizer> customizers = new LinkedList<JcloudsLocationCustomizer>();

        String loginUser;
        String loginUser_privateKeyData, loginUser_privateKeyPassphrase, loginUser_password;
        
        @SuppressWarnings("unchecked")
        BrooklynJcloudsSetupHolder apply() {
            try {
                if (use("provider"))
                    provider = ""+get("provider");
                if (use("providerLocationId"))
                    providerLocationId = ""+get("providerLocationId");
                
                if (use("callerContext"))
                    _callerContext = get("callerContext");
                
                // align user (brooklyn standard) and userName (jclouds standard) fields
                // TODO don't default to root --> default later to what jclouds says
                if (!use("user") && use("userName"))
                    setKeyFromKey("user", "userName");
                if (use("user"))
                    user = (String) get("user");
                
                // perhaps deprecate supply of data (and of different root key?) to keep it simpler?
                if (truth(unusedConf.remove("publicKeyData")))
                    publicKeyData = sshPublicKeyData = ""+get("publicKeyData");
                if (truth(unusedConf.remove("privateKeyData")))
                    privateKeyData = sshPrivateKeyData = ""+get("privateKeyData");
                if (use("privateKeyFile")) {
                    if (truth(privateKeyData)) LOG.warn("privateKeyData and privateKeyFile both specified; preferring the former");
                    else
                        privateKeyData = sshPublicKeyData = setKeyToValue("sshPrivateKeyData", Files.toString(instance.getPrivateKeyFile(allconf), Charsets.UTF_8));
                }
                if (truth(unusedConf.remove("publicKeyFile"))) {
                    if (truth(publicKeyData)) LOG.warn("publicKeyData and publicKeyFile both specified; preferring the former");
                    else
                        publicKeyData = sshPublicKeyData = setKeyToValue("sshPublicKeyData", Files.toString(instance.getPublicKeyFile(allconf), Charsets.UTF_8));
                } else if (!truth(publicKeyData) && truth(get("privateKeyFile"))) {
                    File f = new File(""+get("privateKeyFile")+".pub");
                    if (f.exists()) {
                        LOG.debug("Loading publicKeyData from privateKeyFile + .pub");
                        publicKeyData = sshPublicKeyData = setKeyToValue("sshPublicKeyData", Files.toString(f, Charsets.UTF_8));
                    }
                }
                // deprecated:
                if (use("sshPublicKey")) 
                    publicKeyData = sshPublicKeyData = setKeyToValue("sshPublicKeyData", Files.toString(asFile(allconf.get("sshPublicKey")), Charsets.UTF_8));
                if (use("sshPrivateKey")) 
                    privateKeyData = sshPrivateKeyData = setKeyToValue("sshPrivateKeyData", Files.toString(asFile(allconf.get("sshPrivateKey")), Charsets.UTF_8));
                
                // are these two ever used:
                if (use("rootSshPrivateKey")) {
                    LOG.warn("Using deprecated property rootSshPrivateKey; use loginUser{,.privateKeyFile,...} instead");
                    setKeyToValue("rootSshPrivateKeyData", Files.toString(asFile(allconf.get("rootSshPrivateKey")), Charsets.UTF_8));
                }
                if (use("rootSshPublicKey")) {
                    LOG.warn("Using deprecated property rootSshPublicKey; use loginUser{,.publicKeyFile} instead (though public key often not needed)");
                    setKeyToValue("rootSshPublicKeyData", Files.toString(asFile(allconf.get("rootSshPublicKey")), Charsets.UTF_8));
                }
                // above replaced with below
                if (use("loginUser")) {
                    loginUser = ""+get("loginUser");
                    if (use("loginUser.privateKeyData")) {
                        loginUser_privateKeyData = ""+get("loginUser.privateKeyData");
                    }
                    if (use("loginUser.privateKeyFile")) {
                        if (loginUser_privateKeyData!=null) 
                            LOG.warn("loginUser private key data and private key file specified; preferring from file");
                        loginUser_privateKeyData = setKeyToValue("loginUser.privateKeyData", Files.toString(asFile(allconf.get("loginUser.privateKeyFile")), Charsets.UTF_8));
                    }
                    if (use("loginUser.privateKeyPassphrase")) {
                        LOG.warn("loginUser.privateKeyPassphrase not supported by jclouds; use a key which does not have a passphrase for the loginUser");
                        loginUser_privateKeyPassphrase = ""+get("loginUser.privateKeyPassphrase");
                    }
                    if (use("loginUser.password")) {
                        loginUser_password = ""+get("loginUser.password");
                    }
                    // these we ignore
                    use("loginUser.publicKeyData");
                    use("loginUser.publicKeyFile");
                    
                    if (loginUser.equals(user)) {
                        LOG.debug("Detected that jclouds loginUser is the same as regular user; we don't create this user");
                    }
                }
                
                if (use("dontCreateUser")) {
                    LOG.warn("Using deprecated property dontCreateUser; use login.user instead, set equal to the user to run as");
                    setKeyToValue("dontCreateUser", true);
                }
                // allows specifying a LoginCredentials object, for use by jclouds, if known for the VM (ie it is non-standard);
                if (use("customCredentials")) 
                    customCredentials = (LoginCredentials) get("customCredentials");
         
                // following values are copies pass-through, no change
                use("privateKeyPassphrase");
                use("password");
                use("noDefaultSshKeys");
                
                if (use("customizer"))
                    customizers.add((JcloudsLocationCustomizer) get("customizer"));
                if (use("customizers")) {
                    customizers.addAll((Collection<JcloudsLocationCustomizer>) get("customizers"));
                }
                
                return this;
            } catch (IOException e) {
                throw Throwables.propagate(e);
            }
        }
        
        String remove(String key) {
            return (String) unusedConf.remove(key);
        }
        
        void warnIfUnused(String context) {
            if (!unusedConf.isEmpty())
                LOG.debug("NOTE: unused flags passed to "+context+" in "+
                        elvis(allconf.get("providerLocationId"), allconf.get("provider"))+": "+unusedConf);
        }
        
        public String getCallerContext() {
            if (truth(_callerContext)) return _callerContext.toString();
            return "thread "+Thread.currentThread().getId();
        }

        public String setUser(String u) {
            String oldUser = user;
            user = u;
            allconf.put("user", u);
            allconf.put("userName", u);
            return oldUser;
        }

        public String setPassword(String password) {
            String oldPassword = this.password;
            this.password = password;
            allconf.put("password", password);
            return oldPassword;
        }

        public String setPrivateKeyData(String privateKeyData) {
            String oldPrivateKeyData = this.privateKeyData;
            this.privateKeyData = privateKeyData;
            allconf.put("privateKeyData", privateKeyData);
            allconf.put("sshPrivateKeyData", privateKeyData);
            return oldPrivateKeyData;
        }

        public void set(String key, String value) {
            allconf.put(key, value);
        }

        public boolean isDontCreateUser() {
            if (!use("dontCreateUser")) return false;
            Object v = get("dontCreateUser");
            if (v==null) return false;
            if (v instanceof Boolean) return ((Boolean)v).booleanValue();
            if (v instanceof CharSequence) return Boolean.parseBoolean(((CharSequence)v).toString());
            throw new IllegalArgumentException("dontCreateUser does not accept value '"+v+"' of type "+v.getClass());
        }
    }
    
    public static final Set<String> getAllSupportedProperties() {
        return ImmutableSet.<String>builder()
                .addAll(SUPPORTED_BASIC_PROPERTIES)
                .addAll(SUPPORTED_TEMPLATE_BUILDER_PROPERTIES.keySet())
                .addAll(SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES.keySet())
                .build();    
    }

    //FIXME use of this map and the unusedConf list, with the CredentialsFromEnv and JCloudsLocationFactory, seems overly complicated!
    //also, we need a way to define imageId (and others?) with a specific location
        
    public static final Collection<String> SUPPORTED_BASIC_PROPERTIES = ImmutableSet.of(
        "provider", "identity", "credential", "groupId", "providerLocationId", 
        "userName", "user", 
        "publicKeyFile", "privateKeyFile", "publicKeyData", "privateKeyData", "privateKeyPassphrase", 
        "loginUser", "loginUser.password", "loginUser.publicKeyFile", "loginUser.privateKeyFile", "loginUser.publicKeyData", "loginUser.privateKeyData", "loginUser.privateKeyPassphrase", 
        // deprecated:
        "sshPublicKey", "sshPrivateKey", 
        "rootSshPrivateKey", "rootSshPublicKey"
        );
    
    /** returns public key file, if one has been configured */
    public File getPublicKeyFile() { return getPublicKeyFile(getConf()); }

    public File getPublicKeyFile(Map allconf) { return elvis(asFile(allconf.get("publicKeyFile")), asFile(allconf.get("sshPublicKey"))); }
    
    /** returns private key file, if one has been configured */
    public File getPrivateKeyFile() { return getPrivateKeyFile(getConf()); }

    public File getPrivateKeyFile(Map allconf) { return elvis(asFile(allconf.get("privateKeyFile")), asFile(allconf.get("sshPrivateKey"))); }

    public ComputeService getComputeService() {
        return getComputeService(MutableMap.of());
    }
    public ComputeService getComputeService(Map flags) {
        BrooklynJcloudsSetupHolder setup = new BrooklynJcloudsSetupHolder(this).useConfig(flags).apply();
        return JcloudsUtil.buildOrFindComputeService(setup.allconf, setup.unusedConf);
    }
    
    /** returns the location ID used by the provider, if set, e.g. us-west-1 */
    public String getJcloudsProviderLocationId() {
        return (String) getConf().get("providerLocationId");
    }

    public Set<? extends ComputeMetadata> listNodes() {
        return listNodes(MutableMap.of());
    }
    public Set<? extends ComputeMetadata> listNodes(Map flags) {
        return getComputeService(flags).listNodes();
    }

    public JcloudsSshMachineLocation obtain(TemplateBuilder tb) throws NoMachinesAvailableException {
        return obtain(MutableMap.of(), tb);
    }
    public JcloudsSshMachineLocation obtain(Map flags, TemplateBuilder tb) throws NoMachinesAvailableException {
        Map flags2 = MutableMap.builder().putAll(flags).put("templateBuilder", tb).build();
        return obtain(flags2);
    }
    public JcloudsSshMachineLocation obtain() throws NoMachinesAvailableException {
        return obtain(MutableMap.of());
    }
    public JcloudsSshMachineLocation obtain(Map flags) throws NoMachinesAvailableException {
        BrooklynJcloudsSetupHolder setup = new BrooklynJcloudsSetupHolder(this).useConfig(flags).apply();
        
        String groupId = elvis(setup.remove("groupId"), generateGroupId());
        final ComputeService computeService = JcloudsUtil.buildOrFindComputeService(setup.allconf, setup.unusedConf);
        
        NodeMetadata node = null;
        try {
            LOG.info("Creating VM in "+
                    elvis(setup.providerLocationId, setup.provider)+" for "+setup.getCallerContext());

            Template template = buildTemplate(computeService, setup);

            setup.warnIfUnused("JcloudsLocation.obtain");            
    
            Set<? extends NodeMetadata> nodes = computeService.createNodesInGroup(groupId, 1, template);
            node = Iterables.getOnlyElement(nodes, null);
            LOG.debug("jclouds created {} for {}", node, setup.getCallerContext());
            if (node == null) {
                throw new IllegalStateException("No nodes returned by jclouds create-nodes in "
                        +setup.provider+"/"+setup.providerLocationId+" for "+setup.getCallerContext());
            }

            LoginCredentials expectedCredentials = setup.customCredentials;
            if (expectedCredentials!=null) {
                //set userName and other data, from these credentials
                Object oldUsername = setup.setUser(expectedCredentials.getUser());
                LOG.debug("node {} username {} / {} (customCredentials)", new Object[] { node, expectedCredentials.getUser(), oldUsername });
                if (truth(expectedCredentials.getPassword())) setup.setPassword(expectedCredentials.getPassword());
                if (truth(expectedCredentials.getPrivateKey())) setup.setPrivateKeyData(expectedCredentials.getPrivateKey());
            }
            if (expectedCredentials==null) {
                expectedCredentials = LoginCredentials.fromCredentials(node.getCredentials());
                String user = setup.user;
                LOG.debug("node {} username {} / {} (jclouds)", new Object[] { node, user, expectedCredentials.getUser() });
                if (truth(expectedCredentials.getUser())) {
                    if (user==null) {
                        setup.setUser(user = expectedCredentials.getUser());
                    } else if ("root".equals(user) && ROOT_ALIASES.contains(expectedCredentials.getUser())) {
                        // deprecated, we used to default username to 'root'; now we leave null, then use autodetected credentials if no user specified
                        // 
                        LOG.warn("overriding username 'root' in favour of '"+expectedCredentials.getUser()+"' at {}; this behaviour may be removed in future", node);
                        setup.setUser(user = expectedCredentials.getUser());
                    }
                }
                //override credentials
                String pkd = elvis(setup.privateKeyData, expectedCredentials.getPrivateKey());
                String pwd = elvis(setup.password, expectedCredentials.getPassword());
                if (user==null || (pkd==null && pwd==null)) {
                    String missing = (user==null ? "user" : "credential");
                    LOG.warn("Not able to determine "+missing+" for "+this+" at "+node+"; will likely fail subsequently");
                    expectedCredentials = null;
                } else {
                    LoginCredentials.Builder expectedCredentialsBuilder = LoginCredentials.builder().
                            user(user);
                    if (pkd!=null) expectedCredentialsBuilder.privateKey(pkd);
                    if (pwd!=null) expectedCredentialsBuilder.password(pwd);
                    expectedCredentials = expectedCredentialsBuilder.build();        
                }
            }
            if (expectedCredentials != null)
                node = NodeMetadataBuilder.fromNodeMetadata(node).credentials(expectedCredentials).build();
            else
                // only happens if something broke above...
                expectedCredentials = LoginCredentials.fromCredentials(node.getCredentials());
            
            // Wait for the VM to be reachable over SSH
            if (setup.get("waitForSshable") != null ? truth(setup.get("waitForSshable")) : true) {
                String vmIp = JcloudsUtil.getFirstReachableAddress(node);
                final NodeMetadata nodeRef = node;
                final LoginCredentials expectedCredentialsRef = expectedCredentials;
                
                long delayMs = -1;
                try {
                    delayMs = Time.parseTimeString(""+setup.get("waitForSshable"));
                } catch (Exception e) {}
                if (delayMs<=0) delayMs = START_SSHABLE_TIMEOUT;
                
                LOG.info("Started VM in {} for {}; waiting for it to be sshable on {}@{}",
                        new Object[] {
                                elvis(setup.get("providerLocationId"), setup.get("provider")),
                                setup.getCallerContext(), 
                                setup.user, 
                                vmIp
                        });
                boolean reachable = new Repeater()
                    .repeat()
                    .every(1,SECONDS)
                    .until(new Callable<Boolean>() {
                        public Boolean call() {
                            Statement statement = Statements.newStatementList(exec("hostname"));
                            // NB this assumes passwordless sudo !
                            ExecResponse response = computeService.runScriptOnNode(nodeRef.getId(), statement, 
                                    overrideLoginCredentials(expectedCredentialsRef));
                            return response.getExitStatus() == 0;
                        }})
                    .limitTimeTo(delayMs, MILLISECONDS)
                    .run();

                if (!reachable) {
                    throw new IllegalStateException("SSH failed for "+
                            setup.user+"@"+vmIp+" (for "+setup.getCallerContext()+") after waiting "+
                            Time.makeTimeString(delayMs));
                }
            }
            
            String vmHostname = getPublicHostname(node, setup);
            
            Map sshConfig = generateSshConfig(setup, node);

            if (LOG.isDebugEnabled())
                LOG.debug("creating JcloudsSshMachineLocation for {}@{} for {} with {}", 
                        new Object[] {
                                setup.user, 
                                vmHostname, 
                                setup.getCallerContext(), 
                                Entities.sanitize(sshConfig)
                        });
            JcloudsSshMachineLocation sshLocByHostname = new JcloudsSshMachineLocation(
                    MutableMap.builder()
                            .put("address", vmHostname) 
                            .put("displayName", vmHostname)
                            .put("user", setup.user)
                            .put("config", sshConfig)
                            .put("localTempDir", localTempDir)
                            .build(),
                    this, 
                    node);
                        
            sshLocByHostname.setParentLocation(this);
            vmInstanceIds.put(sshLocByHostname, node.getId());
            
            // Apply any optional app-specific customization.
            for (JcloudsLocationCustomizer customizer : setup.customizers) {
                customizer.customize(computeService, sshLocByHostname);
            }
            
            return sshLocByHostname;
        } catch (RunNodesException e) {
            if (e.getNodeErrors().size() > 0) {
                node = Iterables.get(e.getNodeErrors().keySet(), 0);
            }
            LOG.error("Failed to start VM: {}", e.getMessage());
            throw Throwables.propagate(e);
        } catch (Exception e) {
            LOG.error("Failed to start VM: {}", e.getMessage());
            LOG.info(Throwables.getStackTraceAsString(e));
            throw Throwables.propagate(e);
        } finally {
            //leave it open for reuse
//            computeService.getContext().close();
        }

    }

    public JcloudsSshMachineLocation rebindMachine(NodeMetadata metadata) throws NoMachinesAvailableException {
        return rebindMachine(MutableMap.of(), metadata);
    }
    public JcloudsSshMachineLocation rebindMachine(Map flags, NodeMetadata metadata) throws NoMachinesAvailableException {
        BrooklynJcloudsSetupHolder setup = new BrooklynJcloudsSetupHolder(this).useConfig(flags).apply();
        if (!setup.use("id")) setup.set("id", metadata.getId());
        setHostnameUpdatingCredentials(setup, metadata);
        return rebindMachine(setup.allconf);
    }
    
    protected void setHostnameUpdatingCredentials(BrooklynJcloudsSetupHolder setup, NodeMetadata metadata) {
        List<String> usersTried = new ArrayList<String>();
        
        if (truth(setup.user)) {
            if (setHostname(setup, metadata, false)) return;
            usersTried.add(setup.user);
        }
        
        LoginCredentials credentials = metadata.getCredentials();
        if (truth(credentials)) {
            if (truth(credentials.getUser())) setup.setUser(credentials.getUser());
            if (truth(credentials.getPrivateKey())) setup.setPrivateKeyData(credentials.getPrivateKey());
            if (setHostname(setup, metadata, false)) return;
            usersTried.add(setup.user);
        }
        
        for (String u: NON_ADDABLE_USERS) {
            setup.setUser(u);
            if (setHostname(setup, metadata, false)) {
                LOG.warn("Auto-detected user at "+metadata+" as "+setup.user+" (other attempted users "+usersTried+" cannot access it)");
                return;
            }
            usersTried.add(setup.user);
        }
        // just repeat, so we throw exception
        LOG.warn("Failed to log in to "+metadata+", tried as users "+usersTried+" (throwing original exception)");
        setHostname(setup, metadata, true);
    }
    
    protected boolean setHostname(BrooklynJcloudsSetupHolder setup, NodeMetadata metadata, boolean rethrow) {
        try {
            setup.set("hostname", getPublicHostname(metadata, setup));
            return true;
        } catch (Exception e) {
            if (rethrow) {
                LOG.warn("couldn't connect to "+metadata+" when trying to discover hostname (rethrowing): "+e);
                throw Throwables.propagate(e);                
            }
            return false;
        }
    }

    /**
     * Brings an existing machine with the given details under management.
     * <p>
     * Required fields are:
     * <ul>
     *   <li>id: the jclouds VM id, e.g. "eu-west-1/i-5504f21d" (NB this is @see JcloudsSshMachineLocation#getJcloudsId() not #getId())
     *   <li>hostname: the public hostname or IP of the machine, e.g. "ec2-176-34-93-58.eu-west-1.compute.amazonaws.com"
     *   <li>userName: the username for ssh'ing into the machine
     * <ul>
     */
    public JcloudsSshMachineLocation rebindMachine(Map flags) throws NoMachinesAvailableException {
        try {
            BrooklynJcloudsSetupHolder setup = new BrooklynJcloudsSetupHolder(this).useConfig(flags).apply();
            String id = (String) checkNotNull(setup.get("id"), "id");
            String hostname = (String) checkNotNull(setup.get("hostname"), "hostname");
            String user = (String) checkNotNull(setup.user, "user");
            
            LOG.info("Rebinding to VM {} ({}@{}), in jclouds location for provider {}", 
                    new Object[] {id, user, hostname, getProvider()});
            
                    
            ComputeService computeService = JcloudsUtil.buildComputeService(setup.allconf, setup.unusedConf);
            NodeMetadata node = computeService.getNodeMetadata(id);
            if (node == null) {
                throw new IllegalArgumentException("Node not found with id "+id);
            }
    
            if (truth(setup.privateKeyData)) {
                LoginCredentials expectedCredentials = LoginCredentials.fromCredentials(new Credentials(setup.user, setup.privateKeyData));
                //override credentials
                node = NodeMetadataBuilder.fromNodeMetadata(node).credentials(expectedCredentials).build();
            }
            // TODO confirm we can SSH ?

            Map sshConfig = generateSshConfig(setup, node);
            
            JcloudsSshMachineLocation sshLocByHostname = new JcloudsSshMachineLocation(
                    MutableMap.builder()
                            .put("address", hostname) 
                            .put("displayName", hostname)
                            .put("user", user)
                            .put("config", sshConfig)
                            .build(),
                    this, 
                    node);
                            
            sshLocByHostname.setParentLocation(this);
            vmInstanceIds.put(sshLocByHostname, node.getId());
                
            return sshLocByHostname;
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private Map generateSshConfig(BrooklynJcloudsSetupHolder setup, NodeMetadata node) throws IOException {
        Map sshConfig = Maps.newLinkedHashMap();
        
        if (setup.password != null) {
            sshConfig.put("password", setup.password);
        } else if (node!=null && node.getCredentials().getPassword() != null) {
            sshConfig.put("password", node.getCredentials().getPassword());
        }
        
        if (truth(setup.privateKeyData)) {
            sshConfig.put("privateKeyData", setup.privateKeyData);
        } else if (truth(setup.allconf.get("sshPrivateKeyData"))) {
            LOG.warn("Using legacy sshPrivateKeyData but not privateKeyData");
            Object d = setup.allconf.get("sshPrivateKeyData");
            sshConfig.put("privateKeyData", d);
            sshConfig.put("privateKey", d);
            sshConfig.put("sshPrivateKeyData", d);
        } else if (truth(getPrivateKeyFile())) {
            LOG.warn("Using legacy keyFiles but not privateKeyData");
            sshConfig.put("keyFiles", ImmutableList.of(getPrivateKeyFile().getCanonicalPath()));
        }
        
        if (truth(setup.allconf.get("privateKeyPassphrase"))) {
            // NB: not supported in jclouds
            sshConfig.put("privateKeyPassphrase", setup.privateKeyPassphrase);
        }

        return sshConfig;
    }
    
    public static String generateGroupId() {
        // In jclouds 1.5, there are strict rules for group id: it must be DNS compliant, and no more than 15 characters
        // TODO surely this can be overridden!  it's so silly being so short in common places ... or at least set better metadata?
        String user = System.getProperty("user.name");
        String rand = Identifiers.makeRandomId(6);
        String result = "br-"+Strings.maxlen(user, 4)+"-"+rand;
        return result.toLowerCase();
    }

    public static File asFile(Object o) {
        if (o instanceof File) return (File)o;
        if (o == null) return null;
        return new File(o.toString());
    }

    public static String fileAsString(Object o) {
        if (o instanceof String) return (String)o;
        if (o instanceof File) return ((File)o).getAbsolutePath();
        if (o==null) return null;
        return o.toString();
    }

    public void release(SshMachineLocation machine) {
        String instanceId = vmInstanceIds.remove(machine);
        if (!truth(instanceId)) {
            throw new IllegalArgumentException("Unknown machine "+machine);
        }
        
        LOG.info("Releasing machine {} in {}, instance id {}", new Object[] {machine, this, instanceId});
        
        removeChildLocation(machine);
        ComputeService computeService = null;
        try {
            computeService = JcloudsUtil.buildOrFindComputeService(getConf());
            computeService.destroyNode(instanceId);
        } catch (Exception e) {
            LOG.error("Problem releasing machine "+machine+" in "+this+", instance id "+instanceId+
                    "; discarding instance and continuing...", e);
            Throwables.propagate(e);
        } finally {
        /*
         //don't close
            if (computeService != null) {
                try {
                    computeService.getContext().close();
                } catch (Exception e) {
                    LOG.error "Problem closing compute-service's context; continuing...", e
                }
            }
         */
        }
    }
    
    private static interface CustomizeTemplateBuilder {
        void apply(TemplateBuilder tb, Map props, Object v);
    }
    
    private static interface CustomizeTemplateOptions {
        void apply(TemplateOptions tb, Map props, Object v);
    }
    
    /** note, it is important these be written in correct camel case, so the routines
     *  which convert it to "min-ram" syntax and MIN_RAM syntax are correct */
    
    public static final Map<String,CustomizeTemplateBuilder> SUPPORTED_TEMPLATE_BUILDER_PROPERTIES = ImmutableMap.<String,CustomizeTemplateBuilder>builder()
            .put("minRam", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.minRam(TypeCoercions.coerce(v, Integer.class));
                    }})
            .put("minCores", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.minCores(TypeCoercions.coerce(v, Double.class));
                    }})
            .put("hardwareId", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.hardwareId(((CharSequence)v).toString());
                    }})
            .put("imageId", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.imageId(((CharSequence)v).toString());
                    }})
            .put("imageDescriptionRegex", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.imageDescriptionMatches(((CharSequence)v).toString());
                    }})
            .put("imageNameRegex", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.imageNameMatches(((CharSequence)v).toString());
                    }})
            .put("templateSpec", new CustomizeTemplateBuilder() {
                public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.from(TemplateBuilderSpec.parse(((CharSequence)v).toString()));
                    }})
            .put("defaultImageId", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        /* deferred */
                    }})
            .put("templateBuilder", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        /* deferred */
                    }})
                    
             // following are deprecated in 0.4.0, kept for backwards compatibility:
            .put("imageDescriptionPattern", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.imageDescriptionMatches(((CharSequence)v).toString());
                    }})
            .put("imageNamePattern", new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, Map props, Object v) {
                        tb.imageNameMatches(((CharSequence)v).toString());
                    }})
            .build();

    public static final Map<String,CustomizeTemplateOptions> SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES = ImmutableMap.<String,CustomizeTemplateOptions>builder()
            .put("securityGroups", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        if (t instanceof EC2TemplateOptions) {
                            String[] securityGroups = toStringArray(v);
                            ((EC2TemplateOptions)t).securityGroups(securityGroups);
                        // jclouds 1.5, also support:
                        } else if (t instanceof NovaTemplateOptions) {
                            String[] securityGroups = toStringArray(v);
                            ((NovaTemplateOptions)t).securityGroupNames(securityGroups);
                        } else {
                            LOG.info("ignoring securityGroups({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }
                    }})
            .put("userData", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        /* expects UUENCODED byte array or string */
                        if (t instanceof EC2TemplateOptions) {
                            byte[] bytes = toByteArray(v);
                            ((EC2TemplateOptions)t).userData(bytes);
                        } else {
                            LOG.info("ignoring userData({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }
                    }})
            .put("inboundPorts", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        int[] inboundPorts = toIntArray(v);
                        if (LOG.isDebugEnabled()) LOG.debug("opening inbound ports {} for {}", Arrays.toString(inboundPorts), t);
                        t.inboundPorts(inboundPorts);
                    }})
            .put("userMetadata", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        t.userMetadata(toMapStringString(v));
                    }})
            .put("rootSshPublicKeyData", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        t.authorizePublicKey(((CharSequence)v).toString());
                    }})
            .put("sshPublicKey", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        /* special; not included here */
                    }})
            .put("userName", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        /* special; not included here */
                    }})
            .put("runAsRoot", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        t.runAsRoot((Boolean)v);
                    }})
            .put("loginUser", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        t.overrideLoginUser(((CharSequence)v).toString());
                    }})
            .put("loginUser.password", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        t.overrideLoginPassword(((CharSequence)v).toString());
                    }})
            .put("loginUser.privateKeyData", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        t.overrideLoginPrivateKey(((CharSequence)v).toString());
                    }})
            .put("overrideLoginUser", new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, Map props, Object v) {
                        LOG.warn("Using deprecated property overrideLoginUser; use loginUser instead");
                        t.overrideLoginUser(((CharSequence)v).toString());
                    }})
            .build();

    private static boolean listedAvailableTemplatesOnNoSuchTemplate = false;
    
    private Template buildTemplate(ComputeService computeService, BrooklynJcloudsSetupHolder setup) {
        TemplateBuilder templateBuilder = (TemplateBuilder) setup.get("templateBuilder");
        if (templateBuilder==null)
            templateBuilder = new PortableTemplateBuilder();
        else
            LOG.debug("jclouds using templateBuilder {} as base for provisioning in {} for {}", new Object[] {templateBuilder, this, setup.getCallerContext()});

        if (setup.providerLocationId!=null) {
            templateBuilder.locationId(setup.providerLocationId);
        }
        
        for (Map.Entry<String, CustomizeTemplateBuilder> entry : SUPPORTED_TEMPLATE_BUILDER_PROPERTIES.entrySet()) {
            String name = entry.getKey();
            CustomizeTemplateBuilder code = entry.getValue();
            if (setup.use(name))
                code.apply(templateBuilder, setup.allconf, setup.get(name));
        }

        if (templateBuilder instanceof PortableTemplateBuilder) {
            ((PortableTemplateBuilder)templateBuilder).attachComputeService(computeService);
            // do the default last, and only if nothing else specified (guaranteed to be a PTB if nothing else specified)
            if (setup.use("defaultImageId")) {
                if (((PortableTemplateBuilder)templateBuilder).isBlank()) {
                    CharSequence defaultImageId = (CharSequence) setup.get("defaultImageId");
                    templateBuilder.imageId(defaultImageId.toString());
                }
            }
        }

        // Finally, apply any optional app-specific customization.
        for (JcloudsLocationCustomizer customizer : setup.customizers) {
            customizer.customize(computeService, templateBuilder);
        }
        
        Template template;
        try {
            template = templateBuilder.build();
            if (template==null) throw new NullPointerException("No template found (templateBuilder.build returned null)");
            LOG.debug(""+this+" got template "+template+" (image "+template.getImage()+")");
            if (template.getImage()==null) throw new NullPointerException("Template does not contain an image (templateBuilder.build returned invalid template)");
            if (isBadTemplate(template.getImage())) {
                // release candidates might break things :(   TODO get the list and score them
                if (templateBuilder instanceof PortableTemplateBuilder) {
                    if (((PortableTemplateBuilder)templateBuilder).getOsFamily()==null) {
                        templateBuilder.osFamily(OsFamily.UBUNTU).osVersionMatches("11.04").os64Bit(true);
                        Template template2 = templateBuilder.build();
                        if (template2!=null) {
                            LOG.debug(""+this+" preferring template {} over {}", template2, template);
                            template = template2;
                        }
                    }
                }
            }
        } catch (Exception e) {
            try {
                synchronized (this) {
                    // delay subsequent log.warns (put in synch block) so the "Loading..." message is obvious
                    LOG.warn("Unable to match required VM template constraints "+templateBuilder+" when trying to provision VM in "+this+" (rethrowing): "+e);
                    if (!listedAvailableTemplatesOnNoSuchTemplate) {
                        listedAvailableTemplatesOnNoSuchTemplate = true;
                        LOG.info("Loading available images at "+this+" for reference...");
                        Map m1 = new LinkedHashMap(setup.allconf);
                        if (m1.remove("imageId")!=null)
                            // don't apply default filters if user has tried to specify an image ID
                            m1.put("anyOwner", true);
                        ComputeService computeServiceLessRestrictive = JcloudsUtil.buildOrFindComputeService(m1, new MutableMap());
                        Set<? extends Image> imgs = computeServiceLessRestrictive.listImages();
                        LOG.info(""+imgs.size()+" available images at "+this);
                        for (Image img: imgs) {
                            LOG.info(" Image: "+img);
                        }
                    }
                }
            } catch (Exception e2) {
                LOG.warn("Error loading available images to report (following original error matching template which will be rethrown): "+e2, e2);
            }
            throw new IllegalStateException("Unable to match required VM template constraints "+templateBuilder+" when trying to provision VM in "+this+". See list of images in log.", e);
        }
        TemplateOptions options = template.getOptions();
        
        for (Map.Entry<String, CustomizeTemplateOptions> entry : SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES.entrySet()) {
            String name = entry.getKey();
            CustomizeTemplateOptions code = entry.getValue();
            if (setup.use(name))
                code.apply(options, setup.allconf, setup.get(name));
        }
                
        // Setup the user
        
        //NB: we ignore private key here because, by default we probably should not be installing it remotely;
        //also, it may not be valid for first login (it is created before login e.g. on amazon, so valid there;
        //but not elsewhere, e.g. on rackspace)
        if (truth(setup.user) && !NON_ADDABLE_USERS.contains(setup.user) && 
                !setup.user.equals(setup.loginUser) && !truth(setup.isDontCreateUser())) {
            // create the user, if it's not the login user and not a known root-level user
            // by default we now give these users sudo privileges.
            // if you want something else, that can be specified manually, 
            // e.g. using jclouds UserAdd.Builder, with RunScriptOnNode, or template.options.runScript(xxx)
            // (if that is a common use case, we could expose a property here)
            // note AdminAccess requires _all_ fields set, due to http://code.google.com/p/jclouds/issues/detail?id=1095
            AdminAccess.Builder adminBuilder = AdminAccess.builder().
                    adminUsername(setup.user).
                    grantSudoToAdminUser(true);
            adminBuilder.adminPassword(setup.use("password") ? setup.password : Identifiers.makeRandomId(12));
            if (setup.publicKeyData!=null)
                adminBuilder.authorizeAdminPublicKey(true).adminPublicKey(setup.publicKeyData);
            else
                adminBuilder.authorizeAdminPublicKey(false).adminPublicKey("ignored").lockSsh(true);
            adminBuilder.installAdminPrivateKey(false).adminPrivateKey("ignored");
            adminBuilder.resetLoginPassword(true).loginPassword(Identifiers.makeRandomId(12));
            adminBuilder.lockSsh(true);
            options.runScript(adminBuilder.build());
        } else if (truth(setup.publicKeyData)) {
            // don't create the user, but authorize the public key for the default user
            options.authorizePublicKey(setup.publicKeyData);
        }
        
        // Finally, apply any optional app-specific customization.
        for (JcloudsLocationCustomizer customizer : setup.customizers) {
            customizer.customize(computeService, options);
        }
        
        LOG.debug("jclouds using template {} / options {} to provision machine in {} for {}", new Object[] {template, options, this, setup.getCallerContext()});
        return template;
    }

    // TODO we really need a better way to decide which images are preferred
    // though to be fair this is similar to jclouds strategies
    // we fall back to the "bad" images (^^^ above) if we can't find a good one above
    // ---
    // but in practice in AWS images name "rc-" and from "alphas" break things badly
    // (apt repos don't work, etc)
    private boolean isBadTemplate(Image image) {
        String name = image.getName();
        if (name != null && name.contains(".rc-")) return true;
        OperatingSystem os = image.getOperatingSystem();
        if (os!=null) {
            String description = os.getDescription();
            if (description != null && description.contains("-alpha"))
                return true;
        }
        return false;
    }

    private String getPublicHostname(NodeMetadata node, BrooklynJcloudsSetupHolder setup) {
        if ("aws-ec2".equals(setup != null ? setup.get("provider") : null)) {
            String vmIp = null;
            try {
                vmIp = JcloudsUtil.getFirstReachableAddress(node);
            } catch (Exception e) {
                LOG.warn("Error reaching aws-ec2 instance on port 22; falling back to jclouds metadata for address", e);
            }
            if (vmIp != null) {
                try {
                    return getPublicHostnameAws(vmIp, setup);
                } catch (Exception e) {
                    LOG.warn("Error querying aws-ec2 instance over ssh for its hostname; falling back to first reachable IP", e);
                    return vmIp;
                }
            }
        }
        
        return getPublicHostnameGeneric(node, setup);
    }
    
    private String getPublicHostnameGeneric(NodeMetadata node, @Nullable BrooklynJcloudsSetupHolder setup) {
        //prefer the public address to the hostname because hostname is sometimes wrong/abbreviated
        //(see that javadoc; also e.g. on rackspace, the hostname lacks the domain)
        //TODO would it be better to prefer hostname, but first check that it is resolvable? 
        if (truth(node.getPublicAddresses())) {
            return node.getPublicAddresses().iterator().next();
        } else if (truth(node.getHostname())) {
            return node.getHostname();
        } else if (truth(node.getPrivateAddresses())) {
            return node.getPrivateAddresses().iterator().next();
        } else {
            return null;
        }
    }
    
    private String getPublicHostnameAws(String ip, BrooklynJcloudsSetupHolder setup) {
        SshMachineLocation sshLocByIp = null;
        try {
            Map sshConfig = generateSshConfig(setup, null);
            
            // TODO messy way to get an SSH session 
            sshLocByIp = new SshMachineLocation(MutableMap.of("address", ip, "user", setup.user, "config", sshConfig));
            
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errStream = new ByteArrayOutputStream();
            int exitcode = sshLocByIp.run(
                    MutableMap.of("out", outStream, "err", errStream), 
                    "echo `curl --silent --retry 20 http://169.254.169.254/latest/meta-data/public-hostname`; exit");
            String outString = new String(outStream.toByteArray());
            String[] outLines = outString.split("\n");
            for (String line : outLines) {
                if (line.startsWith("ec2-")) return line.trim();
            }
            throw new IllegalStateException("Could not obtain hostname for vm "+ip+"; exitcode="+exitcode+"; stdout="+outString+"; stderr="+new String(errStream.toByteArray()));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            Closeables.closeQuietly(sshLocByIp);
        }
    }
    
    public static class JcloudsSshMachineLocation extends SshMachineLocation {
        final JcloudsLocation parent;
        final NodeMetadata node;
        private final RunScriptOnNode.Factory runScriptFactory;
        
        public JcloudsSshMachineLocation(Map flags, JcloudsLocation parent, NodeMetadata node) {
            super(flags);
            this.parent = parent;
            this.node = node;
            
            ComputeServiceContext context = parent.getComputeService().getContext();
            runScriptFactory = context.utils().injector().getInstance(RunScriptOnNode.Factory.class);
        }
        
        public NodeMetadata getNode() {
            return node;
        }
        
        public JcloudsLocation getParent() {
            return parent;
        }
        
        /** returns the hostname for use by peers in the same subnet,
         * defaulting to public hostname if nothing special
         * <p>
         * for use e.g. in clouds like amazon where other machines
         * in the same subnet need to use a different IP
         */
        public String getSubnetHostname() {
            if (truth(node.getPrivateAddresses()))
                return node.getPrivateAddresses().iterator().next();
            return parent.getPublicHostname(node, null);
        }
        
        public String getJcloudsId() {
            return node.getId();
        }
        
        /** executes the given statements on the server using jclouds ScriptBuilder,
         * wrapping in a script which is polled periodically.
         * the output is returned once the script completes (disadvantage compared to other methods)
         * but the process is nohupped and the SSH session is not kept, 
         * so very useful for long-running processes
         */
        public ListenableFuture<ExecResponse> submitRunScript(String ...statements) {
            return submitRunScript(new InterpretableStatement(statements));
        }
        public ListenableFuture<ExecResponse> submitRunScript(Statement script) {
            return submitRunScript(script, new RunScriptOptions());            
        }
        public ListenableFuture<ExecResponse> submitRunScript(Statement script, RunScriptOptions options) {
            return runScriptFactory.submit(node, script, options);
        }
        /** uses submitRunScript to execute the commands, and throws error if it fails or returns non-zero */
        public void execRemoteScript(String ...commands) {
            try {
                ExecResponse result = submitRunScript(commands).get();
                if (result.getExitStatus()!=0)
                    throw new IllegalStateException("Error running remote commands (code "+result.getExitStatus()+"): "+commands);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw Throwables.propagate(e);
            } catch (ExecutionException e) {
                throw Throwables.propagate(e);
            }
        }
    
        /**
         * Retrieves the password for this VM, if one exists. The behaviour/implementation is different for different clouds.
         * e.g. on Rackspace, the password for a windows VM is available immediately; on AWS-EC2, for a Windows VM you need 
         * to poll repeatedly until the password is available which can take up to 15 minutes.
         */
        public String waitForPassword() {
            // TODO Hacky; don't want aws specific stuff here but what to do?!
            if (parent.getProvider().equals("aws-ec2")) {
                try {
                    return JcloudsUtil.waitForPasswordOnAws(parent.getComputeService(), node, 15, TimeUnit.MINUTES);
                } catch (TimeoutException e) {
                    throw Throwables.propagate(e);
                }
            } else {
                LoginCredentials credentials = node.getCredentials();
                return (credentials != null) ? credentials.getPassword() : null;
            }
        }

        @Override
        public OsDetails getOsDetails() {
            if (node.getOperatingSystem() != null) {
                return new BasicOsDetails(
                        node.getOperatingSystem().getName() != null
                                ? node.getOperatingSystem().getName() : "linux",
                        node.getOperatingSystem().getArch() != null
                                ? node.getOperatingSystem().getArch() : BasicOsDetails.OsArchs.I386,
                        node.getOperatingSystem().getVersion() != null
                                ? node.getOperatingSystem().getVersion() : "unknown",
                        node.getOperatingSystem().is64Bit());
            }
            return super.getOsDetails();
        }
    }

    private static double toDouble(Object v) {
        if (v instanceof Number) {
            return ((Number)v).doubleValue();
        } else {
            throw new IllegalArgumentException("Invalid type for double: "+v+" of type "+v.getClass());
        }
    }

    private static int[] toIntArray(Object v) {
        int[] result;
        if (v instanceof Iterable) {
            result = new int[Iterables.size((Iterable)v)];
            int i = 0;
            for (Object o : (Iterable)v) {
                result[i++] = (Integer) o;
            }
        } else if (v instanceof int[]) {
            result = (int[]) v;
        } else if (v instanceof Object[]) {
            result = new int[((Object[])v).length];
            for (int i = 0; i < result.length; i++) {
                result[i] = (Integer) ((Object[])v)[i];
            }
        } else if (v instanceof Integer) {
            result = new int[] {(Integer)v};
        } else {
            throw new IllegalArgumentException("Invalid type for int[]: "+v+" of type "+v.getClass());
        }
        return result;
    }

    private static String[] toStringArray(Object v) {
        Collection<String> result = Lists.newArrayList();
        if (v instanceof Iterable) {
            int i = 0;
            for (Object o : (Iterable)v) {
                result.add(o.toString());
            }
        } else if (v instanceof Object[]) {
            for (int i = 0; i < ((Object[])v).length; i++) {
                result.add(((Object[])v)[i].toString());
            }
        } else if (v instanceof String) {
            result.add((String) v);
        } else {
            throw new IllegalArgumentException("Invalid type for String[]: "+v+" of type "+v.getClass());
        }
        return result.toArray(new String[0]);
    }
    
    private static byte[] toByteArray(Object v) {
        if (v instanceof byte[]) {
            return (byte[]) v;
        } else if (v instanceof CharSequence) {
            return v.toString().getBytes();
        } else {
            throw new IllegalArgumentException("Invalid type for byte[]: "+v+" of type "+v.getClass());
        }
    }
    
    // Handles GString
    private static Map<String,String> toMapStringString(Object v) {
        if (v instanceof Map<?,?>) {
            Map<String,String> result = Maps.newLinkedHashMap();
            for (Map.Entry<?,?> entry : ((Map<?,?>)v).entrySet()) {
                String key = ((CharSequence)entry.getKey()).toString();
                String value = ((CharSequence)entry.getValue()).toString();
                result.put(key, value);
            }
            return result;
        } else if (v instanceof CharSequence) {
            return KeyValueParser.parseMap(v.toString());
        } else {
            throw new IllegalArgumentException("Invalid type for Map<String,String>: "+v+" of type "+v.getClass());
        }
    }
}
