package brooklyn.entity.database.mysql;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.basic.Entities;
import brooklyn.location.basic.LocationRegistry;
import brooklyn.location.basic.jclouds.JcloudsLocation;
import brooklyn.test.entity.TestApplication;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;

/**
 * The MySqlLiveTest installs MySQL on various operating systems like Ubuntu, CentOS, Red Hat etc. To make sure that
 * MySQL works like expected on these Operating Systems.
 */
public class MySqlLiveTest extends MySqlIntegrationTest{
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

    public void test(String osRegex) throws Exception {
        TestApplication tapp = new TestApplication(name: "MySqlIntegrationTest");

        MySqlNode mysql = new MySqlNode(tapp, creationScriptContents: CREATION_SCRIPT);

        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newDefault();
        brooklynProperties.put("brooklyn.jclouds.cloudservers-uk.image-name-regex", osRegex);
        brooklynProperties.remove("brooklyn.jclouds.cloudservers-uk.image-id");
        brooklynProperties.put("inboundPorts", [22, 3306]);
        LocationRegistry locationRegistry = new LocationRegistry(brooklynProperties);

        JcloudsLocation jcloudsLocation = (JcloudsLocation) locationRegistry.resolve("cloudservers-uk");

        Entities.startManagement(tapp);
        tapp.start(asList(jcloudsLocation));

        JcloudsLocation.JcloudsSshMachineLocation l = mysql.getLocations().iterator().next();
        //hack to get the port for mysql open; is the inbounds property not respected on rackspace??
        l.exec(asList("iptables -I INPUT -p tcp --dport 3306 -j ACCEPT"))

        String host = mysql.getAttribute(MySqlNode.HOSTNAME);
        int port = mysql.getAttribute(MySqlNode.MYSQL_PORT);
        new VogellaExampleAccess().readDataBase(host, port);
    } 
}
