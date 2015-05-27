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
package brooklyn.location.jclouds.networking;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.SecurityGroup;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.extensions.SecurityGroupExtension;
import org.jclouds.domain.Location;
import org.jclouds.net.domain.IpPermission;
import org.jclouds.net.domain.IpProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.location.geo.LocalhostExternalIpLoader;
import brooklyn.location.jclouds.BasicJcloudsLocationCustomizer;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsMachineLocation;
import brooklyn.util.net.Cidr;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * Configures custom security groups on Jclouds locations.
 * 
 * TODO this should allow {@link SecurityGroupDefinition} instances to be applied
 */
@Beta
public final class JcloudsLocationSecurityGroupCustomizer extends BasicJcloudsLocationCustomizer {

    private static final Logger LOG = LoggerFactory.getLogger(JcloudsLocationSecurityGroupCustomizer.class);

    // Caches instances of JcloudsLocationSecurityGroupCustomizer by application IDs.
    private static final LoadingCache<String, JcloudsLocationSecurityGroupCustomizer> CUSTOMISERS = CacheBuilder.newBuilder()
            .build(new CacheLoader<String, JcloudsLocationSecurityGroupCustomizer>() {
                @Override
                public JcloudsLocationSecurityGroupCustomizer load(final String appContext) throws Exception {
                    return new JcloudsLocationSecurityGroupCustomizer(appContext);
                }
            });

    /** Caches the base security group that should be shared between all instances in the same Jclouds location */
    private final Cache<Location, SecurityGroup> sharedGroupCache = CacheBuilder.newBuilder().build();
    /** Caches security groups unique to instances */
    private final Cache<String, SecurityGroup> uniqueGroupCache = CacheBuilder.newBuilder().build();

    private final String applicationId;
    private final Supplier<Cidr> brooklynCidrSupplier;

    JcloudsLocationSecurityGroupCustomizer(String applicationId) {
        this(applicationId, new LocalhostExternalIpCidrSupplier());
    }

    JcloudsLocationSecurityGroupCustomizer(String applicationId, Supplier<Cidr> cidrSupplier) {
        this.applicationId = applicationId;
        this.brooklynCidrSupplier = cidrSupplier;
    }

    /**
     * Gets the customizer for the given applicationId. Multiple calls to this method with the
     * same application context will return the same JcloudsLocationSecurityGroupCustomizer instance.
     * @param applicationId An identifier for the application the customizer is to be used for
     * @return the unique customizer for the given context
     */
    public static JcloudsLocationSecurityGroupCustomizer getInstance(String applicationId) {
        return CUSTOMISERS.getUnchecked(applicationId);
    }

    /**
     * Gets a customizer for the given entity's application. Multiple calls to this method with entities
     * in the same application will return the same JcloudsLocationSecurityGroupCustomizer instance.
     * @param entity The entity the customizer is to be used for
     * @return the unique customizer for the entity's owning application
     */
    public static JcloudsLocationSecurityGroupCustomizer getInstance(Entity entity) {
        return getInstance(entity.getApplicationId());
    }

    /** @see #addPermissionsToLocation(brooklyn.location.jclouds.JcloudsMachineLocation, java.util.Collection)  */
    public void addPermissionsToLocation(final JcloudsMachineLocation location, IpPermission... permissions) {
        addPermissionsToLocation(location, ImmutableList.copyOf(permissions));
    }

    /**
     * Applies the given security group permissions to the given location.
     * <p/>
     * Takes no action if the location's compute service does not have a security group extension.
     * @param permissions The set of permissions to be applied to the location
     * @param location Location to gain permissions
     */
    public void addPermissionsToLocation(final JcloudsMachineLocation location, final Collection<IpPermission> permissions) {
        ComputeService computeService = location.getParent().getComputeService();
        String nodeId = location.getNode().getId();
        addPermissionsToLocation(permissions, nodeId, computeService);
    }

    /**
     * Applies the given security group permissions to the given node with the given compute service.
     * <p/>
     * Takes no action if the compute service does not have a security group extension.
     * @param permissions The set of permissions to be applied to the node
     * @param nodeId The id of the node to update
     * @param computeService The compute service to use to apply the changes
     */
    @VisibleForTesting
    void addPermissionsToLocation(Collection<IpPermission> permissions, final String nodeId, ComputeService computeService) {
        if (!computeService.getSecurityGroupExtension().isPresent()) {
            LOG.warn("Security group extension for {} absent; cannot update node {} with {}",
                    new Object[] {computeService, nodeId, permissions});
            return;
        }
        final SecurityGroupExtension securityApi = computeService.getSecurityGroupExtension().get();

        // Expect to have two security groups on the node: one shared between all nodes in the location,
        // that is cached in sharedGroupCache, and one created by Jclouds that is unique to the node.
        // Relies on customize having been called before. This should be safe because the arguments
        // needed to call this method are not available until post-instance creation.
        SecurityGroup machineUniqueSecurityGroup;
        Tasks.setBlockingDetails("Loading unique security group for node: " + nodeId);
        try {
            machineUniqueSecurityGroup = uniqueGroupCache.get(nodeId, new Callable<SecurityGroup>() {
                @Override public SecurityGroup call() throws Exception {
                    SecurityGroup sg = getUniqueSecurityGroupForNodeCachingSharedGroupIfPreviouslyUnknown(nodeId, securityApi);
                    if (sg == null) {
                        throw new IllegalStateException("Failed to find machine-unique group on node: " + nodeId);
                    }
                    return sg;
                }
            });
        } catch (ExecutionException e) {
            throw Throwables.propagate(new Exception(e.getCause()));
        } finally {
            Tasks.resetBlockingDetails();
        }
        for (IpPermission permission : permissions) {
            addPermission(permission, machineUniqueSecurityGroup, securityApi);
        }
    }

