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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import java.net.URI;
import java.util.Collections;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.domain.SecurityGroup;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.extensions.SecurityGroupExtension;
import org.jclouds.compute.options.TemplateOptions;
import org.jclouds.domain.Location;
import org.jclouds.net.domain.IpPermission;
import org.jclouds.net.domain.IpProtocol;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Cidr;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class JcloudsLocationSecurityGroupCustomizerTest {

    JcloudsLocationSecurityGroupCustomizer customizer;
    ComputeService computeService;
    Location location;
    SecurityGroupExtension securityApi;

    /** Used to skip external checks in unit tests. */
    private static class TestCidrSupplier implements Supplier<Cidr> {
        @Override public Cidr get() {
            return new Cidr("192.168.10.10/32");
        }
    }

    @BeforeMethod
    public void setUp() {
        customizer = new JcloudsLocationSecurityGroupCustomizer("testapp", new TestCidrSupplier());
        location = mock(Location.class);
        securityApi = mock(SecurityGroupExtension.class);
        computeService = mock(ComputeService.class);
        when(computeService.getSecurityGroupExtension()).thenReturn(Optional.of(securityApi));
    }

    @Test
    public void testSameInstanceReturnedForSameApplication() {
        assertEquals(JcloudsLocationSecurityGroupCustomizer.getInstance("a"),
                JcloudsLocationSecurityGroupCustomizer.getInstance("a"));
        assertNotEquals(JcloudsLocationSecurityGroupCustomizer.getInstance("a"),
                JcloudsLocationSecurityGroupCustomizer.getInstance("b"));
    }

    @Test
    public void testSecurityGroupAddedWhenJcloudsLocationCustomised() {
        Template template = mock(Template.class);
        TemplateOptions templateOptions = mock(TemplateOptions.class);
        when(template.getLocation()).thenReturn(location);
        when(template.getOptions()).thenReturn(templateOptions);
        SecurityGroup group = newGroup("id");
        when(securityApi.createSecurityGroup(anyString(), eq(location))).thenReturn(group);

        // Two Brooklyn.JcloudsLocations added to same Jclouds.Location
        JcloudsLocation jcloudsLocationA = new JcloudsLocation(MutableMap.of("deferConstruction", true));
        JcloudsLocation jcloudsLocationB = new JcloudsLocation(MutableMap.of("deferConstruction", true));
        customizer.customize(jcloudsLocationA, computeService, template);
        customizer.customize(jcloudsLocationB, computeService, template);

        // One group with three permissions shared by both locations.
        // Expect TCP+UDP between members of group and SSH to Brooklyn
        verify(securityApi).createSecurityGroup(anyString(), eq(location));
        verify(securityApi, times(3)).addIpPermission(any(IpPermission.class), eq(group));
        // New groups set on options
        verify(templateOptions, times(2)).securityGroups(anyString());
    }

    @Test
    public void testSharedGroupLoadedWhenItExistsButIsNotCached() {
        Template template = mock(Template.class);
        TemplateOptions templateOptions = mock(TemplateOptions.class);
        when(template.getLocation()).thenReturn(location);
        when(template.getOptions()).thenReturn(templateOptions);
        JcloudsLocation jcloudsLocation = new JcloudsLocation(MutableMap.of("deferConstruction", true));
        SecurityGroup shared = newGroup(customizer.getNameForSharedSecurityGroup());
        SecurityGroup irrelevant = newGroup("irrelevant");
        when(securityApi.listSecurityGroupsInLocation(location)).thenReturn(ImmutableSet.of(irrelevant, shared));

        customizer.customize(jcloudsLocation, computeService, template);

        verify(securityApi).listSecurityGroupsInLocation(location);
        verify(securityApi, never()).createSecurityGroup(anyString(), any(Location.class));
    }

    @Test
    public void testAddPermissionsToNode() {
        IpPermission ssh = newPermission(22);
        IpPermission jmx = newPermission(31001);
        String nodeId = "node";
        SecurityGroup sharedGroup = newGroup(customizer.getNameForSharedSecurityGroup());
        SecurityGroup group = newGroup("id");
        when(securityApi.listSecurityGroupsForNode(nodeId)).thenReturn(ImmutableSet.of(sharedGroup, group));

        customizer.addPermissionsToLocation(ImmutableList.of(ssh, jmx), nodeId, computeService);

        verify(securityApi, never()).createSecurityGroup(anyString(), any(Location.class));
        verify(securityApi, times(1)).addIpPermission(ssh, group);
        verify(securityApi, times(1)).addIpPermission(jmx, group);
    }

    @Test
    public void testAddPermissionsToNodeUsesUncachedSecurityGroup() {
        JcloudsLocation jcloudsLocation = new JcloudsLocation(MutableMap.of("deferConstruction", true));
        IpPermission ssh = newPermission(22);
        String nodeId = "nodeId";
        SecurityGroup sharedGroup = newGroup(customizer.getNameForSharedSecurityGroup());
        SecurityGroup uniqueGroup = newGroup("unique");

        Template template = mock(Template.class);
        TemplateOptions templateOptions = mock(TemplateOptions.class);
        when(template.getLocation()).thenReturn(location);
        when(template.getOptions()).thenReturn(templateOptions);
        when(securityApi.createSecurityGroup(anyString(), eq(location))).thenReturn(sharedGroup);

        // Call customize to cache the shared group
        customizer.customize(jcloudsLocation, computeService, template);
        reset(securityApi);
        when(securityApi.listSecurityGroupsForNode(nodeId)).thenReturn(ImmutableSet.of(uniqueGroup, sharedGroup));
        customizer.addPermissionsToLocation(ImmutableSet.of(ssh), nodeId, computeService);

        // Expect the per-machine group to have been altered, not the shared group
        verify(securityApi).addIpPermission(ssh, uniqueGroup);
        verify(securityApi, never()).addIpPermission(any(IpPermission.class), eq(sharedGroup));
    }

    @Test
    public void testSecurityGroupsLoadedWhenAddingPermissionsToUncachedNode() {
        IpPermission ssh = newPermission(22);
        String nodeId = "nodeId";
        SecurityGroup sharedGroup = newGroup(customizer.getNameForSharedSecurityGroup());
        SecurityGroup uniqueGroup = newGroup("unique");

        when(securityApi.listSecurityGroupsForNode(nodeId)).thenReturn(ImmutableSet.of(sharedGroup, uniqueGroup));

        // Expect first call to list security groups on nodeId, second to use cached version
        customizer.addPermissionsToLocation(ImmutableSet.of(ssh), nodeId, computeService);
        customizer.addPermissionsToLocation(ImmutableSet.of(ssh), nodeId, computeService);

        verify(securityApi, times(1)).listSecurityGroupsForNode(nodeId);
        verify(securityApi, times(2)).addIpPermission(ssh, uniqueGroup);
        verify(securityApi, never()).addIpPermission(any(IpPermission.class), eq(sharedGroup));
    }

    private SecurityGroup newGroup(String id) {
        URI uri = null;
        String ownerId = null;
        return new SecurityGroup(
                "providerId",
                id,
                id,
                location,
                uri,
                Collections.<String, String>emptyMap(),
                ImmutableSet.<String>of(),
                ImmutableSet.<IpPermission>of(),
                ownerId);
    }

    private IpPermission newPermission(int port) {
        return IpPermission.builder()
                .ipProtocol(IpProtocol.TCP)
                .fromPort(port)
                .toPort(port)
                .cidrBlock("0.0.0.0/0")
                .build();
    }
}
