package brooklyn.location.jclouds;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.jclouds.abiquo.compute.options.AbiquoTemplateOptions;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.compute.domain.OperatingSystem;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.domain.TemplateBuilderSpec;
import org.jclouds.compute.functions.Sha512Crypt;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.domain.Credentials;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.ec2.compute.options.EC2TemplateOptions;
import org.jclouds.openstack.nova.v2_0.compute.options.NovaTemplateOptions;
import org.jclouds.rest.AuthorizationException;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.StatementList;
import org.jclouds.scriptbuilder.domain.Statements;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.jclouds.scriptbuilder.statements.login.ReplaceShadowPasswordEntry;
import org.jclouds.scriptbuilder.statements.ssh.AuthorizeRSAPublicKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.config.ConfigUtils;
import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationSpec;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocationConfigUtils;
import brooklyn.location.basic.LocationCreationUtils;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.cloud.AbstractCloudMachineProvisioningLocation;
import brooklyn.location.jclouds.templates.PortableTemplateBuilder;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.internal.Repeater;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.ssh.IptablesCommands;
import brooklyn.util.ssh.IptablesCommands.Chain;
import brooklyn.util.ssh.IptablesCommands.Policy;
import brooklyn.util.ssh.IptablesCommands.Protocol;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.KeyValueParser;
import brooklyn.util.text.Strings;
import brooklyn.util.text.TemplateProcessor;
import brooklyn.util.time.Time;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.common.primitives.Ints;

/**
 * For provisioning and managing VMs in a particular provider/region, using jclouds.
 * Configuration flags are defined in {@link JcloudsLocationConfig}.
 */
public class JcloudsLocation extends AbstractCloudMachineProvisioningLocation implements JcloudsLocationConfig {

    // TODO After converting from Groovy to Java, this is now very bad code! It relies entirely on putting 
    // things into and taking them out of maps; it's not type-safe, and it's thus very error-prone.
    // In Groovy, that's considered ok but not in Java. 

    // TODO test (and fix) ability to set config keys from flags

    // TODO need a way to define imageId (and others?) with a specific location

    // TODO we say config is inherited, but it isn't the case for many "deep" / jclouds properties
    // e.g. when we pass getConfigBag() in and decorate it with additional flags
    // (inheritance only works when we call getConfig in this class)
    
    public static final Logger LOG = LoggerFactory.getLogger(JcloudsLocation.class);
        
    public static final String ROOT_USERNAME = "root";
    /** these userNames are known to be the preferred/required logins in some common/default images 
     *  where root@ is not allowed to log in */
    public static final List<String> ROOT_ALIASES = ImmutableList.of("ubuntu", "ec2-user");
    public static final List<String> COMMON_USER_NAMES_TO_TRY = ImmutableList.<String>builder().add(ROOT_USERNAME).addAll(ROOT_ALIASES).add("admin").build();
    
    private static final Pattern LIST_PATTERN = Pattern.compile("^\\[(.*)\\]$");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("^\\d*$");
    
    private final Map<String,Map<String, ? extends Object>> tagMapping = Maps.newLinkedHashMap();
    private final Map<JcloudsSshMachineLocation,String> vmInstanceIds = Maps.newLinkedHashMap();

    public JcloudsLocation() {
       super();
    }
    
    /** typically wants at least ACCESS_IDENTITY and ACCESS_CREDENTIAL */
    public JcloudsLocation(Map<?,?> conf) {
       super(conf);
    }

    @Override
    public void configure(Map properties) {
        super.configure(properties);
        
        if (getConfigBag().containsKey("providerLocationId")) {
            LOG.warn("Using deprecated 'providerLocationId' key in "+this);
            if (!getConfigBag().containsKey(CLOUD_REGION_ID))
                getConfigBag().put(CLOUD_REGION_ID, (String)getConfigBag().getStringKey("providerLocationId"));
        }
        
        if (!truth(name)) {
            name = elvis(getProvider(), "unknown") +
                   (truth(getRegion()) ? ":"+getRegion() : "") +
                   (truth(getEndpoint()) ? ":"+getEndpoint() : "");
        }
        
        setCreationString(getConfigBag());
    }
    
    public JcloudsLocation newSubLocation(Map<?,?> newFlags) {
        return LocationCreationUtils.newSubLocation(newFlags, this);
    }

    @Override
    public String toString() {
        Object identity = getIdentity();
        String configDescription = getConfigBag().getDescription();
        if (configDescription!=null && configDescription.startsWith(getClass().getSimpleName()))
            return configDescription;
        return getClass().getSimpleName()+"["+name+":"+(identity != null ? identity : null)+
                (configDescription!=null ? "/"+configDescription : "") + "]";
    }

    @Override
    public String toVerboseString() {
        return Objects.toStringHelper(this).omitNullValues()
                .add("id", getId()).add("name", getDisplayName()).add("identity", getIdentity())
                .add("description", getConfigBag().getDescription()).add("provider", getProvider())
                .add("region", getRegion()).add("endpoint", getEndpoint())
                .toString();
    }

    public String getProvider() {
        return getConfig(CLOUD_PROVIDER);
    }

    public String getIdentity() {
        return getConfig(ACCESS_IDENTITY);
    }
    
    public String getCredential() {
        return getConfig(ACCESS_CREDENTIAL);
    }
    
    /** returns the location ID used by the provider, if set, e.g. us-west-1 */
    public String getRegion() {
        return getConfig(CLOUD_REGION_ID);
    }

    /** @deprecated since 0.5.0 use getRegion */
    public String getJcloudsProviderLocationId() {
        return getConfig(CLOUD_REGION_ID);
    }

    public String getEndpoint() {
        return LocationConfigUtils.getConfigCheckingDeprecatedAlternatives(getConfigBag(), 
                CLOUD_ENDPOINT, JCLOUDS_KEY_ENDPOINT);
    }

    public String getUser(ConfigBag config) {
        return LocationConfigUtils.getConfigCheckingDeprecatedAlternatives(config, 
                USER, JCLOUDS_KEY_USERNAME);
    }
    
    protected Collection<JcloudsLocationCustomizer> getCustomizers(ConfigBag setup) {
        JcloudsLocationCustomizer customizer = setup.get(JCLOUDS_LOCATION_CUSTOMIZER);
        Collection<JcloudsLocationCustomizer> customizers = setup.get(JCLOUDS_LOCATION_CUSTOMIZERS);
        if (customizer==null && customizers==null) return Collections.emptyList();
        List<JcloudsLocationCustomizer> result = new ArrayList<JcloudsLocationCustomizer>();
        if (customizer!=null) result.add(customizer);
        if (customizers!=null) result.addAll(customizers);
        return result;
    }