    /**
     * Loads the security groups attached to the node with the given ID and returns the group
     * that is unique to the node, per the application context. This method will also update
     * {@link #sharedGroupCache} if no mapping for the shared group's location previously
     * existed (e.g. Brooklyn was restarted and rebound to an existing application).
     *
     * @param nodeId The id of the node in question
     * @param securityApi The API to use to list security groups
     * @return the security group unique to the given node, or null if one could not be determined.
     */
    private SecurityGroup getUniqueSecurityGroupForNodeCachingSharedGroupIfPreviouslyUnknown(
            String nodeId, SecurityGroupExtension securityApi) {
        Set<SecurityGroup> groupsOnNode = securityApi.listSecurityGroupsForNode(nodeId);
        if (groupsOnNode.size() != 2) {
            LOG.warn("Expected to find two security groups on node {} in app {} (one shared, one unique). Found {}: {}",
                    new Object[]{nodeId, applicationId, groupsOnNode.size(), groupsOnNode});
            return null;
        }
        String expectedSharedName = getNameForSharedSecurityGroup();
        Iterator<SecurityGroup> it = groupsOnNode.iterator();
        SecurityGroup shared = it.next();
        SecurityGroup unique;
        if (shared.getName().endsWith(expectedSharedName)) {
            unique = it.next();
        } else {
            unique = shared;
            shared = it.next();
        }
        if (!shared.getName().endsWith(expectedSharedName)) {
            LOG.warn("Couldn't determine which security group is shared between instances in app {}. Expected={}, found={}",
                    new Object[]{applicationId, expectedSharedName, groupsOnNode});
            return null;
        }
        // Shared entry might be missing if Brooklyn has rebound to an application
        SecurityGroup old = sharedGroupCache.asMap().putIfAbsent(shared.getLocation(), shared);
        LOG.info("Loaded unique security group for node {} (in {}): {}",
                new Object[]{nodeId, applicationId, unique});
        if (old == null) {
            LOG.info("Proactively set shared group for app {} to: {}", applicationId, shared);
        }
        return unique;
    }

    /**
     * Replaces security groups configured on the given template with one that allows
     * SSH access on port 22 and allows communication on all ports between machines in
     * the same group. Security groups are reused when templates have equal
     * {@link org.jclouds.compute.domain.Template#getLocation locations}.
     * <p/>
     * This method is called by Brooklyn when obtaining machines, as part of the
     * {@link brooklyn.location.jclouds.JcloudsLocationCustomizer} contract. It
     * should not be called from anywhere else.
     *
     * @param location The Brooklyn location that has called this method while obtaining a machine
     * @param computeService The compute service being used by the location argument to provision a machine
     * @param template The machine template created by the location argument
     */
    @Override
    public void customize(JcloudsLocation location, ComputeService computeService, Template template) {
        if (!computeService.getSecurityGroupExtension().isPresent()) {
            LOG.warn("Security group extension for {} absent; cannot configure security groups in context: {}", computeService, applicationId);
        } else if (template.getLocation() == null) {
            LOG.warn("No location has been set on {}; cannot configure security groups in context: {}", template, applicationId);
        } else {
            LOG.info("Configuring security groups on location {} in context {}", location, applicationId);
            setSecurityGroupOnTemplate(location, template, computeService.getSecurityGroupExtension().get());
        }
    }

    private void setSecurityGroupOnTemplate(final JcloudsLocation location, final Template template, final SecurityGroupExtension securityApi) {
        SecurityGroup shared;
        Tasks.setBlockingDetails("Loading security group shared by instances in " + template.getLocation() +
                " in app " + applicationId);
        try {
            shared = sharedGroupCache.get(template.getLocation(), new Callable<SecurityGroup>() {
                @Override public SecurityGroup call() throws Exception {
                    return getOrCreateSharedSecurityGroup(template.getLocation(), securityApi);
                }
            });
        } catch (ExecutionException e) {
            throw Throwables.propagate(new Exception(e.getCause()));
        } finally {
            Tasks.resetBlockingDetails();
        }

        Set<String> originalGroups = template.getOptions().getGroups();
        template.getOptions().securityGroups(shared.getName());
        if (!originalGroups.isEmpty()) {
            LOG.info("Replaced configured security groups: configured={}, replaced with={}", originalGroups, template.getOptions().getGroups());
        } else {
            LOG.debug("Configured security groups at {} to: {}", location, template.getOptions().getGroups());
        }
    }

