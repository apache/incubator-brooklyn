package brooklyn.entity.database.postgresql;


import brooklyn.config.BrooklynProperties
import brooklyn.entity.basic.Entities
import brooklyn.entity.database.VogellaExampleAccess
import brooklyn.entity.database.mysql.MySqlIntegrationTest
import brooklyn.entity.database.mysql.MySqlNode
import brooklyn.location.basic.LocationRegistry
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.test.entity.TestApplication
import org.testng.annotations.Test

import static java.util.Arrays.asList
import brooklyn.entity.database.BaseDatabaseLiveTest

/**
 * The PostgreSqlLiveTest installs Postgresql on various operating systems like Ubuntu, CentOS, Red Hat etc. To make sure that
 * PostgreSql works like expected on these Operating Systems.
 */
public class PostgreSqlLiveTest extends PostgreSqlIntegrationTest {
    @Test(groups = ["Live"])
    public void test_Debian_6() {
        test("Debian 6");
    }

    @Test(groups = ["Live"])
    public void test_Ubuntu_10_0() {
        test("Ubuntu 10.0");
    }

    @Test(groups = ["Live"])
    public void test_Ubuntu_11_0() {
        test("Ubuntu 11.0");
    }

    @Test(groups = ["Live"])
    public void test_Ubuntu_12_0() {
        test("Ubuntu 12.0");
    }

    @Test(groups = ["Live"])
    public void test_CentOS_6_0() {
        test("CentOS 6.0");
    }

    @Test(groups = ["Live"])
    public void test_CentOS_5_6() {
        test("CentOS 5.6");
    }

    @Test(groups = ["Live"])
    public void test_Fedora_17() {
        test("Fedora 17");
    }

    @Test(groups = ["Live"])
    public void test_Red_Hat_Enterprise_Linux_6() {
        test("Red Hat Enterprise Linux 6");
    }

    @Test(groups = ["Live"])
    public void test_localhost() throws Exception {
        super.test_localhost();
    }
    
    public void test(String osRegex) throws Exception {
        TestApplication tapp = new TestApplication(name: "PostgreSqlLiveTest");

        PostgreSqlNode psql = new PostgreSqlNode(tapp, creationScriptContents: CREATION_SCRIPT);

        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newDefault();
        brooklynProperties.put("brooklyn.jclouds.cloudservers-uk.image-name-regex", osRegex);
        brooklynProperties.remove("brooklyn.jclouds.cloudservers-uk.image-id");
        brooklynProperties.put("inboundPorts", [22, 5432]);
        LocationRegistry locationRegistry = new LocationRegistry(brooklynProperties);

        JcloudsLocation jcloudsLocation = (JcloudsLocation) locationRegistry.resolve("cloudservers-uk");

        Entities.startManagement(tapp);
        tapp.start(asList(jcloudsLocation));

        SshMachineLocation l = (SshMachineLocation) mysql.getLocations().iterator().next();
        //hack to get the port for mysql open; is the inbounds property not respected on rackspace??
        l.exec(asList("iptables -I INPUT -p tcp --dport 5432 -j ACCEPT"))

        String host = mysql.getAttribute(MySqlNode.HOSTNAME);
        int port = mysql.getAttribute(MySqlNode.MYSQL_PORT);
        new VogellaExampleAccess().readDataBase("org.postgresql.Driver", "postgresql", host, port);
    }
}
