package brooklyn.entity.database.rubyrep;

import static java.util.Arrays.asList;

import org.testng.annotations.Test;

import brooklyn.entity.database.DatabaseNode;
import brooklyn.entity.database.postgresql.PostgreSqlIntegrationTest;
import brooklyn.entity.database.postgresql.PostgreSqlNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;

import com.google.common.collect.ImmutableSet;

/**
 * The RubyRepRackspaceLiveTest installs RubyRep on various operating systems like Ubuntu, CentOS, Red Hat etc. To make sure that
 * RubyRep and PostgreSql works like expected on these Operating Systems.
 */
public class RubyRepRackspaceLiveTest extends RubyRepIntegrationTest {
    
    @Test(groups = "Live")
    public void test_Debian_6() throws Exception {
        test("Debian 6");
    }

    @Test(groups = "Live")
    public void test_Ubuntu_10_0() throws Exception {
        test("Ubuntu 10.0");
    }

    @Test(groups = "Live")
    public void test_Ubuntu_11_0() throws Exception {
        test("Ubuntu 11.0");
    }

    @Test(groups = "Live")
    public void test_Ubuntu_12_0() throws Exception {
        test("Ubuntu 12.0");
    }

    @Test(groups = "Live")
    public void test_CentOS_6_0() throws Exception {
        test("CentOS 6.0");
    }

    @Test(groups = "Live")
    public void test_CentOS_5_6() throws Exception {
        test("CentOS 5.6");
    }

    @Test(groups = "Live")
    public void test_Fedora_17() throws Exception {
        test("Fedora 17");
    }

    @Test(groups = "Live")
    public void test_Red_Hat_Enterprise_Linux_6() throws Exception {
        test("Red Hat Enterprise Linux 6");
    }

    public void test(String osRegex) throws Exception {
        PostgreSqlNode db1 = tapp.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .configure("creationScriptContents", PostgreSqlIntegrationTest.CREATION_SCRIPT)
                .configure("port", 9111));
        PostgreSqlNode db2 = tapp.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .configure("creationScriptContents", PostgreSqlIntegrationTest.CREATION_SCRIPT)
                .configure("port", 9111));

        brooklynProperties.put("brooklyn.jclouds.rackspace-cloudservers-uk.imageNameRegex", osRegex);
        brooklynProperties.remove("brooklyn.jclouds.rackspace-cloudservers-uk.image-id");
        brooklynProperties.remove("brooklyn.jclouds.rackspace-cloudservers-uk.imageId");
        brooklynProperties.put("brooklyn.jclouds.rackspace-cloudservers-uk.inboundPorts", new int[] {22, 9111});
        Location loc = managementContext.getLocationRegistry().resolve("jclouds:rackspace-cloudservers-uk");
        
        startInLocation(tapp, db1, db2, loc);

        //hack to get the port for mysql open; is the inbounds property not respected on rackspace??
        for (DatabaseNode node : ImmutableSet.of(db1, db2)) {
            SshMachineLocation l = (SshMachineLocation) node.getLocations().iterator().next();
            l.exec(asList("iptables -I INPUT -p tcp --dport 9111 -j ACCEPT"));
        }
        
        testReplication(db1, db2);
    }
    
    // disable inherited non-live tests
    @Test(enabled = false, groups = "Integration")
    public void test_localhost_mysql() throws Exception {
        super.test_localhost_mysql();
    }

    // disable inherited non-live tests
    @Test(enabled = false, groups = "Integration")
    public void test_localhost_postgres() throws Exception {
        super.test_localhost_postgres();
    }

    // disable inherited non-live tests
    @Test(enabled = false, groups = "Integration")
    public void test_localhost_postgres_mysql() throws Exception {
        super.test_localhost_postgres_mysql();
    }
}
