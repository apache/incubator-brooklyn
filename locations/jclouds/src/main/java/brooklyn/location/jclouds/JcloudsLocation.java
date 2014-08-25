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
package brooklyn.location.jclouds;

import static brooklyn.util.JavaGroovyEquivalents.elvis;
import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;
import static com.google.common.base.Preconditions.checkArgument;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.jclouds.abiquo.compute.options.AbiquoTemplateOptions;
import org.jclouds.cloudstack.compute.options.CloudStackTemplateOptions;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.config.AdminAccessConfiguration;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.ExecResponse;
import org.jclouds.compute.domain.Hardware;
import org.jclouds.compute.domain.Image;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadata.Status;
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.compute.domain.OsFamily;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.domain.TemplateBuilderSpec;
import org.jclouds.compute.functions.Sha512Crypt;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.domain.Credentials;
import org.jclouds.domain.LocationScope;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.ec2.compute.options.EC2TemplateOptions;
import org.jclouds.googlecomputeengine.compute.options.GoogleComputeEngineTemplateOptions;
import org.jclouds.openstack.nova.v2_0.compute.options.NovaTemplateOptions;
import org.jclouds.rest.AuthorizationException;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.StatementList;
import org.jclouds.scriptbuilder.domain.Statements;
import org.jclouds.scriptbuilder.functions.InitAdminAccess;
import org.jclouds.scriptbuilder.statements.login.AdminAccess;
import org.jclouds.scriptbuilder.statements.login.ReplaceShadowPasswordEntry;
import org.jclouds.scriptbuilder.statements.ssh.AuthorizeRSAPublicKeys;
import org.jclouds.softlayer.compute.options.SoftLayerTemplateOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.config.ConfigUtils;
import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationSpec;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineManagementMixins.MachineMetadata;
import brooklyn.location.MachineManagementMixins.RichMachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.access.PortForwardManager;
import brooklyn.location.basic.BasicMachineMetadata;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.basic.LocationConfigUtils;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.cloud.AbstractCloudMachineProvisioningLocation;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.location.cloud.CloudMachineNamer;
import brooklyn.location.jclouds.JcloudsPredicates.NodeInLocation;
import brooklyn.location.jclouds.networking.JcloudsPortForwarderExtension;
import brooklyn.location.jclouds.templates.PortableTemplateBuilder;
import brooklyn.location.jclouds.zone.AwsAvailabilityZoneExtension;
import brooklyn.management.AccessController;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.CompoundRuntimeException;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.ReferenceWithError;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.internal.ssh.ShellTool;
import brooklyn.util.internal.ssh.SshTool;
import brooklyn.util.javalang.Enums;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.net.Cidr;
import brooklyn.util.net.Networking;
import brooklyn.util.net.Protocol;
import brooklyn.util.os.Os;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.ssh.IptablesCommands;
import brooklyn.util.ssh.IptablesCommands.Chain;
import brooklyn.util.ssh.IptablesCommands.Policy;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.KeyValueParser;
import brooklyn.util.text.Strings;
import brooklyn.util.text.TemplateProcessor;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Splitter;
import com.google.common.base.Stopwatch;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import com.google.common.io.Files;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Ints;

/**
 * For provisioning and managing VMs in a particular provider/region, using jclouds.
 * Configuration flags are defined in {@link JcloudsLocationConfig}.
 */
@SuppressWarnings("serial")
public class JcloudsLocation extends AbstractCloudMachineProvisioningLocation implements JcloudsLocationConfig, RichMachineProvisioningLocation<SshMachineLocation> {

    // TODO After converting from Groovy to Java, this is now very bad code! It relies entirely on putting 
    // things into and taking them out of maps; it's not type-safe, and it's thus very error-prone.
    // In Groovy, that's considered ok but not in Java. 

    // TODO test (and fix) ability to set config keys from flags

    // TODO need a way to define imageId (and others?) with a specific location

    // TODO we say config is inherited, but it isn't the case for many "deep" / jclouds properties
    // e.g. when we pass getRawLocalConfigBag() in and decorate it with additional flags
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

    @SetFromFlag // so it's persisted
    private final Map<JcloudsSshMachineLocation,String> vmInstanceIds = Maps.newLinkedHashMap();
    
    static { Networking.init(); }
    
    public JcloudsLocation() {
        super();
    }
    
    /** typically wants at least ACCESS_IDENTITY and ACCESS_CREDENTIAL */
    public JcloudsLocation(Map<?,?> conf) {
       super(conf);
    }

    @Override
    public JcloudsLocation configure(Map properties) {
        super.configure(properties);
        
        if (getLocalConfigBag().containsKey("providerLocationId")) {
            LOG.warn("Using deprecated 'providerLocationId' key in "+this);
            if (!getLocalConfigBag().containsKey(CLOUD_REGION_ID))
                // FIXME modifies getLocalConfigBag result, expecting that to set it on actual location
                getLocalConfigBag().put(CLOUD_REGION_ID, (String)getLocalConfigBag().getStringKey("providerLocationId"));
        }
        
        if (isDisplayNameAutoGenerated() || !groovyTruth(getDisplayName())) {
            setDisplayName(elvis(getProvider(), "unknown") +
                   (groovyTruth(getRegion()) ? ":"+getRegion() : "") +
                   (groovyTruth(getEndpoint()) ? ":"+getEndpoint() : ""));
        }
        
        setCreationString(getLocalConfigBag());
        
        if (getConfig(MACHINE_CREATION_SEMAPHORE) == null) {
            Integer maxConcurrent = getConfig(MAX_CONCURRENT_MACHINE_CREATIONS);
            if (maxConcurrent == null || maxConcurrent < 1) {
                throw new IllegalStateException(MAX_CONCURRENT_MACHINE_CREATIONS.getName() + " must be >= 1, but was "+maxConcurrent);
            }
            setConfig(MACHINE_CREATION_SEMAPHORE, new Semaphore(maxConcurrent, true));
        }
        return this;
    }
    
    @Override
    public void init() {
        super.init();
        if ("aws-ec2".equals(getProvider())) {
            addExtension(AvailabilityZoneExtension.class, new AwsAvailabilityZoneExtension(getManagementContext(), this));
        }
    }
    
    @Override
    public JcloudsLocation newSubLocation(Map<?,?> newFlags) {
        return newSubLocation(getClass(), newFlags);
    }

    @Override
    public JcloudsLocation newSubLocation(Class<? extends AbstractCloudMachineProvisioningLocation> type, Map<?,?> newFlags) {
        // TODO should be able to use ConfigBag.newInstanceExtending; would require moving stuff around to api etc
        return (JcloudsLocation) getManagementContext().getLocationManager().createLocation(LocationSpec.create(type)
                .parent(this)
                .configure(getLocalConfigBag().getAllConfig())  // FIXME Should this just be inherited?
                .configure(MACHINE_CREATION_SEMAPHORE, getMachineCreationSemaphore())
                .configure(newFlags));
    }

    @Override
    public String toString() {
        Object identity = getIdentity();
        String configDescription = getLocalConfigBag().getDescription();
        if (configDescription!=null && configDescription.startsWith(getClass().getSimpleName()))
            return configDescription;
        return getClass().getSimpleName()+"["+getDisplayName()+":"+(identity != null ? identity : null)+
                (configDescription!=null ? "/"+configDescription : "") + "]";
    }