    /**
     * Loads the security group to be shared between nodes in the same application in the
     * given Location. If no such security group exists it is created.
     *
     * @param location The location in which the security group will be found
     * @param securityApi The API to use to list and create security groups
     * @return the security group to share between instances in the given location in this application
     */
    private SecurityGroup getOrCreateSharedSecurityGroup(Location location, SecurityGroupExtension securityApi) {
        final String groupName = getNameForSharedSecurityGroup();
        // Could sort-and-search if straight search is too expensive
        Optional<SecurityGroup> shared = Iterables.tryFind(securityApi.listSecurityGroupsInLocation(location), new Predicate<SecurityGroup>() {
            @Override public boolean apply(final SecurityGroup input) {
                // endsWith because Jclouds prepends 'jclouds#' to security group names.
                return input.getName().endsWith(groupName);
            }
        });
        if (shared.isPresent()) {
            LOG.info("Found existing shared security group in {} for app {}: {}",
                    new Object[]{location, applicationId, groupName});
            return shared.get();
        } else {
            LOG.info("Creating new shared security group in {} for app {}: {}",
                    new Object[]{location, applicationId, groupName});
            return createBaseSecurityGroupInLocation(groupName, location, securityApi);
        }
    }

    /**
     * Creates a security group with rules to:
     * <ul>
     *     <li>Allow SSH access on port 22 from the world</li>
     *     <li>Allow all communication between machines in the same group</li>
     * </ul>
     * @param groupName The name of the security group to create
     * @param location The location in which the security group will be created
     * @param securityApi The API to use to create the security group
     *
     * @return the created security group
     */
    private SecurityGroup createBaseSecurityGroupInLocation(String groupName, Location location, SecurityGroupExtension securityApi) {
        SecurityGroup group = addSecurityGroupInLocation(groupName, location, securityApi);

        // Note: For groupName to work with GCE we also need to tag the machines with the same ID.
        // See sourceTags section at https://developers.google.com/compute/docs/networking#firewalls
        IpPermission.Builder allWithinGroup = IpPermission.builder()
                .groupId(group.getProviderId())
                .fromPort(0)
                .toPort(65535);
        addPermission(allWithinGroup.ipProtocol(IpProtocol.TCP).build(), group, securityApi);
        addPermission(allWithinGroup.ipProtocol(IpProtocol.UDP).build(), group, securityApi);

        IpPermission sshPermission = IpPermission.builder()
                .fromPort(22)
                .toPort(22)
                .ipProtocol(IpProtocol.TCP)
                .cidrBlock(brooklynCidrSupplier.get().toString())
                .build();
        addPermission(sshPermission, group, securityApi);

        return group;
    }

    private SecurityGroup addSecurityGroupInLocation(String groupName, Location location, SecurityGroupExtension securityApi) {
        LOG.debug("Creating security group {} in {}", groupName, location);
        return securityApi.createSecurityGroup(groupName, location);
    }

    private SecurityGroup addPermission(IpPermission permission, SecurityGroup group, SecurityGroupExtension securityApi) {
        LOG.debug("Adding permission to security group {}: {}", group.getName(), permission);
        return securityApi.addIpPermission(permission, group);
    }

    /** @return the CIDR block used to configure Brooklyn's in security groups */
    public String getBrooklynCidrBlock() {
        return brooklynCidrSupplier.get().toString();
    }

    /**
     * @return The name to be used by security groups that will be shared between machines
     *         in the same location for this instance's application context.
     */
    @VisibleForTesting
    String getNameForSharedSecurityGroup() {
        return "brooklyn-" + applicationId.toLowerCase() + "-shared";
    }

    /**
     * Invalidates all entries in {@link #sharedGroupCache} and {@link #uniqueGroupCache}.
     * Use to simulate the effects of rebinding Brooklyn to a deployment.
     */
    @VisibleForTesting
    void clearSecurityGroupCaches() {
        LOG.info("Clearing security group caches");
        sharedGroupCache.invalidateAll();
        uniqueGroupCache.invalidateAll();
    }

    /**
     * A supplier of CIDRs that loads the external IP address of the localhost machine.
     */
    private static class LocalhostExternalIpCidrSupplier implements Supplier<Cidr> {

        private volatile Cidr cidr;

        @Override
        public Cidr get() {
            Cidr local = cidr;
            if (local == null) {
                synchronized (this) {
                    local = cidr;
                    if (local == null) {
                        String externalIp = LocalhostExternalIpLoader.getLocalhostIpWithin(Duration.seconds(5));
                        cidr = local = new Cidr(externalIp + "/32");
                    }
                }
            }
            return local;
        }

    }

}
