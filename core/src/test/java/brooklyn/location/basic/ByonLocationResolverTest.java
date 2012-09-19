package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Set;

import org.testng.annotations.Test;

import brooklyn.location.MachineLocation;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class ByonLocationResolverTest {

    @Test
    public void testThrowsOnInvalid() throws Exception {
        assertThrowsIllegalArgument("wrongprefix:(hosts=\"1.1.1.1\")");
        assertThrowsIllegalArgument("byon"); // no hosts
        assertThrowsIllegalArgument("byon:()"); // no hosts
        assertThrowsIllegalArgument("byon:(hosts=\"\")"); // empty hosts
        assertThrowsIllegalArgument("byon:(hosts=\"1.1.1.1\""); // no closing bracket
        assertThrowsIllegalArgument("byon:(hosts=\"1.1.1.1\", name)"); // no value for name
        assertThrowsIllegalArgument("byon:(hosts=\"1.1.1.1\", name=)"); // no value for name
    }
    
    @Test
    public void testResolvesHosts() throws Exception {
        assertByonClusterEquals(resolve("byon:(hosts=\"1.1.1.1\")"), ImmutableSet.of("1.1.1.1"), null);
        assertByonClusterEquals(resolve("byon:(hosts=\"1.1.1.1\")"), ImmutableSet.of("1.1.1.1"), null);
        assertByonClusterEquals(resolve("byon:(hosts=\"1.1.1.1,1.1.1.2\")"), ImmutableSet.of("1.1.1.1","1.1.1.2"), null);
        assertByonClusterEquals(resolve("byon:(hosts=\"1.1.1.1\", name=myname)"), ImmutableSet.of("1.1.1.1"), "myname");
        assertByonClusterEquals(resolve("byon:(hosts=\"1.1.1.1\", name=\"myname\")"), ImmutableSet.of("1.1.1.1"), "myname");
    }
        
    @Test
    public void testResolvesUsernameAtHost() throws Exception {
        assertByonClusterWithUsersEquals(resolve("byon:(hosts=\"myuser@1.1.1.1\")"), ImmutableSet.of(new UserHostTuple("myuser", "1.1.1.1")), null);
        assertByonClusterWithUsersEquals(resolve("byon:(hosts=\"myuser@1.1.1.1,myuser2@1.1.1.1\")"), ImmutableSet.of(new UserHostTuple("myuser", "1.1.1.1"), new UserHostTuple("myuser2", "1.1.1.1")), null);
        assertByonClusterWithUsersEquals(resolve("byon:(hosts=\"myuser@1.1.1.1,myuser2@1.1.1.2\")"), ImmutableSet.of(new UserHostTuple("myuser", "1.1.1.1"), new UserHostTuple("myuser2", "1.1.1.2")), null);
    }
    
    private void assertByonClusterEquals(FixedListMachineProvisioningLocation<? extends MachineLocation> cluster, Set<String> expectedHosts, String expectedName) {
        Set<String> actualHosts = ImmutableSet.copyOf(Iterables.transform(cluster.getMachines(), new Function<MachineLocation, String>() {
            @Override public String apply(MachineLocation input) {
                return input.getAddress().getHostName();
            }}));
        assertEquals(actualHosts, expectedHosts);
        assertEquals(cluster.getName(), expectedName);
    }

    private void assertByonClusterWithUsersEquals(FixedListMachineProvisioningLocation<? extends MachineLocation> cluster, Set<UserHostTuple> expectedHosts, String expectedName) {
        Set<UserHostTuple> actualHosts = ImmutableSet.copyOf(Iterables.transform(cluster.getMachines(), new Function<MachineLocation, UserHostTuple>() {
            @Override public UserHostTuple apply(MachineLocation input) {
                return new UserHostTuple(((SshMachineLocation)input).getUser(), input.getAddress().getHostName());
            }}));
        assertEquals(actualHosts, expectedHosts);
        assertEquals(cluster.getName(), expectedName);
    }

    private void assertThrowsIllegalArgument(String val) {
        try {
            resolve(val);
            fail();
        } catch (IllegalArgumentException e) {
            // success
        }
    }
    
    private FixedListMachineProvisioningLocation<SshMachineLocation> resolve(String val) {
        return new ByonLocationResolver().newLocationFromString(val);
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
