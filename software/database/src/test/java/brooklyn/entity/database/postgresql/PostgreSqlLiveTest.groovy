package brooklyn.entity.database.postgresql;


import brooklyn.config.BrooklynProperties
import brooklyn.entity.basic.Entities
import brooklyn.entity.database.VogellaExampleAccess
import brooklyn.entity.database.mysql.MySqlNode
import brooklyn.location.basic.LocationRegistry
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.jclouds.JcloudsLocation
import org.testng.annotations.Test

import static java.util.Arrays.asList

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
        PostgreSqlNode psql = tapp.createAndManageChild(BasicEntitySpec.newInstance(PostgreSqlNode.class)
                .configure("creationScriptContents", CREATION_SCRIPT));

        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newDefault();
        brooklynProperties.put("brooklyn.jclouds.cloudservers-uk.image-name-regex", osRegex);
        brooklynProperties.remove("brooklyn.jclouds.cloudservers-uk.image-id");
        brooklynProperties.put("inboundPorts", [22, 5432]);
        LocationRegistry locationRegistry = new LocationRegistry(brooklynProperties);

        JcloudsLocation jcloudsLocation = (JcloudsLocation) locationRegistry.resolve("cloudservers-uk");

        tapp.start(asList(jcloudsLocation));

        SshMachineLocation l = (SshMachineLocation) psql.getLocations().iterator().next();
        //hack to get the port for postgresql open; is the inbounds property not respected on rackspace??
        l.exec(asList("iptables -I INPUT -p tcp --dport 5432 -j ACCEPT"))

        String host = psql.getAttribute(PostgreSqlNode.HOSTNAME);
        int port = psql.getAttribute(PostgreSqlNode.POSTGRESQL_PORT);
        new VogellaExampleAccess().readDataBase("org.postgresql.Driver", "postgresql", host, port);
    }
}