    @Override
    public String toVerboseString() {
        return Objects.toStringHelper(this).omitNullValues()
                .add("id", getId()).add("name", getDisplayName()).add("identity", getIdentity())
                .add("description", getLocalConfigBag().getDescription()).add("provider", getProvider())
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

    public String getEndpoint() {
        return LocationConfigUtils.getConfigCheckingDeprecatedAlternatives(getAllConfigBag(), 
                CLOUD_ENDPOINT, JCLOUDS_KEY_ENDPOINT);
    }

    public String getUser(ConfigBag config) {
        return LocationConfigUtils.getConfigCheckingDeprecatedAlternatives(config, 
                USER, JCLOUDS_KEY_USERNAME);
    }
    
    protected Semaphore getMachineCreationSemaphore() {
        return checkNotNull(getConfig(MACHINE_CREATION_SEMAPHORE), MACHINE_CREATION_SEMAPHORE.getName());
    }

    protected CloudMachineNamer getCloudMachineNamer(ConfigBag config) {
        String namerClass = config.get(LocationConfigKeys.CLOUD_MACHINE_NAMER_CLASS);
        if (Strings.isNonBlank(namerClass)) {
            Optional<CloudMachineNamer> cloudNamer = Reflections.invokeConstructorWithArgs(getManagementContext().getCatalog().getRootClassLoader(), namerClass, config);
            if (cloudNamer.isPresent()) {
                return cloudNamer.get();
            } else {
                throw new IllegalStateException("Failed to create CloudMachineNamer "+namerClass+" for location "+this);
            }
        } else {
            return new JcloudsMachineNamer(config);
        }
    }
    
    protected Collection<JcloudsLocationCustomizer> getCustomizers(ConfigBag setup) {
        JcloudsLocationCustomizer customizer = setup.get(JCLOUDS_LOCATION_CUSTOMIZER);
        Collection<JcloudsLocationCustomizer> customizers = setup.get(JCLOUDS_LOCATION_CUSTOMIZERS);
        String customizerType = setup.get(JCLOUDS_LOCATION_CUSTOMIZER_TYPE);
        String customizersSupplierType = setup.get(JCLOUDS_LOCATION_CUSTOMIZERS_SUPPLIER_TYPE);

        ClassLoader catalogClassLoader = getManagementContext().getCatalog().getRootClassLoader();
        List<JcloudsLocationCustomizer> result = new ArrayList<JcloudsLocationCustomizer>();
        if (customizer != null) result.add(customizer);
        if (customizers != null) result.addAll(customizers);
        if (Strings.isNonBlank(customizerType)) {
            Optional<JcloudsLocationCustomizer> customizerByType = Reflections.invokeConstructorWithArgs(catalogClassLoader, customizerType, setup);
            if (customizerByType.isPresent()) {
                result.add(customizerByType.get());
            } else {
                customizerByType = Reflections.invokeConstructorWithArgs(catalogClassLoader, customizerType);
                if (customizerByType.isPresent()) {
                    result.add(customizerByType.get());
                } else {
                    throw new IllegalStateException("Failed to create JcloudsLocationCustomizer "+customizersSupplierType+" for location "+this);
                }
            }
        }
        if (Strings.isNonBlank(customizersSupplierType)) {
            Optional<Supplier<Collection<JcloudsLocationCustomizer>>> supplier = Reflections.invokeConstructorWithArgs(catalogClassLoader, customizersSupplierType, setup);
            if (supplier.isPresent()) {
                result.addAll(supplier.get().get());
            } else {
                supplier = Reflections.invokeConstructorWithArgs(catalogClassLoader, customizersSupplierType);
                if (supplier.isPresent()) {
                    result.addAll(supplier.get().get());
                } else {
                    throw new IllegalStateException("Failed to create JcloudsLocationCustomizer supplier "+customizersSupplierType+" for location "+this);
                }
            }
        }
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
            if (groovyTruth(tagMapping.get(it)) && !groovyTruth(result)) {
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
        return JcloudsUtil.findComputeService((flags==null || flags.isEmpty()) ? getAllConfigBag() :
            ConfigBag.newInstanceExtending(getAllConfigBag(), flags));
    }
    
    /** @deprecated since 0.7.0 use {@link #listMachines()} */ @Deprecated
    public Set<? extends ComputeMetadata> listNodes() {
        return listNodes(MutableMap.of());
    }
    /** @deprecated since 0.7.0 use {@link #listMachines()}.
     * (no support for custom compute service flags; if that is needed, we'll have to introduce a new method,
     * but it seems there are no usages) */ @Deprecated
    public Set<? extends ComputeMetadata> listNodes(Map<?,?> flags) {
        return getComputeService(flags).listNodes();
    }
    
    @Override
    public Map<String, MachineMetadata> listMachines() {
        Set<? extends ComputeMetadata> nodes = 
            getRegion()!=null ? getComputeService().listNodesDetailsMatching(new NodeInLocation(getRegion(), true))
                : getComputeService().listNodes();
        Map<String,MachineMetadata> result = new LinkedHashMap<String, MachineMetadata>();
        
        for (ComputeMetadata node: nodes)
            result.put(node.getId(), getMachineMetadata(node));
        
        return result;
    }

    protected MachineMetadata getMachineMetadata(ComputeMetadata node) {
        if (node==null)
            return null;
        return new BasicMachineMetadata(node.getId(), node.getName(), 
            ((node instanceof NodeMetadata) ? Iterators.tryFind( ((NodeMetadata)node).getPublicAddresses().iterator(), Predicates.alwaysTrue() ).orNull() : null),
            ((node instanceof NodeMetadata) ? ((NodeMetadata)node).getStatus()==Status.RUNNING : null),
            node);
    }
    
    public MachineMetadata getMachineMetadata(MachineLocation l) {
        if (l instanceof JcloudsSshMachineLocation) {
            return getMachineMetadata( ((JcloudsSshMachineLocation)l).node );
        }
        return null;
    }
    
    @Override
    public void killMachine(String cloudServiceId) {
        getComputeService().destroyNode(cloudServiceId);
    }
    
    @Override
    public void killMachine(MachineLocation l) {
        MachineMetadata m = getMachineMetadata(l);
        if (m==null) throw new NoSuchElementException("Machine "+l+" is not known at "+this);
        killMachine(m.getId());
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
        ConfigBag setup = ConfigBag.newInstanceExtending(getAllConfigBag(), flags);
        Integer attempts = setup.get(MACHINE_CREATE_ATTEMPTS);
        List<Exception> exceptions = Lists.newArrayList();
        if (attempts == null || attempts < 1) attempts = 1;
        for (int i = 1; i <= attempts; i++) {
            try {
                return obtainOnce(setup);
            } catch (RuntimeException e) {
                LOG.warn("Attempt #{}/{} to obtain machine threw error: {}", new Object[]{i, attempts, e});
                exceptions.add(e);
            }
        }
        String msg = String.format("Failed to get VM after %d attempt%s.", attempts, attempts == 1 ? "" : "s");

        Exception cause = (exceptions.size() == 1) 
                ? exceptions.get(0)
                : new CompoundRuntimeException(msg + " - "
                    + "First cause is "+exceptions.get(0)+" (listed in primary trace); "
                    + "plus " + (exceptions.size()-1) + " more (e.g. the last is "+exceptions.get(exceptions.size()-1)+")", 
                    exceptions.get(0), exceptions);

        if (exceptions.get(exceptions.size()-1) instanceof NoMachinesAvailableException) {
            throw new NoMachinesAvailableException(msg, cause);
        } else {
            throw Exceptions.propagate(cause);
        }
    }

    protected JcloudsSshMachineLocation obtainOnce(ConfigBag setup) throws NoMachinesAvailableException {
        AccessController.Response access = getManagementContext().getAccessController().canProvisionLocation(this);
        if (!access.isAllowed()) {
            throw new IllegalStateException("Access controller forbids provisioning in "+this+": "+access.getMsg());
        }

        setCreationString(setup);
        boolean waitForSshable = !"false".equalsIgnoreCase(setup.get(WAIT_FOR_SSHABLE));
        boolean usePortForwarding = setup.get(USE_PORT_FORWARDING);
        JcloudsPortForwarderExtension portForwarder = setup.get(PORT_FORWARDER);
        if (usePortForwarding) checkNotNull(portForwarder, "portForwarder, when use-port-forwarding enabled");

        final ComputeService computeService = JcloudsUtil.findComputeService(setup);
        CloudMachineNamer cloudMachineNamer = getCloudMachineNamer(setup);
        String groupId = elvis(setup.get(GROUP_ID), cloudMachineNamer.generateNewGroupId());
        NodeMetadata node = null;
        JcloudsSshMachineLocation sshMachineLocation = null;
        
        try {
            LOG.info("Creating VM "+setup.getDescription()+" in "+this);

            Semaphore machineCreationSemaphore = getMachineCreationSemaphore();
            boolean acquired = machineCreationSemaphore.tryAcquire(0, TimeUnit.SECONDS);
            if (!acquired) {
                LOG.info("Waiting in {} for machine-creation permit ({} other queuing requests already)", new Object[] {this, machineCreationSemaphore.getQueueLength()});
                Stopwatch blockStopwatch = Stopwatch.createStarted();
                machineCreationSemaphore.acquire();
                LOG.info("Acquired in {} machine-creation permit, after waiting {}", this, Time.makeTimeStringRounded(blockStopwatch));
            } else {
                LOG.debug("Acquired in {} machine-creation permit immediately", this);
            }
            
            Stopwatch provisioningStopwatch = Stopwatch.createStarted();
            Duration templateTimestamp, provisionTimestamp, usableTimestamp, customizedTimestamp;

            LoginCredentials initialCredentials = null;
            Set<? extends NodeMetadata> nodes;
            Template template;
            try {
                // Setup the template
                template = buildTemplate(computeService, setup);
                if (waitForSshable && !usePortForwarding) {
                    initialCredentials = initTemplateForCreateUser(template, setup);
                }

                //FIXME initialCredentials = initUserTemplateOptions(template, setup);
                for (JcloudsLocationCustomizer customizer : getCustomizers(setup)) {
                    customizer.customize(this, computeService, template);
                    customizer.customize(this, computeService, template.getOptions());
                }
                LOG.debug("jclouds using template {} / options {} to provision machine in {}",
                        new Object[] {template, template.getOptions(), setup.getDescription()});
    
                if (!setup.getUnusedConfig().isEmpty())
                    LOG.debug("NOTE: unused flags passed to obtain VM in "+setup.getDescription()+": "+
                            setup.getUnusedConfig());
                
                templateTimestamp = Duration.of(provisioningStopwatch);
                template.getOptions().getUserMetadata().put("Name", cloudMachineNamer.generateNewMachineUniqueNameFromGroupId(groupId));
                
                nodes = computeService.createNodesInGroup(groupId, 1, template);
                provisionTimestamp = Duration.of(provisioningStopwatch);
            } finally {
                machineCreationSemaphore.release();
            }
            
            node = Iterables.getOnlyElement(nodes, null);
            LOG.debug("jclouds created {} for {}", node, setup.getDescription());
            if (node == null)
                throw new IllegalStateException("No nodes returned by jclouds create-nodes in " + setup.getDescription());

            // Setup port-forwarding, if required
            Optional<HostAndPort> sshHostAndPortOverride;
            if (usePortForwarding) {
                sshHostAndPortOverride = Optional.of(portForwarder.openPortForwarding(
                        node,
                        node.getLoginPort(),
                        Optional.<Integer>absent(), 
                        Protocol.TCP, 
                        Cidr.UNIVERSAL));
                
                if (waitForSshable) {
                    // once that host:port is definitely reachable, we can create the user
                    waitForReachable(computeService, node, sshHostAndPortOverride, node.getCredentials(), setup);
                    initialCredentials = createUser(computeService, node, sshHostAndPortOverride, setup);
                }
            } else {
                sshHostAndPortOverride = Optional.absent();
            }
            
            // Figure out which login-credentials to use
            LoginCredentials customCredentials = setup.get(CUSTOM_CREDENTIALS);
            if (customCredentials != null) {
                initialCredentials = customCredentials;
                //set userName and other data, from these credentials
                Object oldUsername = setup.put(USER, customCredentials.getUser());
                LOG.debug("node {} username {} / {} (customCredentials)", new Object[] { node, customCredentials.getUser(), oldUsername });
                if (groovyTruth(customCredentials.getPassword())) setup.put(PASSWORD, customCredentials.getPassword());
                if (groovyTruth(customCredentials.getPrivateKey())) setup.put(PRIVATE_KEY_DATA, customCredentials.getPrivateKey());
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
            if (waitForSshable) {
                waitForReachable(computeService, node, sshHostAndPortOverride, initialCredentials, setup);
            } else {
                LOG.debug("Skipping ssh check for {} ({}) due to config waitForSshable=false", node, setup.getDescription());
            }
            usableTimestamp = Duration.of(provisioningStopwatch);
            
            // Create a JcloudsSshMachineLocation, and register it
            sshMachineLocation = registerJcloudsSshMachineLocation(computeService, node, initialCredentials, sshHostAndPortOverride, setup);
            if (template!=null && sshMachineLocation.getTemplate()==null) {
                sshMachineLocation.template = template;
            }

            if ("docker".equals(this.getProvider())) {
                Map<Integer, Integer> portMappings = JcloudsUtil.dockerPortMappingsFor(this, node.getId());
                PortForwardManager portForwardManager = setup.get(PORT_FORWARDING_MANAGER);
                if (portForwardManager != null) {
                    for(Integer containerPort : portMappings.keySet()) {
                        Integer hostPort = portMappings.get(containerPort);
                        String dockerHost = sshMachineLocation.getSshHostAndPort().getHostText();
                        portForwardManager.recordPublicIpHostname(node.getId(), dockerHost);
                        portForwardManager.acquirePublicPortExplicit(node.getId(), hostPort);
                        portForwardManager.associate(node.getId(), hostPort, sshMachineLocation, containerPort);
                    }
                } else {
                    LOG.warn("No port-forward manager for {} so could not associate docker port-mappings for {}",
                            this, sshMachineLocation);
                }
            }

            List<String> customisationForLogging = new ArrayList<String>();
            // Apply same securityGroups rules to iptables, if iptables is running on the node
            if (waitForSshable) {
                
                String setupScript = setup.get(JcloudsLocationConfig.CUSTOM_MACHINE_SETUP_SCRIPT_URL);
                if (Strings.isNonBlank(setupScript)) {
                    customisationForLogging.add("custom setup script "+setupScript);
                    
                    String setupVarsString = setup.get(JcloudsLocationConfig.CUSTOM_MACHINE_SETUP_SCRIPT_VARS);
                    Map<String, String> substitutions = (setupVarsString != null)
                            ? Splitter.on(",").withKeyValueSeparator(":").split(setupVarsString)
                            : ImmutableMap.<String, String>of();
                    String scriptContent =  ResourceUtils.create(this).getResourceAsString(setupScript);
                    String script = TemplateProcessor.processTemplateContents(scriptContent, substitutions);
                    sshMachineLocation.execCommands("Customizing node " + this, ImmutableList.of(script));
                }
                
                if (setup.get(JcloudsLocationConfig.MAP_DEV_RANDOM_TO_DEV_URANDOM)) {
                    customisationForLogging.add("point /dev/random to urandom");
                    
                    sshMachineLocation.execCommands("using urandom instead of random", 
                            Arrays.asList("sudo mv /dev/random /dev/random-real", "sudo ln -s /dev/urandom /dev/random"));
                }

                
                if (setup.get(GENERATE_HOSTNAME)) {
                    customisationForLogging.add("configure hostname");
                    
                    sshMachineLocation.execCommands("Generate hostname " + node.getName(), 
                            Arrays.asList("sudo hostname " + node.getName(),
                                    "sudo sed -i \"s/HOSTNAME=.*/HOSTNAME=" + node.getName() + "/g\" /etc/sysconfig/network",
                                    "sudo bash -c \"echo 127.0.0.1   `hostname` >> /etc/hosts\"")
                   );
                }

                if (setup.get(OPEN_IPTABLES)) {
                    customisationForLogging.add("open iptables");
                    
                    List<String> iptablesRules = createIptablesRulesForNetworkInterface((Iterable<Integer>) setup.get(INBOUND_PORTS));
                    iptablesRules.add(IptablesCommands.saveIptablesRules());
                    sshMachineLocation.execCommands("Inserting iptables rules", iptablesRules);
                    sshMachineLocation.execCommands("List iptables rules", ImmutableList.of(IptablesCommands.listIptablesRule()));
                }
                
                if (setup.get(STOP_IPTABLES)) {
                    customisationForLogging.add("stop iptables");
                    
                    List<String> cmds = ImmutableList.of(IptablesCommands.iptablesServiceStop(), IptablesCommands.iptablesServiceStatus());
                    sshMachineLocation.execCommands("Stopping iptables", cmds);
                }
            } else {
                // Otherwise we have deliberately not waited to be ssh'able, so don't try now to 
                // ssh to exec these commands!
            }
            
            // Apply any optional app-specific customization.
            for (JcloudsLocationCustomizer customizer : getCustomizers(setup)) {
                customizer.customize(this, computeService, sshMachineLocation);
            }
            
            customizedTimestamp = Duration.of(provisioningStopwatch);
            
            LOG.info("Finished VM "+setup.getDescription()+" creation:"
                + " "+sshMachineLocation.getUser()+"@"+sshMachineLocation.getAddress() + " ready after "+Duration.of(provisioningStopwatch).toStringRounded()
                + " ("+template+" template built in "+Duration.of(templateTimestamp).toStringRounded()+";"
                + " "+node+" provisioned in "+Duration.of(provisionTimestamp).subtract(templateTimestamp).toStringRounded()+";"
                + " "+sshMachineLocation+" ssh usable in "+Duration.of(usableTimestamp).subtract(provisionTimestamp).toStringRounded()+";"
                + " and os customized in "+Duration.of(customizedTimestamp).subtract(usableTimestamp).toStringRounded()+" - "+Joiner.on(", ").join(customisationForLogging)+")");

            return sshMachineLocation;
        } catch (Exception e) {
            if (e instanceof RunNodesException && ((RunNodesException)e).getNodeErrors().size() > 0) {
                node = Iterables.get(((RunNodesException)e).getNodeErrors().keySet(), 0);
            }
            // sometimes AWS nodes come up busted (eg ssh not allowed); just throw it back (and maybe try for another one)
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
            .put(OS_64_BIT, new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        Boolean os64Bit = TypeCoercions.coerce(v, Boolean.class);
                        if (os64Bit!=null)
                            tb.os64Bit(os64Bit);
                    }})
            .put(MIN_RAM, new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        tb.minRam(TypeCoercions.coerce(v, Integer.class));
                    }})
            .put(MIN_CORES, new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        tb.minCores(TypeCoercions.coerce(v, Double.class));
                    }})
            .put(MIN_DISK, new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        tb.minDisk(TypeCoercions.coerce(v, Double.class));
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
            .put(OS_FAMILY, new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        Maybe<OsFamily> osFamily = Enums.valueOfIgnoreCase(OsFamily.class, v.toString());
                        if (osFamily.isAbsent())
                            throw new IllegalArgumentException("Invalid "+OS_FAMILY+" value "+v);
                        tb.osFamily(osFamily.get());
                    }})
            .put(OS_VERSION_REGEX, new CustomizeTemplateBuilder() {
                    public void apply(TemplateBuilder tb, ConfigBag props, Object v) {
                        tb.osVersionMatches( ((CharSequence)v).toString() );
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
                        } else if (t instanceof SoftLayerTemplateOptions) {
                            String[] securityGroups = toStringArray(v);
                            ((SoftLayerTemplateOptions)t).securityGroups(securityGroups);
                        } else if (t instanceof GoogleComputeEngineTemplateOptions) {
                            String[] securityGroups = toStringArray(v);
                            ((GoogleComputeEngineTemplateOptions)t).securityGroups(securityGroups);
                        } else {
                            LOG.info("ignoring securityGroups({}) in VM creation because not supported for cloud/type ({})", v, t.getClass());
                        }
                    }})
            .put(INBOUND_PORTS, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        int[] inboundPorts = toIntArray(v);
                        if (LOG.isDebugEnabled()) LOG.debug("opening inbound ports {} for cloud/type {}", Arrays.toString(inboundPorts), t.getClass());
                        t.inboundPorts(inboundPorts);
                    }})
            .put(USER_METADATA_STRING, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (t instanceof EC2TemplateOptions) {
                            if (v==null) return;
                            ((EC2TemplateOptions)t).userData(v.toString().getBytes());
                        } else {
                            LOG.info("ignoring userDataString({}) in VM creation because not supported for cloud/type ({})", v, t.getClass());
                        }
                    }})
            .put(USER_DATA_UUENCODED, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (t instanceof EC2TemplateOptions) {
                            byte[] bytes = toByteArray(v);
                            ((EC2TemplateOptions)t).userData(bytes);
                        } else {
                            LOG.info("ignoring userData({}) in VM creation because not supported for cloud/type ({})", v, t.getClass());
                        }
                    }})
            .put(STRING_TAGS, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        List<String> tags = toListOfStrings(v);
                        if (LOG.isDebugEnabled()) LOG.debug("setting VM tags {} for {}", tags, t);
                        t.tags(tags);
                    }})
            .put(USER_METADATA_MAP, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (v != null) {
                            t.userMetadata(toMapStringString(v));
                        }
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
                        if (v != null) {
                            t.overrideLoginUser(((CharSequence)v).toString());
                        }
                    }})
            .put(LOGIN_USER_PASSWORD, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (v != null) {
                            t.overrideLoginPassword(((CharSequence)v).toString());
                        }
                    }})
            .put(LOGIN_USER_PRIVATE_KEY_FILE, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (v != null) {
                            String privateKeyFileName = ((CharSequence)v).toString();
                            String privateKey;
                            try {
                                privateKey = Files.toString(new File(Os.tidyPath(privateKeyFileName)), Charsets.UTF_8);
                            } catch (IOException e) {
                                LOG.error(privateKeyFileName + "not found", e);
                                throw Exceptions.propagate(e);
                            }
                            t.overrideLoginPrivateKey(privateKey);
                        }
                    }})
            .put(LOGIN_USER_PRIVATE_KEY_DATA, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (v != null) {
                            t.overrideLoginPrivateKey(((CharSequence)v).toString());
                        }
                    }})                    
            .put(KEY_PAIR, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (t instanceof EC2TemplateOptions) {
                            ((EC2TemplateOptions)t).keyPair(((CharSequence)v).toString());
                        } else if (t instanceof NovaTemplateOptions) {
                            ((NovaTemplateOptions)t).keyPairName(((CharSequence)v).toString());
                        } else if (t instanceof CloudStackTemplateOptions) {
                            ((CloudStackTemplateOptions) t).keyPair(((CharSequence) v).toString());
                        } else {
                            LOG.info("ignoring keyPair({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }
                    }})
             .put(AUTO_GENERATE_KEYPAIRS, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (t instanceof NovaTemplateOptions) {
                            ((NovaTemplateOptions)t).generateKeyPair((Boolean)v);
                        } else if (t instanceof CloudStackTemplateOptions) {
                            ((CloudStackTemplateOptions) t).generateKeyPair((Boolean) v);
                        } else {
                            LOG.info("ignoring auto-generate-keypairs({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }
                    }})
             .put(AUTO_CREATE_FLOATING_IPS, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (t instanceof NovaTemplateOptions) {
                            ((NovaTemplateOptions)t).autoAssignFloatingIp((Boolean)v);
                        } else {
                            LOG.info("ignoring auto-generate-floating-ips({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }
                    }}) 
             .put(AUTO_ASSIGN_FLOATING_IP, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (t instanceof NovaTemplateOptions) {
                            ((NovaTemplateOptions)t).autoAssignFloatingIp((Boolean)v);
                        } else if (t instanceof CloudStackTemplateOptions) {
                            ((CloudStackTemplateOptions)t).setupStaticNat((Boolean)v);
                        } else {
                            LOG.info("ignoring auto-assign-floating-ip({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }
                    }}) 
              .put(OVERRIDE_RAM, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        if (t instanceof AbiquoTemplateOptions) {
                            ((AbiquoTemplateOptions)t).overrideRam((Integer)v);
                        } else {
                            LOG.info("ignoring overrideRam({}) in VM creation because not supported for cloud/type ({})", v, t);
                        }     
                    }})
              .put(NETWORK_NAME, new CustomizeTemplateOptions() {
                    public void apply(TemplateOptions t, ConfigBag props, Object v) {
                        t.networks((String)v);
                    }})
            .build();

    private static boolean listedAvailableTemplatesOnNoSuchTemplate = false;

    /** returns the jclouds Template which describes the image to be built, for the given config and compute service */
    public Template buildTemplate(ComputeService computeService, ConfigBag config) {
        TemplateBuilder templateBuilder = (TemplateBuilder) config.get(TEMPLATE_BUILDER);
        if (templateBuilder==null) {
            templateBuilder = new PortableTemplateBuilder<PortableTemplateBuilder<?>>();
        } else {
            LOG.debug("jclouds using templateBuilder {} as custom base for provisioning in {} for {}", new Object[] {
                    templateBuilder, this, config.getDescription()});
        }
        if (templateBuilder instanceof PortableTemplateBuilder<?>) {
            if (((PortableTemplateBuilder<?>)templateBuilder).imageChooser()==null) {
                templateBuilder.imageChooser(config.get(JcloudsLocationConfig.IMAGE_CHOOSER));
            } else {
                // an image chooser is already set, so do nothing
            }
        } else {
            // template builder supplied, and we cannot check image chooser status; warn, for now
            LOG.warn("Cannot check imageChooser status for {} due to manually supplied black-box TemplateBuilder; "
                + "it is recommended to use a PortableTemplateBuilder if you supply a TemplateBuilder", config.getDescription());
        }

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
            if (groovyTruth(config.get(DEFAULT_IMAGE_ID))) {
                if (((PortableTemplateBuilder<?>)templateBuilder).isBlank()) {
                    templateBuilder.imageId(config.get(DEFAULT_IMAGE_ID).toString());
                }
            }
        }

        // Then apply any optional app-specific customization.
        for (JcloudsLocationCustomizer customizer : getCustomizers(config)) {
            customizer.customize(this, computeService, templateBuilder);
        }
        
        LOG.debug("jclouds using templateBuilder {} for provisioning in {} for {}", new Object[] {
            templateBuilder, this, config.getDescription()});
        
        // Finally try to build the template
        Template template;
        try {
            template = templateBuilder.build();
            if (template==null) throw new NullPointerException("No template found (templateBuilder.build returned null)");
            LOG.debug("jclouds found template "+template+" (image "+template.getImage()+") for provisioning in "+this+" for "+config.getDescription());
            if (template.getImage()==null) throw new NullPointerException("Template does not contain an image (templateBuilder.build returned invalid template)");
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
                throw new IllegalStateException("Unable to access cloud "+this+" to resolve "+templateBuilder+": "+e, e);
            }
            throw new IllegalStateException("Unable to match required VM template constraints "+templateBuilder+" when trying to provision VM in "+this+"; "
                + "see list of images in log. Root cause: "+e, e);
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
    
    protected SshMachineLocation createTemporarySshMachineLocation(HostAndPort hostAndPort, LoginCredentials creds, ConfigBag config) {
        Optional<String> initialPassword = creds.getOptionalPassword();
        Optional<String> initialPrivateKey = creds.getOptionalPrivateKey();
        String initialUser = creds.getUser();
        
        Map<String,Object> sshProps = Maps.newLinkedHashMap(config.getAllConfig());
        sshProps.put("user", initialUser);
        sshProps.put("address", hostAndPort.getHostText());
        sshProps.put("port", hostAndPort.getPort());
        if (initialPassword.isPresent()) sshProps.put("password", initialPassword.get());
        if (initialPrivateKey.isPresent()) sshProps.put("privateKeyData", initialPrivateKey.get());
        if (initialPrivateKey.isPresent()) sshProps.put("privateKeyData", initialPrivateKey.get());
        
        if (isManaged()) {
            return getManagementContext().getLocationManager().createLocation(sshProps, SshMachineLocation.class);
        } else {
            return new SshMachineLocation(sshProps);
        }
    }

    /**
     * Create the user immediately - executing ssh commands as required.
     */
    protected LoginCredentials createUser(ComputeService computeService, NodeMetadata node, Optional<HostAndPort> hostAndPortOverride, ConfigBag config) {
        UserCreation userCreation = createUserStatements(computeService.getImage(node.getImageId()), config);
        
        if (!userCreation.statements.isEmpty()) {
            org.jclouds.compute.domain.OsFamily osFamily = node.getOperatingSystem().getFamily();
            org.jclouds.scriptbuilder.domain.OsFamily scriptOsFamily = (osFamily == org.jclouds.compute.domain.OsFamily.WINDOWS) 
                    ? org.jclouds.scriptbuilder.domain.OsFamily.WINDOWS
                    : org.jclouds.scriptbuilder.domain.OsFamily.UNIX;
            
            List<String> commands = Lists.newArrayList();
            for (Statement statement : userCreation.statements) {
                InitAdminAccess initAdminAccess = new InitAdminAccess(new AdminAccessConfiguration.Default());
                initAdminAccess.visit(statement);
                commands.add(statement.render(scriptOsFamily));
            }
    
            LoginCredentials initialCredentials = node.getCredentials();
            Optional<String> initialPassword = initialCredentials.getOptionalPassword();
            Optional<String> initialPrivateKey = initialCredentials.getOptionalPrivateKey();
            String initialUser = initialCredentials.getUser();
            String address = hostAndPortOverride.isPresent() ? hostAndPortOverride.get().getHostText() : JcloudsUtil.getFirstReachableAddress(computeService.getContext(), node);
            int port = hostAndPortOverride.isPresent() ? hostAndPortOverride.get().getPort() : node.getLoginPort();
            
            Map<String,Object> sshProps = Maps.newLinkedHashMap(config.getAllConfig());
            sshProps.put("user", initialUser);
            sshProps.put("address", address);
            sshProps.put("port", port);
            if (initialPassword.isPresent()) sshProps.put("password", initialPassword.get());
            if (initialPrivateKey.isPresent()) sshProps.put("privateKeyData", initialPrivateKey.get());
            
            Map<String,Object> execProps = Maps.newLinkedHashMap();
            execProps.put(ShellTool.PROP_RUN_AS_ROOT.getName(), true);
            
            SshMachineLocation sshLoc = null;
            try {
                if (isManaged()) {
                    sshLoc = getManagementContext().getLocationManager().createLocation(sshProps, SshMachineLocation.class);
                } else {
                    sshLoc = new SshMachineLocation(sshProps);
                }
                
                int exitcode = sshLoc.execScript(execProps, "create-user", commands);
                if (exitcode != 0) {
                    LOG.warn("exit code {} when creating user for {}; usage may subsequently fail", exitcode, node);
                }
            } finally {
                getManagementContext().getLocationManager().unmanage(sshLoc);
                Streams.closeQuietly(sshLoc);
            }
        }

        return userCreation.loginCredentials;
    }
    
    /**
     * Setup the TemplateOptions to create the user.
     */
    protected LoginCredentials initTemplateForCreateUser(Template template, ConfigBag config) {
        UserCreation userCreation = createUserStatements(template.getImage(), config);
        
        if (userCreation.statements.size() > 0) {
            TemplateOptions options = template.getOptions();
            options.runScript(new StatementList(userCreation.statements));
        }

        return userCreation.loginCredentials;
    }
    
    protected static class UserCreation {
        public final LoginCredentials loginCredentials;
        public final List<Statement> statements;
        
        public UserCreation(LoginCredentials creds, List<Statement> statements) {
            this.loginCredentials = creds;
            this.statements = statements;
        }
    }
    
    /**
     * Returns the commands required to create the user, to be used for connecting (e.g. over ssh)
     * to the machine; also returns the expected login credentials.
     * <p>
     * The returned login credentials may be null if we haven't done any user-setup and no specific 
     * user was supplied (i.e. if {@code dontCreateUser} was true and {@code user} was null or blank).
     * In which case, the caller should use the jclouds node's login credentials.
     * <p>
     * There are quite a few configuration options. Depending on their values, the user-creation 
     * behaves differently:
     * <ul>
     *   <li>{@code dontCreateUser} says not to run any user-setup commands at all. If {@code user} is
     *       non-empty (including with the default value), then that user will subsequently be used,
     *       otherwise the (inferred) {@code loginUser} will be used.
     *   <li>{@code loginUser} refers to the existing user that jclouds should use when setting up the VM.
     *       Normally this will be inferred from the image (i.e. doesn't need to be explicitly set), but sometimes 
     *       the image gets it wrong so this can be a handy override.
     *   <li>{@code user} is the username for brooklyn to subsequently use when ssh'ing to the machine.
     *       If not explicitly set, its value will default to the username of the user running brooklyn.
     *       <ul>
     *         <li>If the {@code user} value is null or empty, then the (inferred) {@code loginUser} will 
     *             subsequently be used, setting up the password/authorizedKeys for that loginUser.
     *         <li>If the {@code user} is "root", then setup the password/authorizedKeys for root.
     *         <li>If the {@code user} equals the (inferred) {@code loginUser}, then don't try to create this
     *             user but instead just setup the password/authorizedKeys for the user.
     *         <li>Otherwise create the given user, setting up the password/authorizedKeys (unless
     *             {@code dontCreateUser} is set, obviously).
     *       </ul>
     *   <li>{@code publicKeyData} is the key to authorize (i.e. add to .ssh/authorized_keys),
     *       if not null or blank. Note the default is to use {@code ~/.ssh/id_rsa.pub} or {@code ~/.ssh/id_dsa.pub}
     *       if either of those files exist for the user running brooklyn.
     *       Related is {@code publicKeyFile}, which is used to populate publicKeyData.
     *   <li>{@code password} is the password to set for the user. If null or blank, then a random password
     *       will be auto-generated and set.
     *   <li>{@code privateKeyData} is the key to use when subsequent ssh'ing, if not null or blank. 
     *       Note the default is to use {@code ~/.ssh/id_rsa} or {@code ~/.ssh/id_dsa}.
     *       The subsequent preferences for ssh'ing are:
     *       <ul>
     *         <li>Use the {@code privateKeyData} if not null or blank (including if using default)
     *         <li>Use the {@code password} (or the auto-generated password if that is blank). 
     *       </ul>
     *   <li>{@code grantUserSudo} determines whether or not the created user may run the sudo command.</li>
     * </ul>
     *   
     * @param image  The image being used to create the VM
     * @param config Configuration for creating the VM
     * @return       The commands required to create the user, along with the expected login credentials.
     */
    protected UserCreation createUserStatements(Image image, ConfigBag config) {
        //NB: we ignore private key here because, by default we probably should not be installing it remotely;
        //also, it may not be valid for first login (it is created before login e.g. on amazon, so valid there;
        //but not elsewhere, e.g. on rackspace).
        
        LoginCredentials loginCreds = null;
        String user = getUser(config);
        String explicitLoginUser = config.get(LOGIN_USER);
        String loginUser = groovyTruth(explicitLoginUser) ? explicitLoginUser : (image.getDefaultCredentials() != null) ? image.getDefaultCredentials().identity : null;
        Boolean dontCreateUser = config.get(DONT_CREATE_USER);
        Boolean grantUserSudo = config.get(GRANT_USER_SUDO);
        String publicKeyData = LocationConfigUtils.getPublicKeyData(config);
        String privateKeyData = LocationConfigUtils.getPrivateKeyData(config);
        String explicitPassword = config.get(PASSWORD);
        String password = groovyTruth(explicitPassword) ? explicitPassword : Identifiers.makeRandomId(12);
        List<Statement> statements = Lists.newArrayList();
        
        if (groovyTruth(dontCreateUser)) {
            // TODO For dontCreateUser, we probably only want to treat it special if user was explicitly supplied
            // (rather than it just being the default config key value). If user was explicit, then should
            // set the password + authorize the key for that user. Presumably the caller knows that this
            // user pre-exists on the given VM image.
            if (!groovyTruth(user)) {
                // loginCreds result will be null; use creds returned by jclouds on the node
                LOG.info("Not setting up any user (subsequently using loginUser {})", user, loginUser);
                config.put(USER, loginUser);
                
            } else {
                LOG.info("Not creating user {}, and not setting its password or authorizing keys", user);
                
                if (privateKeyData != null) {
                    loginCreds = LoginCredentials.builder().user(user).privateKey(privateKeyData).build();
                } else if (explicitPassword != null) {
                    loginCreds = LoginCredentials.builder().user(user).password(password).build();
                }
            }
            
        } else if (!groovyTruth(user) || user.equals(loginUser)) {
            // For subsequent ssh'ing, we'll be using the loginUser
            if (!groovyTruth(user)) {
                config.put(USER, loginUser);
            }

            // Using the pre-existing loginUser; setup the publicKey/password so can login as expected
            if (password != null) {
                statements.add(new ReplaceShadowPasswordEntry(Sha512Crypt.function(), loginUser, password));
                loginCreds = LoginCredentials.builder().user(loginUser).password(password).build();
            }
            if (publicKeyData!=null) {
                statements.add(new AuthorizeRSAPublicKeys("~"+loginUser+"/.ssh", ImmutableList.of(publicKeyData)));
                if (privateKeyData != null) {
                    loginCreds = LoginCredentials.builder().user(loginUser).privateKey(privateKeyData).build();
                }
            }
            
        } else if (user.equals(ROOT_USERNAME)) {
            // Authorizes the public-key and sets password for the root user, so can login as expected
            if (password != null) {
                statements.add(new ReplaceShadowPasswordEntry(Sha512Crypt.function(), ROOT_USERNAME, password));
                loginCreds = LoginCredentials.builder().user(user).password(password).build();
            }
            if (publicKeyData!=null) {
                statements.add(new AuthorizeRSAPublicKeys("~"+ROOT_USERNAME+"/.ssh", ImmutableList.of(publicKeyData)));
                if (privateKeyData != null) {
                    loginCreds = LoginCredentials.builder().user(user).privateKey(privateKeyData).build();
                }
            }
            
        } else {
            // Create the user
            // note AdminAccess requires _all_ fields set, due to http://code.google.com/p/jclouds/issues/detail?id=1095
            AdminAccess.Builder adminBuilder = AdminAccess.builder()
                    .adminUsername(user)
                    .adminPassword(password)
                    .grantSudoToAdminUser(groovyTruth(grantUserSudo))
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
            
            if (groovyTruth(explicitPassword)) {
                adminBuilder.lockSsh(false);
            } else if (publicKeyData != null) {
                adminBuilder.lockSsh(true);
            } else {
                // no keys or passwords supplied; using only defaults!
                adminBuilder.lockSsh(false);
            }

            
            statements.add(adminBuilder.build());
            
            if (groovyTruth(publicKeyData) && groovyTruth(privateKeyData)) {
                // assume have uploaded corresponding .pub file
                loginCreds = LoginCredentials.builder().user(user).privateKey(privateKeyData).build();
            } else {
                loginCreds = LoginCredentials.builder().user(user).password(password).build();
            }
        }
        
        return new UserCreation(loginCreds, statements);
    }


    // ----------------- rebinding to existing machine ------------------------

    public JcloudsSshMachineLocation rebindMachine(NodeMetadata metadata) throws NoMachinesAvailableException {
        return rebindMachine(MutableMap.of(), metadata);
    }
    public JcloudsSshMachineLocation rebindMachine(Map flags, NodeMetadata metadata) throws NoMachinesAvailableException {
        ConfigBag setup = ConfigBag.newInstanceExtending(getAllConfigBag(), flags);
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
            
            final String rawId = (String) setup.getStringKey("id");
            final String rawHostname = (String) setup.getStringKey("hostname");
            String user = checkNotNull(getUser(setup), "user");
            final String rawRegion = (String) setup.getStringKey("region");
            
            LOG.info("Rebinding to VM {} ({}@{}), in jclouds location for provider {}", 
                    new Object[] {rawId!=null ? rawId : "<lookup>", 
                        user, 
                        (rawHostname != null ? rawHostname : "<unspecified>"), 
                        getProvider()});
            
            ComputeService computeService = JcloudsUtil.findComputeService(setup, true);
            
            Set<? extends NodeMetadata> candidateNodes = computeService.listNodesDetailsMatching(new Predicate<ComputeMetadata>() {
                @Override
                public boolean apply(ComputeMetadata input) {
                    // ID exact match
                    if (rawId!=null) {
                        if (rawId.equals(input.getId())) return true;
                        // AWS format
                        if (rawRegion!=null && (rawRegion+"/"+rawId).equals(input.getId())) return true;
                    }
                    // else do node metadata lookup
                    if (!(input instanceof NodeMetadata)) return false;
                    if (rawHostname!=null && rawHostname.equalsIgnoreCase( ((NodeMetadata)input).getHostname() )) return true;
                    if (rawHostname!=null && ((NodeMetadata)input).getPublicAddresses().contains(rawHostname)) return true;
                    
                    if (rawId!=null && rawId.equalsIgnoreCase( ((NodeMetadata)input).getHostname() )) return true;
                    if (rawId!=null && ((NodeMetadata)input).getPublicAddresses().contains(rawId)) return true;
                    // don't do private IP's because those might be repeated
                    
                    if (rawId!=null && rawId.equalsIgnoreCase( ((NodeMetadata)input).getProviderId() )) return true;
                    if (rawHostname!=null && rawHostname.equalsIgnoreCase( ((NodeMetadata)input).getProviderId() )) return true;
                    
                    return false;
                }
            });
            
            if (candidateNodes.isEmpty())
                throw new IllegalArgumentException("Jclouds node not found for rebind, looking for id="+rawId+" and hostname="+rawHostname);
            if (candidateNodes.size()>1)
                throw new IllegalArgumentException("Jclouds node for rebind matching multiple, looking for id="+rawId+" and hostname="+rawHostname+": "+candidateNodes);

            NodeMetadata node = Iterables.getOnlyElement(candidateNodes);
            String pkd = LocationConfigUtils.getPrivateKeyData(setup);
            if (groovyTruth(pkd)) {
                LoginCredentials expectedCredentials = LoginCredentials.fromCredentials(new Credentials(user, pkd));
                //override credentials
                node = NodeMetadataBuilder.fromNodeMetadata(node).credentials(expectedCredentials).build();
            }
            
            // TODO confirm we can SSH ?
            // NB if rawHostname not set, get the hostname using getPublicHostname(node, Optional.<HostAndPort>absent(), setup);

            return registerJcloudsSshMachineLocation(computeService, node, null, Optional.<HostAndPort>absent(), setup);
            
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }

    public JcloudsSshMachineLocation rebindMachine(Map flags) throws NoMachinesAvailableException {
        ConfigBag setup = ConfigBag.newInstanceExtending(getAllConfigBag(), flags);
        return rebindMachine(setup);
    }

    // -------------- create the SshMachineLocation instance, and connect to it etc ------------------------
    
    /** @deprecated since 0.7.0 use {@link #registerJcloudsSshMachineLocation(ComputeService, NodeMetadata, LoginCredentials, Optional, ConfigBag)} */
    @Deprecated
    protected final JcloudsSshMachineLocation registerJcloudsSshMachineLocation(NodeMetadata node, String vmHostname, Optional<HostAndPort> sshHostAndPort, ConfigBag setup) throws IOException {
        LOG.warn("Using deprecated registerJcloudsSshMachineLocation: now wants computeService passed", new Throwable("source of deprecated registerJcloudsSshMachineLocation invocation"));
        return registerJcloudsSshMachineLocation(null, node, null, sshHostAndPort, setup);
    }
    
    protected JcloudsSshMachineLocation registerJcloudsSshMachineLocation(ComputeService computeService, NodeMetadata node, LoginCredentials initialCredentials, Optional<HostAndPort> sshHostAndPort, ConfigBag setup) throws IOException {
        if (initialCredentials==null)
            initialCredentials = node.getCredentials();
        
        String vmHostname = getPublicHostname(node, sshHostAndPort, setup);
        
        JcloudsSshMachineLocation machine = createJcloudsSshMachineLocation(computeService, node, vmHostname, sshHostAndPort, setup);
        machine.setParent(this);
        vmInstanceIds.put(machine, node.getId());
        return machine;
    }

    /** @deprecated since 0.7.0 use variant which takes compute service; no longer called internally,
     * so marked final to force any overrides to switch to new syntax */
    @Deprecated
    protected final JcloudsSshMachineLocation createJcloudsSshMachineLocation(NodeMetadata node, String vmHostname, Optional<HostAndPort> sshHostAndPort, ConfigBag setup) throws IOException {
        return createJcloudsSshMachineLocation(null, node, vmHostname, sshHostAndPort, setup);
    }
    protected JcloudsSshMachineLocation createJcloudsSshMachineLocation(ComputeService computeService, NodeMetadata node, String vmHostname, Optional<HostAndPort> sshHostAndPort, ConfigBag setup) throws IOException {
        Map<?,?> sshConfig = extractSshConfig(setup, node);
        String nodeAvailabilityZone = extractAvailabilityZone(setup, node);
        String nodeRegion = extractRegion(setup, node);
        if (nodeRegion == null) {
            // e.g. rackspace doesn't have "region", so rackspace-uk is best we can say (but zone="LON")
            nodeRegion = extractProvider(setup, node);
        }

        String address = sshHostAndPort.isPresent() ? sshHostAndPort.get().getHostText() : vmHostname;
        try {
            Networking.getInetAddressWithFixedName(address);
            // fine, it resolves
        } catch (Exception e) {
            // occurs if an unresolvable hostname is given as vmHostname, and the machine only has private IP addresses but they are reachable
            // TODO cleanup use of getPublicHostname so its semantics are clearer, returning reachable hostname or ip, and 
            // do this check/fix there instead of here!
            Exceptions.propagateIfFatal(e);
            LOG.debug("Could not resolve reported address '"+address+"' for "+vmHostname+" ("+setup.getDescription()+"/"+node+"), requesting reachable address");
            if (computeService==null) throw Exceptions.propagate(e);
            // this has sometimes already been done in waitForReachable (unless skipped) but easy enough to do again
            address = JcloudsUtil.getFirstReachableAddress(computeService.getContext(), node);
        }
        
        if (LOG.isDebugEnabled())
            LOG.debug("creating JcloudsSshMachineLocation representation for {}@{} ({}/{}) for {}/{}", 
                    new Object[] {
                            getUser(setup), 
                            address, 
                            Entities.sanitize(sshConfig),
                            sshHostAndPort,
                            setup.getDescription(), 
                            node
                    });
        
        if (isManaged()) {
            return getManagementContext().getLocationManager().createLocation(LocationSpec.create(JcloudsSshMachineLocation.class)
                    .configure("displayName", vmHostname)
                    .configure("address", address) 
                    .configure("port", sshHostAndPort.isPresent() ? sshHostAndPort.get().getPort() : node.getLoginPort()) 
                    .configure("user", getUser(setup))
                    // don't think "config" does anything
                    .configure(sshConfig)
                    // FIXME remove "config" -- inserted directly, above
                    .configure("config", sshConfig)
                    .configure("jcloudsParent", this)
                    .configure("node", node)
                    .configureIfNotNull(CLOUD_AVAILABILITY_ZONE_ID, nodeAvailabilityZone)
                    .configureIfNotNull(CLOUD_REGION_ID, nodeRegion)
                    .configure(CALLER_CONTEXT, setup.get(CALLER_CONTEXT)));
        } else {
            LOG.warn("Using deprecated JcloudsSshMachineLocation constructor because "+this+" is not managed");
            return new JcloudsSshMachineLocation(MutableMap.builder()
                    .put("displayName", vmHostname)
                    .put("address", address) 
                    .put("port", sshHostAndPort.isPresent() ? sshHostAndPort.get().getPort() : node.getLoginPort()) 
                    .put("user", getUser(setup))
                    // don't think "config" does anything
                    .putAll(sshConfig)
                    // FIXME remove "config" -- inserted directly, above
                    .put("config", sshConfig)
                    .put("callerContext", setup.get(CALLER_CONTEXT))
                    .putIfNotNull(CLOUD_AVAILABILITY_ZONE_ID.getName(), nodeAvailabilityZone)
                    .putIfNotNull(CLOUD_REGION_ID.getName(), nodeRegion)
                    .build(),
                    this,
                    node);
        }
    }

    // -------------- give back the machines------------------
    
    protected Map<String,Object> extractSshConfig(ConfigBag setup, NodeMetadata node) {
        ConfigBag nodeConfig = new ConfigBag();
        if (node!=null && node.getCredentials() != null) {
            nodeConfig.putIfNotNull(PASSWORD, node.getCredentials().getPassword());
            nodeConfig.putIfNotNull(PRIVATE_KEY_DATA, node.getCredentials().getPrivateKey());
        }
        return extractSshConfig(setup, nodeConfig).getAllConfig();
    }

    protected String extractAvailabilityZone(ConfigBag setup, NodeMetadata node) {
        return extractNodeLocationId(setup, node, LocationScope.ZONE);
    }

    protected String extractRegion(ConfigBag setup, NodeMetadata node) {
        return extractNodeLocationId(setup, node, LocationScope.REGION);
    }

    protected String extractProvider(ConfigBag setup, NodeMetadata node) {
        return extractNodeLocationId(setup, node, LocationScope.PROVIDER);
    }

    protected String extractNodeLocationId(ConfigBag setup, NodeMetadata node, LocationScope scope) {
        org.jclouds.domain.Location nodeLoc = node.getLocation();
        if(nodeLoc == null) return null; 
        do {
            if (nodeLoc.getScope() == scope) return nodeLoc.getId();
            nodeLoc = nodeLoc.getParent();
        } while (nodeLoc != null);
        return null;
    }


    @Override
    public void release(SshMachineLocation machine) {
        String instanceId = vmInstanceIds.remove(machine);
        if (!groovyTruth(instanceId)) {
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
            computeService = JcloudsUtil.findComputeService(getAllConfigBag());
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
        
        if (groovyTruth(nodeCredentials.getUser())) {
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

    protected void waitForReachable(final ComputeService computeService, final NodeMetadata node, Optional<HostAndPort> hostAndPortOverride, final LoginCredentials expectedCredentials, ConfigBag setup) {
        String waitForSshable = setup.get(WAIT_FOR_SSHABLE);
        checkArgument(!"false".equalsIgnoreCase(waitForSshable), "waitForReachable called despite waitForSshable=%s", waitForSshable);
        
        String vmIp = hostAndPortOverride.isPresent() ? hostAndPortOverride.get().getHostText() : JcloudsUtil.getFirstReachableAddress(computeService.getContext(), node);
        if (vmIp==null) LOG.warn("Unable to extract IP for "+node+" ("+setup.getDescription()+"): subsequent connection attempt will likely fail");
        
        int vmPort = hostAndPortOverride.isPresent() ? hostAndPortOverride.get().getPortOrDefault(22) : 22;
        
        long delayMs = -1;
        try {
            delayMs = Time.parseTimeString(""+waitForSshable);
        } catch (Exception e) {
            // normal if 'true'; just fall back to default
        }
        if (delayMs<0) 
            delayMs = Time.parseTimeString(WAIT_FOR_SSHABLE.getDefaultValue());
        
        String user = expectedCredentials.getUser();
        LOG.debug("VM {}: reported online, now waiting {} for it to be sshable on {}@{}:{}{}", new Object[] {
                setup.getDescription(), Time.makeTimeStringRounded(delayMs),
                user, vmIp, vmPort,
                Objects.equal(user, getUser(setup)) ? "" : " (setup user is different: "+getUser(setup)+")"});
        
        Callable<Boolean> checker;
        if (hostAndPortOverride.isPresent()) {
            final SshMachineLocation machine = createTemporarySshMachineLocation(hostAndPortOverride.get(), expectedCredentials, setup);
            checker = new Callable<Boolean>() {
                public Boolean call() {
                    int exitstatus = machine.execScript("check-connectivity", ImmutableList.of("hostname"));
                    return exitstatus == 0;
                }};
        } else {
            checker = new Callable<Boolean>() {
                public Boolean call() {
                    Statement statement = Statements.newStatementList(exec("hostname"));
                    ExecResponse response = computeService.runScriptOnNode(node.getId(), statement, 
                            overrideLoginCredentials(expectedCredentials).runAsRoot(false));
                    return response.getExitStatus() == 0;
                }};
        }
        
        Stopwatch stopwatch = Stopwatch.createStarted();
        
        ReferenceWithError<Boolean> reachable = new Repeater()
            .every(1,SECONDS)
            .until(checker)
            .limitTimeTo(delayMs, MILLISECONDS)
            .runKeepingError();

        if (!reachable.getWithoutError()) {
            throw new IllegalStateException("SSH failed for "+
                    user+"@"+vmIp+" ("+setup.getDescription()+") after waiting "+
                    Time.makeTimeStringRounded(delayMs), reachable.getError());
        }
        
        LOG.debug("VM {}: is sshable after {} on {}@{}",new Object[] {
                setup.getDescription(), Time.makeTimeStringRounded(stopwatch),
                user, vmIp});
    }

    // -------------------- hostnames ------------------------
    // hostnames are complicated, but irregardless, this code could be cleaned up!

    protected void setHostnameUpdatingCredentials(ConfigBag setup, NodeMetadata metadata) {
        List<String> usersTried = new ArrayList<String>();
        
        String originalUser = getUser(setup);
        if (groovyTruth(originalUser)) {
            if (setHostname(setup, metadata, false)) return;
            usersTried.add(originalUser);
        }
        
        LoginCredentials credentials = metadata.getCredentials();
        if (groovyTruth(credentials)) {
            if (groovyTruth(credentials.getUser())) setup.put(USER, credentials.getUser());
            if (groovyTruth(credentials.getPrivateKey())) setup.put(PRIVATE_KEY_DATA, credentials.getPrivateKey());
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
            setup.put(SshTool.PROP_HOST, getPublicHostname(metadata, Optional.<HostAndPort>absent(), setup));
            return true;
        } catch (Exception e) {
            if (rethrow) {
                LOG.warn("couldn't connect to "+metadata+" when trying to discover hostname (rethrowing): "+e);
                throw Exceptions.propagate(e);                
            }
            return false;
        }
    }

    /**
     * Attempts to obtain the hostname or IP of the node, as advertised by the cloud provider.
     * Prefers public, reachable IPs. 
     * For some clouds (e.g. aws-ec2), it will attempt to find the public hostname.
     */
    protected String getPublicHostname(NodeMetadata node, Optional<HostAndPort> sshHostAndPort, ConfigBag setup) {
        String provider = (setup != null) ? setup.get(CLOUD_PROVIDER) : null;
        if (provider == null) provider= getProvider();
        
        if ("aws-ec2".equals(provider)) {
            HostAndPort inferredHostAndPort = null;
            if (!sshHostAndPort.isPresent()) {
                try {
                    String vmIp = JcloudsUtil.getFirstReachableAddress(this.getComputeService().getContext(), node);
                    int port = node.getLoginPort();
                    inferredHostAndPort = HostAndPort.fromParts(vmIp, port);
                } catch (Exception e) {
                    LOG.warn("Error reaching aws-ec2 instance "+node.getId()+"@"+node.getLocation()+" on port "+node.getLoginPort()+"; falling back to jclouds metadata for address", e);
                }
            }
            if (sshHostAndPort.isPresent() || inferredHostAndPort != null) {
                HostAndPort hostAndPortToUse = sshHostAndPort.isPresent() ? sshHostAndPort.get() : inferredHostAndPort;
                try {
                    return getPublicHostnameAws(hostAndPortToUse, setup);
                } catch (Exception e) {
                    LOG.warn("Error querying aws-ec2 instance instance "+node.getId()+"@"+node.getLocation()+" over ssh for its hostname; falling back to first reachable IP", e);
                    // We've already found a reachable address so settle for that, rather than doing it again
                    if (inferredHostAndPort != null) return inferredHostAndPort.getHostText();
                }
            }
        }
        
        return getPublicHostnameGeneric(node, setup);
    }
    
    private String getPublicHostnameGeneric(NodeMetadata node, @Nullable ConfigBag setup) {
        //prefer the public address to the hostname because hostname is sometimes wrong/abbreviated
        //(see that javadoc; also e.g. on rackspace, the hostname lacks the domain)
        if (groovyTruth(node.getPublicAddresses())) {
            return node.getPublicAddresses().iterator().next();
        } else if (groovyTruth(node.getHostname())) {
            return node.getHostname();
        } else if (groovyTruth(node.getPrivateAddresses())) {
            return node.getPrivateAddresses().iterator().next();
        } else {
            return null;
        }
    }
    
    private String getPublicHostnameAws(HostAndPort sshHostAndPort, ConfigBag setup) {
        SshMachineLocation sshLocByIp = null;
        try {
            ConfigBag sshConfig = extractSshConfig(setup, new ConfigBag());
            
            // TODO messy way to get an SSH session
            if (isManaged()) {
                sshLocByIp = getManagementContext().getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                        .configure("address", sshHostAndPort.getHostText()) 
                        .configure("port", sshHostAndPort.getPort()) 
                        .configure("user", getUser(setup))
                        .configure(sshConfig.getAllConfig()));
            } else {
                MutableMap<Object, Object> locationProps = MutableMap.builder()
                        .put("address", sshHostAndPort.getHostText())
                        .put("port", sshHostAndPort.getPort())
                        .put("user", getUser(setup))
                        .putAll(sshConfig.getAllConfig())
                        .build();
                sshLocByIp = new SshMachineLocation(locationProps);
            }
            
            ByteArrayOutputStream outStream = new ByteArrayOutputStream();
            ByteArrayOutputStream errStream = new ByteArrayOutputStream();
            int exitcode = sshLocByIp.execCommands(
                    MutableMap.of("out", outStream, "err", errStream),
                    "get public AWS hostname",
                    ImmutableList.of(
                            BashCommands.INSTALL_CURL,
                            "echo `curl --silent --retry 20 http://169.254.169.254/latest/meta-data/public-hostname`; exit"));
            String outString = new String(outStream.toByteArray());
            String[] outLines = outString.split("\n");
            for (String line : outLines) {
                if (line.startsWith("ec2-")) return line.trim();
            }
            throw new IllegalStateException("Could not obtain aws-ec2 hostname for vm "+sshHostAndPort+"; exitcode="+exitcode+"; stdout="+outString+"; stderr="+new String(errStream.toByteArray()));
        } finally {
            Streams.closeQuietly(sshLocByIp);
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
            throw new IllegalArgumentException("Invalid type for Map<String,String>: " + v +
                    (v != null ? " of type "+v.getClass() : ""));
        }
    }
    
    private List<String> createIptablesRulesForNetworkInterface(Iterable<Integer> ports) {
       List<String> iptablesRules = Lists.newArrayList();
       for (Integer port : ports) {
          iptablesRules.add(IptablesCommands.insertIptablesRule(Chain.INPUT, Protocol.TCP, port, Policy.ACCEPT));
       }
       return iptablesRules;
    }
}
