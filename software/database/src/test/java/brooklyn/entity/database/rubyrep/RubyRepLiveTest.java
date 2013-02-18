package brooklyn.entity.database.rubyrep;


import brooklyn.config.BrooklynProperties;
import brooklyn.entity.database.mysql.MySqlIntegrationTest;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.database.mysql.MySqlNodeImpl;
import brooklyn.entity.database.postgresql.PostgreSqlIntegrationTest;
import brooklyn.entity.database.postgresql.PostgreSqlNode;
import brooklyn.entity.database.postgresql.PostgreSqlNodeImpl;
import brooklyn.location.Location;
import brooklyn.location.basic.LocationRegistry;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.basic.jclouds.JcloudsLocation;
import brooklyn.util.MutableMap;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;

/**
 * The PostgreSqlLiveTest installs Postgresql on various operating systems like Ubuntu, CentOS, Red Hat etc. To make sure that
 * PostgreSql works like expected on these Operating Systems.
 */
public class RubyRepLiveTest extends RubyRepIntegrationTest {
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
        MySqlNode db1 = new MySqlNodeImpl(MutableMap.of("creationScriptContents", MySqlIntegrationTest.CREATION_SCRIPT,
                "port", "9111"), tapp);
        PostgreSqlNode db2 = new PostgreSqlNodeImpl(MutableMap.of("creationScriptContents", PostgreSqlIntegrationTest.CREATION_SCRIPT,
                "port", "9112"), tapp);
        super.testInLocation(db1, db2, getLocation(osRegex));
    }
    
    protected Location getLocation(String osRegex) {
        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newDefault();
        brooklynProperties.put("brooklyn.jclouds.cloudservers-uk.image-name-regex", osRegex);
        brooklynProperties.remove("brooklyn.jclouds.cloudservers-uk.image-id");
        brooklynProperties.put("inboundPorts", new int[] {22, 5432});
        LocationRegistry locationRegistry = new LocationRegistry(brooklynProperties);
        return locationRegistry.resolve("cloudservers-uk");
    }
}