    public void setDefaultImageId(String val) {
        setConfig(DEFAULT_IMAGE_ID, val);
    }

    // TODO remove tagMapping, or promote it
    // (i think i favour removing it, letting the config come in from the entity)
    
    public void setTagMapping(Map<String,Map<String, ? extends Object>> val) {
        tagMapping.clear();
        tagMapping.putAll(val);
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
    
    public static final Set<ConfigKey<?>> getAllSupportedProperties() {
        Set<String> configsOnClass = Sets.newLinkedHashSet(
            Iterables.transform(ConfigUtils.getStaticKeysOnClass(JcloudsLocation.class),
                new Function<HasConfigKey<?>,String>() {
                    @Override @Nullable
                    public String apply(@Nullable HasConfigKey<?> input) {
                        return input.getConfigKey().getName();
                    }
                }));
        Set<ConfigKey<?>> configKeysInList = ImmutableSet.<ConfigKey<?>>builder()
                .addAll(SUPPORTED_TEMPLATE_BUILDER_PROPERTIES.keySet())
                .addAll(SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES.keySet())
                .build();
        Set<String> configsInList = Sets.newLinkedHashSet(
            Iterables.transform(configKeysInList,
            new Function<ConfigKey<?>,String>() {
                @Override @Nullable
                public String apply(@Nullable ConfigKey<?> input) {
                    return input.getName();
                }
            }));
        
        SetView<String> extrasInList = Sets.difference(configsInList, configsOnClass);
        // notInList is normal
        if (!extrasInList.isEmpty())
            LOG.warn("JcloudsLocation supported properties differs from config defined on class: " + extrasInList);
        return Collections.unmodifiableSet(configKeysInList);
    }

    public ComputeService getComputeService() {
        return getComputeService(MutableMap.of());
    }
    public ComputeService getComputeService(Map<?,?> flags) {
        return JcloudsUtil.findComputeService((flags==null || flags.isEmpty()) ? getConfigBag() :
            ConfigBag.newInstanceExtending(getConfigBag(), flags));
    }
    
    public Set<? extends ComputeMetadata> listNodes() {
        return listNodes(MutableMap.of());
    }
    public Set<? extends ComputeMetadata> listNodes(Map<?,?> flags) {
        return getComputeService(flags).listNodes();
    }

    /** attaches a string describing where something is being created 
     * (provider, region/location and/or endpoint, callerContext) */
    protected void setCreationString(ConfigBag config) {
        config.setDescription(elvis(config.get(CLOUD_PROVIDER), "unknown")+
                (config.containsKey(CLOUD_REGION_ID) ? ":"+config.get(CLOUD_REGION_ID) : "")+
                (config.containsKey(CLOUD_ENDPOINT) ? ":"+config.get(CLOUD_ENDPOINT) : "")+
                (config.containsKey(CALLER_CONTEXT) ? "@"+config.get(CALLER_CONTEXT) : ""));
    }

    // ----------------- obtaining a new machine ------------------------
    
    public JcloudsSshMachineLocation obtain() throws NoMachinesAvailableException {
        return obtain(MutableMap.of());
    }
    public JcloudsSshMachineLocation obtain(TemplateBuilder tb) throws NoMachinesAvailableException {
        return obtain(MutableMap.of(), tb);
    }
    public JcloudsSshMachineLocation obtain(Map<?,?> flags, TemplateBuilder tb) throws NoMachinesAvailableException {
        return obtain(MutableMap.builder().putAll(flags).put(TEMPLATE_BUILDER, tb).build());
    }
    /** core method for obtaining a VM using jclouds;
     * Map should contain CLOUD_PROVIDER and CLOUD_ENDPOINT or CLOUD_REGION, depending on the cloud,
     * as well as ACCESS_IDENTITY and ACCESS_CREDENTIAL,
     * plus any further properties to specify e.g. images, hardware profiles, accessing user
     * (for initial login, and a user potentially to create for subsequent ie normal access) */
    public JcloudsSshMachineLocation obtain(Map<?,?> flags) throws NoMachinesAvailableException {
        ConfigBag setup = ConfigBag.newInstanceExtending(getConfigBag(), flags);
        setCreationString(setup);
        
        final ComputeService computeService = JcloudsUtil.findComputeService(setup);
        String groupId = elvis(setup.get(GROUP_ID), new CloudMachineNamer(setup).generateNewGroupId());
        NodeMetadata node = null;
        JcloudsSshMachineLocation sshMachineLocation = null;
        
        try {
            LOG.info("Creating VM in "+setup.getDescription()+" for "+this);

            Template template = buildTemplate(computeService, setup);
            LoginCredentials initialCredentials = initUserTemplateOptions(template, setup);
            for (JcloudsLocationCustomizer customizer : getCustomizers(setup)) {
                customizer.customize(computeService, template.getOptions());
            }
            LOG.debug("jclouds using template {} / options {} to provision machine in {}", new Object[] {
                    template, template.getOptions(), setup.getDescription()});

            if (!setup.getUnusedConfig().isEmpty())
                LOG.debug("NOTE: unused flags passed to obtain VM in "+setup.getDescription()+": "+
                        setup.getUnusedConfig());
            
            Set<? extends NodeMetadata> nodes = computeService.createNodesInGroup(groupId, 1, template);
            node = Iterables.getOnlyElement(nodes, null);
            LOG.debug("jclouds created {} for {}", node, setup.getDescription());
            if (node == null)
                throw new IllegalStateException("No nodes returned by jclouds create-nodes in " + setup.getDescription());

            LoginCredentials customCredentials = setup.get(CUSTOM_CREDENTIALS);
            if (customCredentials != null) {
                initialCredentials = customCredentials;
                //set userName and other data, from these credentials
                Object oldUsername = setup.put(USER, customCredentials.getUser());
                LOG.debug("node {} username {} / {} (customCredentials)", new Object[] { node, customCredentials.getUser(), oldUsername });
                if (truth(customCredentials.getPassword())) setup.put(PASSWORD, customCredentials.getPassword());
                if (truth(customCredentials.getPrivateKey())) setup.put(PRIVATE_KEY_DATA, customCredentials.getPrivateKey());
            }
            if (initialCredentials == null) {
                initialCredentials = extractVmCredentials(setup, node);
            }
            if (initialCredentials != null) {
                node = NodeMetadataBuilder.fromNodeMetadata(node).credentials(initialCredentials).build();
            } else {
                // only happens if something broke above...
                initialCredentials = LoginCredentials.fromCredentials(node.getCredentials());
            }
            // Wait for the VM to be reachable over SSH
            waitForReachable(computeService, node, initialCredentials, setup);
            
            String vmHostname = getPublicHostname(node, setup);
            sshMachineLocation = registerJcloudsSshMachineLocation(node, vmHostname, setup);
            
            // Apply same securityGroups rules to iptables, if iptables is running on the node
            String waitForSshable = setup.get(WAIT_FOR_SSHABLE);
            if (!(waitForSshable!=null && "false".equalsIgnoreCase(waitForSshable))) {
                if (setup.get(JcloudsLocationConfig.MAP_DEV_RANDOM_TO_DEV_URANDOM))
                   sshMachineLocation.execCommands("using urandom instead of random", 
                        Arrays.asList("sudo mv /dev/random /dev/random-real", "sudo ln -s /dev/urandom /dev/random"));
                
                if (setup.get(OPEN_IPTABLES)) {
                   List<String> iptablesRules = createIptablesRulesForNetworkInterface("eth0", (Iterable<Integer>) setup.get(INBOUND_PORTS));
                   sshMachineLocation.execCommands("Inserting iptables rules", iptablesRules);
                }

                if (setup.get(GENERATE_HOSTNAME)) {
                   sshMachineLocation.execCommands("Generate hostname " + node.getName(), 
                         Arrays.asList("sudo hostname " + node.getName(), 
                                       "sudo bash -c \"echo 127.0.0.1   `hostname` >> /etc/hosts\"")
                   );
               }
                String setupScript = setup.get(JcloudsLocationConfig.CUSTOM_MACHINE_SETUP_SCRIPT_URL);
                if(Strings.isNonBlank(setupScript)) {
                   String setupVarsString = setup.get(JcloudsLocationConfig.CUSTOM_MACHINE_SETUP_SCRIPT_VARS);
                   Map<String, String> substitutions = Splitter.on(",").withKeyValueSeparator(":").split(setupVarsString);
                   String script = TemplateProcessor.processTemplate(setupScript, substitutions);
                   sshMachineLocation.execCommands("Customizing node " + this, ImmutableList.of(script));
                }
                
            } else {
                // Otherwise would break CloudStack, where port-forwarding means that jclouds opinion 
                // of using port 22 is wrong.
            }
            
            // Apply any optional app-specific customization.
            for (JcloudsLocationCustomizer customizer : getCustomizers(setup)) {
                customizer.customize(computeService, sshMachineLocation);
            }
            
            return sshMachineLocation;
        } catch (Exception e) {
            if (e instanceof RunNodesException && ((RunNodesException)e).getNodeErrors().size() > 0) {
                node = Iterables.get(((RunNodesException)e).getNodeErrors().keySet(), 0);
            }
            boolean destroyNode = (node != null) && Boolean.TRUE.equals(setup.get(DESTROY_ON_FAILURE));
            
            LOG.error("Failed to start VM for {}{}: {}", 
                    new Object[] {setup.getDescription(), (destroyNode ? " (destroying "+node+")" : ""), e.getMessage()});
            LOG.debug(Throwables.getStackTraceAsString(e));
            
            if (destroyNode) {
                if (sshMachineLocation != null) {
                    releaseSafely(sshMachineLocation);
                } else {
                    releaseNodeSafely(node);
                }
            }
            
            throw Exceptions.propagate(e);
            
        } finally {
            //leave it open for reuse
//            computeService.getContext().close();
        }

    }

    private void mapSecurityGroupRuleToIpTables(ComputeService computeService, NodeMetadata node,
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
    
    // ------------- constructing the template, etc ------------------------
    
    private static interface CustomizeTemplateBuilder {
        void apply(TemplateBuilder tb, ConfigBag props, Object v);
    }
    
    private static interface CustomizeTemplateOptions {
        void apply(TemplateOptions tb, ConfigBag props, Object v);
    }
    
    /** properties which cause customization of the TemplateBuilder */
    public static final Map<ConfigKey<?>,CustomizeTemplateBuilder> SUPPORTED_TEMPLATE_BUILDER_PROPERTIES = ImmutableMap.<ConfigKey<?>,CustomizeTemplateBuilder>builder()
            .put(MIN_RAM, new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        tb.minRam(TypeCoercions.coerce(v, Integer.class));
                    }})
            .put(MIN_CORES, new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        tb.minCores(TypeCoercions.coerce(v, Double.class));
                    }})
            .put(HARDWARE_ID, new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        tb.hardwareId(((CharSequence)v).toString());
                    }})
            .put(IMAGE_ID, new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        tb.imageId(((CharSequence)v).toString());
                    }})
            .put(IMAGE_DESCRIPTION_REGEX, new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        tb.imageDescriptionMatches(((CharSequence)v).toString());
                    }})
            .put(IMAGE_NAME_REGEX, new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        tb.imageNameMatches(((CharSequence)v).toString());
                    }})
            .put(TEMPLATE_SPEC, new CustomizeTemplateBuilder() {
                public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        tb.from(TemplateBuilderSpec.parse(((CharSequence)v).toString()));
                    }})
            .put(DEFAULT_IMAGE_ID, new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        /* done in the code, but included here so that it is in the map */
                    }})
            .put(TEMPLATE_BUILDER, new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        /* done in the code, but included here so that it is in the map */
                    }})
            .build();
   
    /** properties which cause customization of the TemplateOptions */
    public static final Map<ConfigKey<?>,CustomizeTemplateOptions> SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES = ImmutableMap.<ConfigKey<?>,CustomizeTemplateOptions>builder()
            .put(SECURITY_GROUPS, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (t instanceof EC2TemplateOptions) {
                            String[] securityGroups = toStringArray(v);
                            ((EC2TemplateOptions)t).securityGroups(securityGroups);
                        } else if (t instanceof NovaTemplateOptions) {
                            String[] securityGroups = toStringArray(v);
                            ((NovaTemplateOptions)t).securityGroupNames(securityGroups);
                        } else {
                            LOG.info("ignoring securityGroups({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }
                    }})
            .put(USER_DATA_UUENCODED, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (t instanceof EC2TemplateOptions) {
                            byte[] bytes = toByteArray(v);
                            ((EC2TemplateOptions)t).userData(bytes);
                        } else {
                            LOG.info("ignoring userData({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }
                    }})
            .put(INBOUND_PORTS, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        int[] inboundPorts = toIntArray(v);
                        if (LOG.isDebugEnabled()) LOG.debug("opening inbound ports {} for {}", Arrays.toString(inboundPorts), t);
                        t.inboundPorts(inboundPorts);
                    }})
            .put(TAGS, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        List<String> tags = toListOfStrings(v);
                        if (LOG.isDebugEnabled()) LOG.debug("setting VM tags {} for {}", tags, t);
                        t.tags(tags);
                    }})
            .put(USER_METADATA, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        t.userMetadata(toMapStringString(v));
                    }})
            .put(EXTRA_PUBLIC_KEY_DATA_TO_AUTH, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        t.authorizePublicKey(((CharSequence)v).toString());
                    }})
            .put(RUN_AS_ROOT, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        t.runAsRoot((Boolean)v);
                    }})
            .put(LOGIN_USER, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        t.overrideLoginUser(((CharSequence)v).toString());
                    }})
            .put(LOGIN_USER_PASSWORD, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        t.overrideLoginPassword(((CharSequence)v).toString());
                    }})
            .put(LOGIN_USER_PRIVATE_KEY_FILE, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        String privateKeyFileName = ((CharSequence)v).toString();
                        String privateKey;
                        try {
                            privateKey = Files.toString(new File(ResourceUtils.tidyFilePath(privateKeyFileName)), Charsets.UTF_8);
                        } catch (IOException e) {
                            LOG.error(privateKeyFileName + "not found", e);
                            throw Exceptions.propagate(e);
                        }
                        t.overrideLoginPrivateKey(privateKey);
                    }})
            .put(LOGIN_USER_PRIVATE_KEY_DATA, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        t.overrideLoginPrivateKey(((CharSequence)v).toString());
                    }})                    
            .put(KEY_PAIR, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (t instanceof EC2TemplateOptions) {
                            ((EC2TemplateOptions)t).keyPair(((CharSequence)v).toString());
                        } else if (t instanceof NovaTemplateOptions) {
                            ((NovaTemplateOptions)t).keyPairName(((CharSequence)v).toString());
                        } else {
                            LOG.info("ignoring keyPair({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }
                    }})
             .put(AUTO_GENERATE_KEYPAIRS, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (t instanceof NovaTemplateOptions) {
                            ((NovaTemplateOptions)t).generateKeyPair((Boolean)v);
                        } else {
                            LOG.info("ignoring auto_generate_keypair({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }
                    }})
             .put(AUTO_CREATE_FLOATING_IPS, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (t instanceof NovaTemplateOptions) {
                            ((NovaTemplateOptions)t).autoAssignFloatingIp((Boolean)v);
                        } else {
                            LOG.info("ignoring auto_generate_floating_ips({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }
                    }}) 
              .put(OVERRIDE_RAM, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (t instanceof AbiquoTemplateOptions) {
                            ((AbiquoTemplateOptions)t).overrideRam((Integer)v);
                        } else {
                            LOG.info("ignoring override_ram({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }     
              }}) 
            .build();

    private static boolean listedAvailableTemplatesOnNoSuchTemplate = false;

    /** returns the jclouds Template which describes the image to be built */
    protected Template buildTemplate(ComputeService computeService, ConfigBag config) {
        TemplateBuilder templateBuilder = (TemplateBuilder) config.get(TEMPLATE_BUILDER);
        if (templateBuilder==null)
            templateBuilder = new PortableTemplateBuilder();
        else
            LOG.debug("jclouds using templateBuilder {} as base for provisioning in {} for {}", new Object[] {
                    templateBuilder, this, config.getDescription()});

        if (!Strings.isEmpty(config.get(CLOUD_REGION_ID))) {
            templateBuilder.locationId(config.get(CLOUD_REGION_ID));
        }
        
        // Apply the template builder and options properties
        for (Map.Entry<ConfigKey<?>, CustomizeTemplateBuilder> entry : SUPPORTED_TEMPLATE_BUILDER_PROPERTIES.entrySet()) {
            ConfigKey<?> name = entry.getKey();
            CustomizeTemplateBuilder code = entry.getValue();
            if (config.containsKey(name))
                code.apply(templateBuilder, config, config.get(name));
        }

        if (templateBuilder instanceof PortableTemplateBuilder) {
            ((PortableTemplateBuilder<?>)templateBuilder).attachComputeService(computeService);
            // do the default last, and only if nothing else specified (guaranteed to be a PTB if nothing else specified)
            if (truth(config.get(DEFAULT_IMAGE_ID))) {
                if (((PortableTemplateBuilder<?>)templateBuilder).isBlank()) {
                    templateBuilder.imageId(config.get(DEFAULT_IMAGE_ID).toString());
                }
            }
        }

        // Then apply any optional app-specific customization.
        for (JcloudsLocationCustomizer customizer : getCustomizers(config)) {
            customizer.customize(computeService, templateBuilder);
        }
        
        // Finally try to build the template
        Template template;
        try {
            template = templateBuilder.build();
            if (template==null) throw new NullPointerException("No template found (templateBuilder.build returned null)");
            LOG.debug(""+this+" got template "+template+" (image "+template.getImage()+")");
            if (template.getImage()==null) throw new NullPointerException("Template does not contain an image (templateBuilder.build returned invalid template)");
            if (isBadTemplate(template.getImage())) {
                // release candidates might break things :(   TODO get the list and score them
                if (templateBuilder instanceof PortableTemplateBuilder) {
                    if (((PortableTemplateBuilder<?>)templateBuilder).getOsFamily()==null) {
                        templateBuilder.osFamily(OsFamily.UBUNTU).osVersionMatches("11.04").os64Bit(true);
                        Template template2 = templateBuilder.build();
                        if (template2!=null) {
                            LOG.debug(""+this+" preferring template {} over {}", template2, template);
                            template = template2;
                        }
                    }
                }
            }
        } catch (AuthorizationException e) {
            LOG.warn("Error resolving template: not authorized (rethrowing: "+e+")");
            throw new IllegalStateException("Not authorized to access cloud "+this+" to resolve "+templateBuilder, e);
        } catch (Exception e) {
            try {
                synchronized (this) {
                    // delay subsequent log.warns (put in synch block) so the "Loading..." message is obvious
                    LOG.warn("Unable to match required VM template constraints "+templateBuilder+" when trying to provision VM in "+this+" (rethrowing): "+e);
                    if (!listedAvailableTemplatesOnNoSuchTemplate) {
                        listedAvailableTemplatesOnNoSuchTemplate = true;
                        logAvailableTemplates(config);
                    }
                }
            } catch (Exception e2) {
                LOG.warn("Error loading available images to report (following original error matching template which will be rethrown): "+e2, e2);
                throw new IllegalStateException("Unable to access cloud "+this+" to resolve "+templateBuilder, e);
            }
            throw new IllegalStateException("Unable to match required VM template constraints "+templateBuilder+" when trying to provision VM in "+this+". See list of images in log.", e);
        }
        TemplateOptions options = template.getOptions();
               
        for (Map.Entry<ConfigKey<?>, CustomizeTemplateOptions> entry : SUPPORTED_TEMPLATE_OPTIONS_PROPERTIES.entrySet()) {
            ConfigKey<?> key = entry.getKey();
            CustomizeTemplateOptions code = entry.getValue();
            if (config.containsKey(key))
                code.apply(options, config, config.get(key));
        }
        
        return template;
    }
    
    protected void logAvailableTemplates(ConfigBag config) {
        LOG.info("Loading available images at "+this+" for reference...");
        ConfigBag m1 = ConfigBag.newInstanceCopying(config);
        if (m1.containsKey(IMAGE_ID)) {
            // if caller specified an image ID, remove that, but don't apply default filters
            m1.remove(IMAGE_ID);
            // TODO use key
            m1.putStringKey("anyOwner", true);
        }
        ComputeService computeServiceLessRestrictive = JcloudsUtil.findComputeService(m1);
        Set<? extends Image> imgs = computeServiceLessRestrictive.listImages();
        LOG.info(""+imgs.size()+" available images at "+this);
        for (Image img: imgs) {
            LOG.info(" Image: "+img);
        }
        
        Set<? extends Hardware> profiles = computeServiceLessRestrictive.listHardwareProfiles();
        LOG.info(""+profiles.size()+" available profiles at "+this);
        for (Hardware profile: profiles) {
            LOG.info(" Profile: "+profile);
        }

        Set<? extends org.jclouds.domain.Location> assignableLocations = computeServiceLessRestrictive.listAssignableLocations();
        LOG.info(""+assignableLocations.size()+" available locations at "+this);
        for (org.jclouds.domain.Location assignableLocation: assignableLocations) {
            LOG.info(" Location: "+assignableLocation);
        }
    }
    
    // Setup the user
    protected LoginCredentials initUserTemplateOptions(Template template, ConfigBag config) {
        //NB: we ignore private key here because, by default we probably should not be installing it remotely;
        //also, it may not be valid for first login (it is created before login e.g. on amazon, so valid there;
        //but not elsewhere, e.g. on rackspace).
        
        LoginCredentials result = null;
        TemplateOptions options = template.getOptions();
        Image image = template.getImage();
        String user = getUser(config);
        String explicitLoginUser = config.get(LOGIN_USER);
        String loginUser = truth(explicitLoginUser) ? explicitLoginUser : (image.getDefaultCredentials() != null) ? image.getDefaultCredentials().identity : null;
        Boolean dontCreateUser = config.get(DONT_CREATE_USER);
        String publicKeyData = LocationConfigUtils.getPublicKeyData(config);
        String privateKeyData = LocationConfigUtils.getPrivateKeyData(config);
        String explicitPassword = config.get(PASSWORD);
        String password = truth(explicitPassword) ? explicitPassword : Identifiers.makeRandomId(12);
        List<Statement> statements = Lists.newArrayList();
        
        if (!truth(user) || user.equals(loginUser) || truth(dontCreateUser)) {
            if (truth(dontCreateUser) && truth(user) && !user.equals(loginUser)) {
                // TODO For dontCreateUser, we only want to treat it special if user was explicitly supplied
                // (rather than it just being the default config key value). If user was explicit, then should
                // set the password + authorize the key for that user. Presumably the caller knows that this
                // user pre-exists on the given VM image.
                LOG.info("Not creating user {}, and not setting its password or authorizing keys (temporarily using loginUser {})", user, loginUser);
            }
            
            // For subsequent ssh'ing, we'll be using the loginUser
            if (!truth(user)) {
                config.put(USER, loginUser);
            }
            
            // Using loginUser; setup the publicKey/password so can login as expected
            if (password != null) {
                statements.add(new ReplaceShadowPasswordEntry(Sha512Crypt.function(), loginUser, password));
                result = LoginCredentials.builder().user(loginUser).password(password).build();
            }
            if (publicKeyData!=null) {
                template.getOptions().authorizePublicKey(publicKeyData);
                if (privateKeyData != null) {
                    result = LoginCredentials.builder().user(loginUser).privateKey(privateKeyData).build();
                }
            }

        } else if (truth(dontCreateUser)) {
            // Expect user to already exist; setup the publicKey/password so can login as expected
            if (password != null) {
                statements.add(new ReplaceShadowPasswordEntry(Sha512Crypt.function(), user, password));
                result = LoginCredentials.builder().user(user).password(password).build();
            }
            if (publicKeyData!=null) {
                template.getOptions().authorizePublicKey(publicKeyData);
                if (privateKeyData != null) {
                    result = LoginCredentials.builder().user(loginUser).privateKey(privateKeyData).build();
                }
            }

            // For subsequent ssh'ing, we'll be using the loginUser
            if (!truth(user)) {
                config.put(USER, loginUser);
            }
            
        } else if (user.equals(ROOT_USERNAME)) {
            // Authorizes the public-key and sets password for the root user, so can login as expected
            if (password != null) {
                statements.add(new ReplaceShadowPasswordEntry(Sha512Crypt.function(), ROOT_USERNAME, password));
                result = LoginCredentials.builder().user(user).password(password).build();
            }
            if (publicKeyData!=null) {
                statements.add(new AuthorizeRSAPublicKeys("~"+ROOT_USERNAME+"/.ssh", ImmutableList.of(publicKeyData)));
                result = LoginCredentials.builder().user(user).privateKey(privateKeyData).build();
            }
            
        } else {
            // Create the user
            // By default we now give these users sudo privileges.
            // If you want something else, that can be specified manually, 
            // e.g. using jclouds UserAdd.Builder, with RunScriptOnNode, or template.options.runScript(xxx).
            // (if that is a common use case, we could expose a property here)
            // note AdminAccess requires _all_ fields set, due to http://code.google.com/p/jclouds/issues/detail?id=1095
            AdminAccess.Builder adminBuilder = AdminAccess.builder()
                    .adminUsername(user)
                    .adminPassword(password)
                    .grantSudoToAdminUser(true)
                    .resetLoginPassword(true)
                    .loginPassword(password);
            
            if (publicKeyData!=null) {
                adminBuilder.authorizeAdminPublicKey(true).adminPublicKey(publicKeyData);
            } else {
                adminBuilder.authorizeAdminPublicKey(false).adminPublicKey("ignored");
            }
            
            // TODO Brittle code! This only works with adminPrivateKey set to non-null; 
            // otherwise, in AdminAccess.build, if adminUsername != null && adminPassword != null 
            // then authorizeAdminPublicKey is reset to null!
            adminBuilder.installAdminPrivateKey(false).adminPrivateKey("ignore");
            
            if (truth(explicitPassword)) {
                adminBuilder.lockSsh(false);
            } else if (publicKeyData != null) {
                adminBuilder.lockSsh(true);
            } else {
                // no keys or passwords supplied; using only defaults!
                adminBuilder.lockSsh(false);
            }
            
            options.runScript(adminBuilder.build());
            
            if (privateKeyData != null) {
                // assume have uploaded corresponding .pub file
                result = LoginCredentials.builder().user(user).privateKey(privateKeyData).build();
            } else {
                result = LoginCredentials.builder().user(user).password(password).build();
            }
        }
        
        if (statements.size() > 0) {
            options.runScript(new StatementList(statements));
        }
        
        return result;
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

    // ----------------- rebinding to existing machine ------------------------

    public JcloudsSshMachineLocation rebindMachine(NodeMetadata metadata) throws NoMachinesAvailableException {
        return rebindMachine(MutableMap.of(), metadata);
    }
    public JcloudsSshMachineLocation rebindMachine(Map flags, NodeMetadata metadata) throws NoMachinesAvailableException {
        ConfigBag setup = ConfigBag.newInstanceExtending(getConfigBag(), flags);
        if (!setup.containsKey("id")) setup.putStringKey("id", metadata.getId());
        setHostnameUpdatingCredentials(setup, metadata);
        return rebindMachine(setup);
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
    public JcloudsSshMachineLocation rebindMachine(ConfigBag setup) throws NoMachinesAvailableException {
        try {
            if (setup.getDescription()==null) setCreationString(setup);
            
            String id = (String) checkNotNull(setup.getStringKey("id"), "id");
            String hostname = (String) setup.getStringKey("hostname");
            String user = checkNotNull(getUser(setup), "user");
            
            LOG.info("Rebinding to VM {} ({}@{}), in jclouds location for provider {}", 
                    new Object[] {id, user, (hostname != null ? hostname : "<unspecified>"), getProvider()});
            
            // can we allow re-use ?  previously didn't
            ComputeService computeService = JcloudsUtil.findComputeService(setup, false);
            NodeMetadata node = computeService.getNodeMetadata(id);
            if (node == null) {
                throw new IllegalArgumentException("Node not found with id "+id);
            }
    
            String pkd = LocationConfigUtils.getPrivateKeyData(setup);
            if (truth(pkd)) {
                LoginCredentials expectedCredentials = LoginCredentials.fromCredentials(new Credentials(user, pkd));
                //override credentials
                node = NodeMetadataBuilder.fromNodeMetadata(node).credentials(expectedCredentials).build();
            }
            // TODO confirm we can SSH ?

            if (hostname == null) {
                hostname = getPublicHostname(node, setup);
            }

            return registerJcloudsSshMachineLocation(node, hostname, setup);
            
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }
    public JcloudsSshMachineLocation rebindMachine(Map flags) throws NoMachinesAvailableException {
        return rebindMachine(new ConfigBag().putAll(flags));
    }

    // -------------- create the SshMachineLocation instance, and connect to it etc ------------------------
    
    protected JcloudsSshMachineLocation registerJcloudsSshMachineLocation(NodeMetadata node, String vmHostname, ConfigBag setup) throws IOException {
        JcloudsSshMachineLocation machine = createJcloudsSshMachineLocation(node, vmHostname, setup);
        machine.setParent(this);
        vmInstanceIds.put(machine, node.getId());
        return machine;
    }

    protected JcloudsSshMachineLocation createJcloudsSshMachineLocation(NodeMetadata node, String vmHostname, ConfigBag setup) throws IOException {
        Map<?,?> sshConfig = extractSshConfig(setup, node);
        if (LOG.isDebugEnabled())
            LOG.debug("creating JcloudsSshMachineLocation representation for {}@{} for {} with {}", 
                    new Object[] {
                            getUser(setup), 
                            vmHostname, 
                            setup.getDescription(), 
                            Entities.sanitize(sshConfig)
                    });
        
        if (isManaged()) {
            return getManagementContext().getLocationManager().createLocation(LocationSpec.create(JcloudsSshMachineLocation.class)
                            .configure("address", vmHostname) 
                            .configure("displayName", vmHostname)
                            .configure("user", getUser(setup))
                            // don't think "config" does anything
                            .configure(sshConfig)
                            // FIXME remove "config" -- inserted directly, above
                            .configure("config", sshConfig)
                            .configure("jcloudsParent", this)
                            .configure("node", node));
        } else {
            LOG.warn("Using deprecated JcloudsSshMachineLocation constructor because "+this+" is not managed");
            return new JcloudsSshMachineLocation(MutableMap.builder()
                    .put("address", vmHostname) 
                    .put("displayName", vmHostname)
                    .put("user", getUser(setup))
                    // don't think "config" does anything
                    .putAll(sshConfig)
                    // FIXME remove "config" -- inserted directly, above
                    .put("config", sshConfig)
                    .build(),
                    this,
                    node);
        }
    }

    // -------------- give back the machines------------------
    
    protected Map<String,Object> extractSshConfig(ConfigBag setup, NodeMetadata node) {
        ConfigBag nodeConfig = new ConfigBag();
        if (node!=null) {
            nodeConfig.putIfNotNull(PASSWORD, node.getCredentials().getPassword());
            nodeConfig.putIfNotNull(PRIVATE_KEY_DATA, node.getCredentials().getPrivateKey());
        }
        return extractSshConfig(setup, nodeConfig).getAllConfigRaw();
    }

    @Override
    public void release(SshMachineLocation machine) {
        String instanceId = vmInstanceIds.remove(machine);
        if (!truth(instanceId)) {
            throw new IllegalArgumentException("Unknown machine "+machine);
        }
        
        LOG.info("Releasing machine {} in {}, instance id {}", new Object[] {machine, this, instanceId});
        
        removeChild(machine);
        try {
            releaseNode(instanceId);
        } catch (Exception e) {
            LOG.error("Problem releasing machine "+machine+" in "+this+", instance id "+instanceId+
                    "; discarding instance and continuing...", e);
            Exceptions.propagate(e);
        }
    }

    protected void releaseSafely(SshMachineLocation machine) {
        try {
            release(machine);
        } catch (Exception e) {
            // rely on exception having been logged by #release(SshMachineLocation), so no-op
        }
    }

    protected void releaseNodeSafely(NodeMetadata node) {
        String instanceId = node.getId();
        LOG.info("Releasing node {} in {}, instance id {}", new Object[] {node, this, instanceId});
        
        ComputeService computeService = null;
        try {
            releaseNode(instanceId);
        } catch (Exception e) {
            LOG.warn("Problem releasing node "+node+" in "+this+", instance id "+instanceId+
                    "; discarding instance and continuing...", e);
        }
    }

    protected void releaseNode(String instanceId) {
        ComputeService computeService = null;
        try {
            computeService = JcloudsUtil.findComputeService(getConfigBag());
            computeService.destroyNode(instanceId);
        } finally {
        /*
         //don't close, so can re-use...
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

    // ------------ support methods --------------------

    /**
     * Extracts the user that jclouds tells us about (i.e. from the jclouds node).
     */
    protected LoginCredentials extractVmCredentials(ConfigBag setup, NodeMetadata node) {
        String user = getUser(setup);
        String localPrivateKeyData = LocationConfigUtils.getPrivateKeyData(setup);
        String localPassword = setup.get(PASSWORD);
        LoginCredentials nodeCredentials = LoginCredentials.fromCredentials(node.getCredentials());

        LOG.debug("node {} username {} / {} (jclouds)", new Object[] { node, user, nodeCredentials.getUser() });
        
        if (truth(nodeCredentials.getUser())) {
            if (user==null) {
                setup.put(USER, user = nodeCredentials.getUser());
            } else if ("root".equals(user) && ROOT_ALIASES.contains(nodeCredentials.getUser())) {
                // deprecated, we used to default username to 'root'; now we leave null, then use autodetected credentials if no user specified
                // 
                LOG.warn("overriding username 'root' in favour of '"+nodeCredentials.getUser()+"' at {}; this behaviour may be removed in future", node);
                setup.put(USER, user = nodeCredentials.getUser());
            }
            
            String pkd = elvis(localPrivateKeyData, nodeCredentials.getPrivateKey());
            String pwd = elvis(localPassword, nodeCredentials.getPassword());
            if (user==null || (pkd==null && pwd==null)) {
                String missing = (user==null ? "user" : "credential");
                LOG.warn("Not able to determine "+missing+" for "+this+" at "+node+"; will likely fail subsequently");
                return null;
            } else {
                LoginCredentials.Builder resultBuilder = LoginCredentials.builder()
                        .user(user);
                if (pkd!=null) resultBuilder.privateKey(pkd);
                if (pwd!=null && pkd==null) resultBuilder.password(pwd);
                return resultBuilder.build();        
            }
        }
        
        LOG.warn("No node-credentials or admin-access available for node "+node+" in "+this+"; will likely fail subsequently");
        return null;
    }

    protected void waitForReachable(final ComputeService computeService, NodeMetadata node, LoginCredentials expectedCredentials, ConfigBag setup) {
        String waitForSshable = setup.get(WAIT_FOR_SSHABLE);
        if (waitForSshable!=null && "false".equalsIgnoreCase(waitForSshable)) {
            LOG.debug("Skipping ssh check for {} ({}) due to config waitForSshable=false", node, setup.getDescription());
            return;
        }
        
        String vmIp = JcloudsUtil.getFirstReachableAddress(this.getComputeService().getContext(), node);
        if (vmIp==null) LOG.warn("Unable to extract IP for "+node+" ("+setup.getDescription()+"): subsequent connection attempt will likely fail");
        
        final NodeMetadata nodeRef = node;
        final LoginCredentials expectedCredentialsRef = expectedCredentials;
        long delayMs = -1;
        try {
            delayMs = Time.parseTimeString(""+waitForSshable);
        } catch (Exception e) { /* normal if 'true'; just fall back to default */ }
        if (delayMs<0) 
            delayMs = Time.parseTimeString(WAIT_FOR_SSHABLE.getDefaultValue());
        
        String user = expectedCredentialsRef.getUser();
        LOG.info("Started VM in {}; waiting {} for it to be sshable on {}@{}{}",
                new Object[] {
                        setup.getDescription(),Time.makeTimeString(delayMs),
                        user, vmIp, Objects.equal(user, getUser(setup)) ? "" : " (setup user is different: "+getUser(setup)+")"
                });
        
        boolean reachable = new Repeater()
            .repeat()
            .every(1,SECONDS)
            .until(new Callable<Boolean>() {
                public Boolean call() {
                    Statement statement = Statements.newStatementList(exec("hostname"));
                    // NB this assumes passwordless sudo !
                    ExecResponse response = computeService.runScriptOnNode(nodeRef.getId(), statement, 
                            overrideLoginCredentials(expectedCredentialsRef).runAsRoot(false));
                    return response.getExitStatus() == 0;
                }})
            .limitTimeTo(delayMs, MILLISECONDS)
            .run();

        if (!reachable) {
            throw new IllegalStateException("SSH failed for "+
                    user+"@"+vmIp+" ("+setup.getDescription()+") after waiting "+
                    Time.makeTimeString(delayMs));
        }
    }
    
    // -------------------- hostnames ------------------------
    // hostnames are complicated, but irregardless, this code could be cleaned up!

    protected void setHostnameUpdatingCredentials(ConfigBag setup, NodeMetadata metadata) {
        List<String> usersTried = new ArrayList<String>();
        
        String originalUser = getUser(setup);
        if (truth(originalUser)) {
            if (setHostname(setup, metadata, false)) return;
            usersTried.add(originalUser);
        }
        
        LoginCredentials credentials = metadata.getCredentials();
        if (truth(credentials)) {
            if (truth(credentials.getUser())) setup.put(USER, credentials.getUser());
            if (truth(credentials.getPrivateKey())) setup.put(PRIVATE_KEY_DATA, credentials.getPrivateKey());
            if (setHostname(setup, metadata, false)) {
                if (originalUser!=null && !originalUser.equals(getUser(setup))) {
                    LOG.warn("Switching to cloud-specified user at "+metadata+" as "+getUser(setup)+" (failed to connect using: "+usersTried+")");
                }
                return;
            }
            usersTried.add(getUser(setup));
        }
        
        for (String u: COMMON_USER_NAMES_TO_TRY) {
            setup.put(USER, u);
            if (setHostname(setup, metadata, false)) {
                LOG.warn("Auto-detected user at "+metadata+" as "+getUser(setup)+" (failed to connect using: "+usersTried+")");
                return;
            }
            usersTried.add(getUser(setup));
        }
        // just repeat, so we throw exception
        LOG.warn("Failed to log in to "+metadata+", tried as users "+usersTried+" (throwing original exception)");
        setup.put(USER, originalUser);
        setHostname(setup, metadata, true);
    }
    
    protected boolean setHostname(ConfigBag setup, NodeMetadata metadata, boolean rethrow) {
        try {
            setup.put(SshTool.PROP_HOST, getPublicHostname(metadata, setup));
            return true;
        } catch (Exception e) {
            if (rethrow) {
                LOG.warn("couldn't connect to "+metadata+" when trying to discover hostname (rethrowing): "+e);
                throw Exceptions.propagate(e);                
            }
            return false;
        }
    }

    String getPublicHostname(NodeMetadata node, ConfigBag setup) {
        if ("aws-ec2".equals(setup != null ? setup.get(CLOUD_PROVIDER) : null)) {
            String vmIp = null;
            try {
                vmIp = JcloudsUtil.getFirstReachableAddress(this.getComputeService().getContext(), node);
            } catch (Exception e) {
                LOG.warn("Error reaching aws-ec2 instance "+node.getId()+"@"+node.getLocation()+" on port "+node.getLoginPort()+"; falling back to jclouds metadata for address", e);
            }
            if (vmIp != null) {
                try {
                    return getPublicHostnameAws(vmIp, setup);
                } catch (Exception e) {
                    LOG.warn("Error querying aws-ec2 instance instance "+node.getId()+"@"+node.getLocation()+" over ssh for its hostname; falling back to first reachable IP", e);
                    return vmIp;
                }
            }
        }
        
        return getPublicHostnameGeneric(node, setup);
    }
    
    private String getPublicHostnameGeneric(NodeMetadata node, @Nullable ConfigBag setup) {
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
    
    private String getPublicHostnameAws(String ip, ConfigBag setup) {
        SshMachineLocation sshLocByIp = null;
        try {
            ConfigBag sshConfig = extractSshConfig(setup, new ConfigBag());
            
            // TODO messy way to get an SSH session 
            MutableMap<Object, Object> locationProps = MutableMap.builder()
                    .put("address", ip) 
                    .put("user", getUser(setup))
                    .putAll(sshConfig.getAllConfig())
                    .build();
            if (isManaged()) {
                sshLocByIp = getManagementContext().getLocationManager().createLocation(locationProps, SshMachineLocation.class);
            } else {
                sshLocByIp = new SshMachineLocation(locationProps);
            }
            
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
        } finally {
            Closeables.closeQuietly(sshLocByIp);
        }
    }
    
    // ------------ static converters (could go to a new file) ------------------
    
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


    protected static double toDouble(Object v) {
        if (v instanceof Number) {
            return ((Number)v).doubleValue();
        } else {
            throw new IllegalArgumentException("Invalid type for double: "+v+" of type "+v.getClass());
        }
    }

    @VisibleForTesting
    static int[] toIntArray(Object v) {
        int[] result;
        if (v instanceof Iterable) {
            result = new int[Iterables.size((Iterable<?>)v)];
            int i = 0;
            for (Object o : (Iterable<?>)v) {
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
        } else if (v instanceof String) {
            Matcher listMatcher = LIST_PATTERN.matcher(v.toString());
            boolean intList = true;
            if (listMatcher.matches()) {
                List<String> strings = KeyValueParser.parseList(listMatcher.group(1));
                List<Integer> integers = new ArrayList<Integer>();
                for (String string : strings) {
                    if (INTEGER_PATTERN.matcher(string).matches()) {
                        integers.add(Integer.parseInt(string));
                    } else {
                        intList = false;
                        break;
                    }
                }
                result = Ints.toArray(integers);
            } else {
                intList = false;
                result = null;
            }
            if (!intList) {
                throw new IllegalArgumentException("Invalid type for int[]: "+v+" of type "+v.getClass());
            }
        } else {
            throw new IllegalArgumentException("Invalid type for int[]: "+v+" of type "+v.getClass());
        }
        return result;
    }

    protected static String[] toStringArray(Object v) {
        return toListOfStrings(v).toArray(new String[0]);
    }
    
    protected static List<String> toListOfStrings(Object v) {
        List<String> result = Lists.newArrayList();
        if (v instanceof Iterable) {
            for (Object o : (Iterable<?>)v) {
                result.add(o.toString());
            }
        } else if (v instanceof Object[]) {
            for (int i = 0; i < ((Object[])v).length; i++) {
                result.add(((Object[])v)[i].toString());
            }
        } else if (v instanceof String) {
            result.add((String) v);
        } else {
            throw new IllegalArgumentException("Invalid type for List<String>: "+v+" of type "+v.getClass());
        }
        return result;
    }
    
    protected static byte[] toByteArray(Object v) {
        if (v instanceof byte[]) {
            return (byte[]) v;
        } else if (v instanceof CharSequence) {
            return v.toString().getBytes();
        } else {
            throw new IllegalArgumentException("Invalid type for byte[]: "+v+" of type "+v.getClass());
        }
    }
    
    // Handles GString
    protected static Map<String,String> toMapStringString(Object v) {
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
    
    private List<String> createIptablesRulesForNetworkInterface(String networkInterface, Iterable<Integer> ports) {
       List<String> iptablesRules = Lists.newArrayList();
       for (Integer port : ports) {
          iptablesRules.add(IptablesCommands.insertIptablesRule(Chain.INPUT, networkInterface, Protocol.TCP, port, Policy.ACCEPT));
       }
       return iptablesRules;
    }
}
