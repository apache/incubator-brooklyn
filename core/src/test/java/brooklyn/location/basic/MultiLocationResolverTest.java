package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.net.InetAddress;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

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
