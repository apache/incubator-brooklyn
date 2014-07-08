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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class MultiLocationResolverTest {

    private BrooklynProperties brooklynProperties;
    private LocalManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext(BrooklynProperties.Factory.newEmpty());
        brooklynProperties = managementContext.getBrooklynProperties();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testThrowsOnInvalid() throws Exception {
        assertThrowsNoSuchElement("wrongprefix:(hosts=\"1.1.1.1\")");
        assertThrowsIllegalArgument("single");
    }
    
    @Test
    public void testThrowsOnInvalidTarget() throws Exception {
        assertThrowsIllegalArgument("multi:()");
        assertThrowsIllegalArgument("multi:(wrongprefix:(hosts=\"1.1.1.1\"))");
        assertThrowsIllegalArgument("multi:(foo:bar)");
    }

    @Test
    public void testCleansUpOnInvalidTarget() {
        assertThrowsNoSuchElement("multi:(targets=\"localhost:(name=testCleansUpOnInvalidTarget),thisNamedLocationDoesNotExist\")");
        Optional<Location> subtarget = Iterables.tryFind(managementContext.getLocationManager().getLocations(), LocationPredicates.displayNameEqualTo("testCleansUpOnInvalidTarget"));
        assertFalse(subtarget.isPresent(), "subtarget="+subtarget);
    }


    @Test
    public void testResolvesSubLocs() {
        assertMultiLocation(resolve("multi:(targets=localhost)"), 1, ImmutableList.of(Predicates.instanceOf(LocalhostMachineProvisioningLocation.class)));
        assertMultiLocation(resolve("multi:(targets=\"localhost,localhost\")"), 2, Collections.nCopies(2, Predicates.instanceOf(LocalhostMachineProvisioningLocation.class)));
        assertMultiLocation(resolve("multi:(targets=\"localhost,localhost,localhost\")"), 3, Collections.nCopies(3, Predicates.instanceOf(LocalhostMachineProvisioningLocation.class)));
        assertMultiLocation(resolve("multi:(targets=\"localhost:(name=mysubname)\")"), 1, ImmutableList.of(displayNameEqualTo("mysubname")));
        assertMultiLocation(resolve("multi:(targets=byon:(hosts=\"1.1.1.1\"))"), 1, ImmutableList.of(Predicates.and(
                Predicates.instanceOf(FixedListMachineProvisioningLocation.class),
                new Predicate<MachineProvisioningLocation>() {
                    @Override public boolean apply(MachineProvisioningLocation input) {
                        SshMachineLocation machine;
                        try {
                            machine = (SshMachineLocation) input.obtain(ImmutableMap.of());
                        } catch (NoMachinesAvailableException e) {
                            throw Exceptions.propagate(e);
                        }
                        try {
                            String addr = ((SshMachineLocation)machine).getAddress().getHostAddress();
                            return addr != null && addr.equals("1.1.1.1");
                        } finally {
                            input.release(machine);
                        }
                    }
                })));
        assertMultiLocation(resolve("multi:(targets=\"byon:(hosts=1.1.1.1),byon:(hosts=1.1.1.2)\")"), 2, Collections.nCopies(2, Predicates.instanceOf(FixedListMachineProvisioningLocation.class)));
    }

    @Test
    public void testResolvesName() {
        MultiLocation<SshMachineLocation> multiLoc = resolve("multi:(name=myname,targets=localhost)");
        assertEquals(multiLoc.getDisplayName(), "myname");
    }
    
    @Test
    public void testNamedByonLocation() throws Exception {
        brooklynProperties.put("brooklyn.location.named.mynamed", "multi:(targets=byon:(hosts=\"1.1.1.1\"))");
        
        MultiLocation<SshMachineLocation> loc = resolve("named:mynamed");
        assertEquals(loc.obtain(ImmutableMap.of()).getAddress(), InetAddress.getByName("1.1.1.1"));
    }

    @Test
    public void testResolvesFromMap() throws NoMachinesAvailableException {
        Location l = managementContext.getLocationRegistry().resolve("multi", MutableMap.of("targets", 
            MutableList.of("localhost", MutableMap.of("byon", MutableMap.of("hosts", "127.0.0.127")))));
        MultiLocation<?> ml = (MultiLocation<?>)l;
        Iterator<MachineProvisioningLocation<?>> ci = ml.getSubLocations().iterator();
        
        l = ci.next();
        Assert.assertTrue(l instanceof LocalhostMachineProvisioningLocation, "Expected localhost, got "+l);
        
        l = ci.next();
        Assert.assertTrue(l instanceof FixedListMachineProvisioningLocation, "Expected fixed, got "+l);
        MachineLocation sl = ((FixedListMachineProvisioningLocation<?>)l).obtain();
        Assert.assertEquals(sl.getAddress().getHostAddress(), "127.0.0.127");
        
        Assert.assertFalse(ci.hasNext());
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
    private MultiLocation<SshMachineLocation> resolve(String val) {
        return (MultiLocation<SshMachineLocation>) managementContext.getLocationRegistry().resolve(val);
    }
    
    @SuppressWarnings("rawtypes")
    private void assertMultiLocation(MultiLocation<?> multiLoc, int expectedSize, List<? extends Predicate> expectedSubLocationPredicates) {
        AvailabilityZoneExtension zones = multiLoc.getExtension(AvailabilityZoneExtension.class);
        List<Location> subLocs = zones.getAllSubLocations();
        assertEquals(subLocs.size(), expectedSize, "zones="+subLocs);
        for (int i = 0; i < subLocs.size(); i++) {
            MachineProvisioningLocation subLoc = (MachineProvisioningLocation) subLocs.get(i);
            assertTrue(expectedSubLocationPredicates.get(i).apply(subLoc), "index="+i+"; subLocs="+subLocs);
        }
    }
    
    public static <T> Predicate<Location> displayNameEqualTo(final T val) {
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return Objects.equal(input.getDisplayName(), val);
            }
        };
    }
}
