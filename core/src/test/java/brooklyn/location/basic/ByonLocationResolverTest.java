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
package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.os.Os;
import brooklyn.util.text.StringPredicates;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class ByonLocationResolverTest {

    private static final Logger log = LoggerFactory.getLogger(ByonLocationResolverTest.class);
    
    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;
    private Predicate<CharSequence> defaultNamePredicate;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext(BrooklynProperties.Factory.newEmpty());
        brooklynProperties = managementContext.getBrooklynProperties();
        defaultNamePredicate = StringPredicates.startsWith(FixedListMachineProvisioningLocation.class.getSimpleName());
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testTakesByonScopedProperties() {
        brooklynProperties.put("brooklyn.location.byon.privateKeyFile", "myprivatekeyfile");
        brooklynProperties.put("brooklyn.location.byon.publicKeyFile", "mypublickeyfile");
        brooklynProperties.put("brooklyn.location.byon.privateKeyData", "myprivateKeyData");
        brooklynProperties.put("brooklyn.location.byon.publicKeyData", "myPublicKeyData");
        brooklynProperties.put("brooklyn.location.byon.privateKeyPassphrase", "myprivateKeyPassphrase");

        Map<String, Object> conf = resolve("byon:(hosts=\"1.1.1.1\")").getAllConfig(true);
        
        assertEquals(conf.get("privateKeyFile"), "myprivatekeyfile");
        assertEquals(conf.get("publicKeyFile"), "mypublickeyfile");
        assertEquals(conf.get("privateKeyData"), "myprivateKeyData");
        assertEquals(conf.get("publicKeyData"), "myPublicKeyData");
        assertEquals(conf.get("privateKeyPassphrase"), "myprivateKeyPassphrase");
    }

    @Test
    public void testNamedByonLocation() throws Exception {
        brooklynProperties.put("brooklyn.location.named.mynamed", "byon:(hosts=\"1.1.1.1\")");
        
        FixedListMachineProvisioningLocation<SshMachineLocation> loc = resolve("named:mynamed");
        assertEquals(loc.obtain().getAddress(), InetAddress.getByName("1.1.1.1"));
    }

    @Test
    public void testPropertyScopePrecedence() throws Exception {
        brooklynProperties.put("brooklyn.location.named.mynamed", "byon:(hosts=\"1.1.1.1\")");
        
        // prefer those in "named" over everything else
        brooklynProperties.put("brooklyn.location.named.mynamed.privateKeyFile", "privateKeyFile-inNamed");
        brooklynProperties.put("brooklyn.location.byon.privateKeyFile", "privateKeyFile-inProviderSpecific");
        brooklynProperties.put("brooklyn.localhost.privateKeyFile", "privateKeyFile-inGeneric");

        // prefer those in provider-specific over generic
        brooklynProperties.put("brooklyn.location.byon.publicKeyFile", "publicKeyFile-inProviderSpecific");
        brooklynProperties.put("brooklyn.location.publicKeyFile", "publicKeyFile-inGeneric");

        // prefer location-generic if nothing else
        brooklynProperties.put("brooklyn.location.privateKeyData", "privateKeyData-inGeneric");

        Map<String, Object> conf = resolve("named:mynamed").getAllConfig(true);
        
        assertEquals(conf.get("privateKeyFile"), "privateKeyFile-inNamed");
        assertEquals(conf.get("publicKeyFile"), "publicKeyFile-inProviderSpecific");
        assertEquals(conf.get("privateKeyData"), "privateKeyData-inGeneric");
    }

    @Test
    public void testThrowsOnInvalid() throws Exception {
        assertThrowsNoSuchElement("wrongprefix:(hosts=\"1.1.1.1\")");
        assertThrowsIllegalArgument("byon"); // no hosts
        assertThrowsIllegalArgument("byon:()"); // no hosts
        assertThrowsIllegalArgument("byon:(hosts=\"\")"); // empty hosts
        assertThrowsIllegalArgument("byon:(hosts=\"1.1.1.1\""); // no closing bracket
        assertThrowsIllegalArgument("byon:(hosts=\"1.1.1.1\", name)"); // no value for name
        assertThrowsIllegalArgument("byon:(hosts=\"1.1.1.1\", name=)"); // no value for name
    }
    
    @Test
    public void testResolvesHosts() throws Exception {
        assertByonClusterEquals(resolve("byon:(hosts=\"1.1.1.1\")"), ImmutableSet.of("1.1.1.1"));
        assertByonClusterEquals(resolve("byon:(hosts=\"1.1.1.1\")"), ImmutableSet.of("1.1.1.1"));
        assertByonClusterEquals(resolve("byon:(hosts=\"1.1.1.1,1.1.1.2\")"), ImmutableSet.of("1.1.1.1","1.1.1.2"));
        assertByonClusterEquals(resolve("byon:(hosts=\"1.1.1.1, 1.1.1.2\")"), ImmutableSet.of("1.1.1.1","1.1.1.2"));
    }

    @Test
    public void testUsesDisplayName() throws Exception {
        assertByonClusterEquals(resolve("byon:(hosts=\"1.1.1.1\", name=myname)"), ImmutableSet.of("1.1.1.1"), "myname");
        assertByonClusterEquals(resolve("byon:(hosts=\"1.1.1.1\", name=\"myname\")"), ImmutableSet.of("1.1.1.1"), "myname");
    }

    @Test
    public void testResolvesHostsGlobExpansion() throws Exception {
        assertByonClusterEquals(resolve("byon:(hosts=\"1.1.1.{1,2}\")"), ImmutableSet.of("1.1.1.1","1.1.1.2"));
        assertByonClusterEquals(resolve("byon:(hosts=\"1.1.{1.1,2.{1,2}}\")"), 
                ImmutableSet.of("1.1.1.1","1.1.2.1","1.1.2.2"));
        assertByonClusterEquals(resolve("byon:(hosts=\"1.1.{1,2}.{1,2}\")"), 
                ImmutableSet.of("1.1.1.1","1.1.1.2","1.1.2.1","1.1.2.2"));
    }

    @Test(groups="Integration")
    public void testNiceError() throws Exception {
        Asserts.assertFailsWith(new Runnable() {
            @Override public void run() {
                FixedListMachineProvisioningLocation<SshMachineLocation> x = 
                        resolve("byon:(hosts=\"1.1.1.{1,2}}\")");
                log.error("got "+x+" but should have failed (your DNS is giving an IP for hostname '1.1.1.1}' (with the extra '}')");
            }
        }, new Predicate<Throwable>() {
            @Override
            public boolean apply(@Nullable Throwable input) {
                String s = input.toString();
                // words
                if (!s.contains("Invalid host")) return false;
                // problematic entry
                if (!s.contains("1.1.1.1}")) return false;
                // original spec
                if (!s.contains("1.1.1.{1,2}}")) return false;
                return true;
            }
        });
    }

    @Test
    public void testResolvesUsernameAtHost() throws Exception {
        assertByonClusterWithUsersEquals(resolve("byon:(hosts=\"myuser@1.1.1.1\")"), ImmutableSet.of(new UserHostTuple("myuser", "1.1.1.1")));
        assertByonClusterWithUsersEquals(resolve("byon:(hosts=\"myuser@1.1.1.1,myuser2@1.1.1.1\")"), ImmutableSet.of(new UserHostTuple("myuser", "1.1.1.1"), new UserHostTuple("myuser2", "1.1.1.1")));
        assertByonClusterWithUsersEquals(resolve("byon:(hosts=\"myuser@1.1.1.1,myuser2@1.1.1.2\")"), ImmutableSet.of(new UserHostTuple("myuser", "1.1.1.1"), new UserHostTuple("myuser2", "1.1.1.2")));
    }

    @Test
    public void testResolvesUserArg() throws Exception {
        assertByonClusterWithUsersEquals(resolve("byon:(hosts=\"1.1.1.1\",user=bob)"), ImmutableSet.of(new UserHostTuple("bob", "1.1.1.1")));
        assertByonClusterWithUsersEquals(resolve("byon:(user=\"bob\",hosts=\"myuser@1.1.1.1,1.1.1.1\")"), 
                ImmutableSet.of(new UserHostTuple("myuser", "1.1.1.1"), new UserHostTuple("bob", "1.1.1.1")));
    }

    @Test
    public void testResolvesUserArg2() throws Exception {
        String spec = "byon:(hosts=\"1.1.1.1\",user=bob)";
        FixedListMachineProvisioningLocation<SshMachineLocation> ll = resolve(spec);
        SshMachineLocation l = ll.obtain();
        Assert.assertEquals("bob", l.getUser());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testResolvesUserArg3() throws Exception {
        String spec = "byon:(hosts=\"1.1.1.1\")";
        managementContext.getLocationRegistry().getProperties().putAll(MutableMap.of(
                "brooklyn.location.named.foo", spec,
                "brooklyn.location.named.foo.user", "bob"));
        ((BasicLocationRegistry)managementContext.getLocationRegistry()).updateDefinedLocations();
        
        MachineProvisioningLocation<SshMachineLocation> ll = (MachineProvisioningLocation<SshMachineLocation>) 
                new NamedLocationResolver().newLocationFromString(MutableMap.of(), "named:foo", managementContext.getLocationRegistry());
        SshMachineLocation l = ll.obtain(MutableMap.of());
        Assert.assertEquals("bob", l.getUser());
    }

    @SuppressWarnings("unchecked")
    @Test
    /** private key should be inherited, so confirm that happens correctly */
    public void testResolvesPrivateKeyArgInheritance() throws Exception {
        String spec = "byon:(hosts=\"1.1.1.1\")";
        managementContext.getLocationRegistry().getProperties().putAll(MutableMap.of(
                "brooklyn.location.named.foo", spec,
                "brooklyn.location.named.foo.user", "bob",
                "brooklyn.location.named.foo.privateKeyFile", "/tmp/x"));
        ((BasicLocationRegistry)managementContext.getLocationRegistry()).updateDefinedLocations();
        
        MachineProvisioningLocation<SshMachineLocation> ll = (MachineProvisioningLocation<SshMachineLocation>) 
                new NamedLocationResolver().newLocationFromString(MutableMap.of(), "named:foo", managementContext.getLocationRegistry());
        
        Assert.assertEquals("/tmp/x", ll.getConfig(LocationConfigKeys.PRIVATE_KEY_FILE));
        Assert.assertTrue(ll.hasConfig(LocationConfigKeys.PRIVATE_KEY_FILE, false));
        Assert.assertEquals("/tmp/x", ll.getAllConfig(false).get(LocationConfigKeys.PRIVATE_KEY_FILE.getName()));
        Assert.assertEquals("/tmp/x", ((AbstractLocation)ll).getAllConfigBag().get(LocationConfigKeys.PRIVATE_KEY_FILE));

        SshMachineLocation l = ll.obtain(MutableMap.of());
        
        Assert.assertEquals("/tmp/x", l.getConfig(LocationConfigKeys.PRIVATE_KEY_FILE));
        
        Assert.assertTrue(l.hasConfig(LocationConfigKeys.PRIVATE_KEY_FILE, true));
        Assert.assertFalse(l.hasConfig(LocationConfigKeys.PRIVATE_KEY_FILE, false));

        Assert.assertEquals("/tmp/x", l.getAllConfig(true).get(LocationConfigKeys.PRIVATE_KEY_FILE.getName()));
        Assert.assertEquals("/tmp/x", l.getAllConfig(true).get(LocationConfigKeys.PRIVATE_KEY_FILE.getName()));

        Assert.assertEquals("/tmp/x", l.getAllConfigBag().get(LocationConfigKeys.PRIVATE_KEY_FILE));
    }

    @Test
    public void testResolvesLocalTempDir() throws Exception {
        String localTempDir = Os.mergePaths(Os.tmp(), "testResolvesUsernameAtHost");
        brooklynProperties.put("brooklyn.location.byon.localTempDir", localTempDir);

        FixedListMachineProvisioningLocation<SshMachineLocation> byon = resolve("byon:(hosts=\"1.1.1.1\")");
        SshMachineLocation machine = byon.obtain();
        assertEquals(machine.getConfig(SshMachineLocation.LOCAL_TEMP_DIR), localTempDir);
    }

    @Test
    public void testMachinesObtainedInOrder() throws Exception {
        List<String> ips = ImmutableList.of("1.1.1.1", "1.1.1.6", "1.1.1.3", "1.1.1.4", "1.1.1.5");
        String spec = "byon:(hosts=\""+Joiner.on(",").join(ips)+"\")";
        
        MachineProvisioningLocation<SshMachineLocation> ll = resolve(spec);

        for (String expected : ips) {
            SshMachineLocation obtained = ll.obtain(ImmutableMap.of());
            assertEquals(obtained.getAddress().getHostAddress(), expected);
        }
    }
    
    @Test
    public void testEmptySpec() throws Exception {
        String spec = "byon";
        Map<String, ?> flags = ImmutableMap.of(
                "hosts", ImmutableList.of("1.1.1.1", "2.2.2.22"),
                "name", "foo",
                "user", "myuser"
        );
        MachineProvisioningLocation<SshMachineLocation> provisioner = resolve(spec, flags);
        SshMachineLocation location1 = provisioner.obtain(ImmutableMap.of());
        Assert.assertEquals("myuser", location1.getUser());
        Assert.assertEquals("1.1.1.1", location1.getAddress().getHostAddress());
    }

    private void assertByonClusterEquals(FixedListMachineProvisioningLocation<? extends MachineLocation> cluster, Set<String> expectedHosts) {
        assertByonClusterEquals(cluster, expectedHosts, defaultNamePredicate);
    }
    
    private void assertByonClusterEquals(FixedListMachineProvisioningLocation<? extends MachineLocation> cluster, Set<String> expectedHosts, String expectedName) {
        assertByonClusterEquals(cluster, expectedHosts, Predicates.equalTo(expectedName));
    }
    
    private void assertByonClusterEquals(FixedListMachineProvisioningLocation<? extends MachineLocation> cluster, Set<String> expectedHosts, Predicate<? super String> expectedName) {
        Set<String> actualHosts = ImmutableSet.copyOf(Iterables.transform(cluster.getMachines(), new Function<MachineLocation, String>() {
            @Override public String apply(MachineLocation input) {
                return input.getAddress().getHostName();
            }}));
        assertEquals(actualHosts, expectedHosts);
        assertTrue(expectedName.apply(cluster.getDisplayName()), "name="+cluster.getDisplayName());
    }

    private void assertByonClusterWithUsersEquals(FixedListMachineProvisioningLocation<? extends MachineLocation> cluster, Set<UserHostTuple> expectedHosts) {
        assertByonClusterWithUsersEquals(cluster, expectedHosts, defaultNamePredicate);
    }
    
    private void assertByonClusterWithUsersEquals(FixedListMachineProvisioningLocation<? extends MachineLocation> cluster, Set<UserHostTuple> expectedHosts, Predicate<? super String> expectedName) {
        Set<UserHostTuple> actualHosts = ImmutableSet.copyOf(Iterables.transform(cluster.getMachines(), new Function<MachineLocation, UserHostTuple>() {
            @Override public UserHostTuple apply(MachineLocation input) {
                return new UserHostTuple(((SshMachineLocation)input).getUser(), input.getAddress().getHostName());
            }}));
        assertEquals(actualHosts, expectedHosts);
        assertTrue(expectedName.apply(cluster.getDisplayName()), "name="+cluster.getDisplayName());
    }

    private void assertThrowsNoSuchElement(String val) {
        try {
            resolve(val);
            fail();
        } catch (NoSuchElementException e) {
            // success
        }
    }
    
    private void assertThrowsIllegalArgument(String val) {
        try {
            resolve(val);
            fail();
        } catch (IllegalArgumentException e) {
            // success
        }
    }
    
    @SuppressWarnings("unchecked")
    private FixedListMachineProvisioningLocation<SshMachineLocation> resolve(String val) {
        return (FixedListMachineProvisioningLocation<SshMachineLocation>) managementContext.getLocationRegistry().resolve(val);
    }
    
    @SuppressWarnings("unchecked")
    private FixedListMachineProvisioningLocation<SshMachineLocation> resolve(String val, Map<?, ?> locationFlags) {
        return (FixedListMachineProvisioningLocation<SshMachineLocation>) managementContext.getLocationRegistry().resolve(val, locationFlags);
    }
    
    private static class UserHostTuple {
        final String username;
        final String hostname;
        
        UserHostTuple(String username, String hostname) {
            this.username = username;
            this.hostname = hostname;
        }
        
        @Override
        public boolean equals(Object o) {
            return o instanceof UserHostTuple && Objects.equal(username, ((UserHostTuple)o).username)
                    && Objects.equal(hostname, ((UserHostTuple)o).hostname);
        }
        
        @Override
        public int hashCode() {
            return Objects.hashCode(username, hostname);
        }
        @Override
        public String toString() {
            return Objects.toStringHelper(UserHostTuple.class).add("user", username).add("host", hostname).toString();
        }
    }
}
