package brooklyn.entity.database.mysql

import static java.util.Arrays.asList

import org.testng.annotations.Test

import static java.util.Arrays.asList

import org.testng.annotations.Test

import brooklyn.config.BrooklynProperties
import brooklyn.entity.database.VogellaExampleAccess
import brooklyn.entity.proxying.EntitySpecs
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.jclouds.JcloudsLocation

/**
 * The MySqlLiveTest installs MySQL on various operating systems like Ubuntu, CentOS, Red Hat etc. To make sure that
 * MySQL works like expected on these Operating Systems.
 */
public class MySqlLiveRackspaceTest extends MySqlIntegrationTest {
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
        MySqlNode mysql = tapp.createAndManageChild(EntitySpecs.spec(MySqlNode.class)
                .configure("creationScriptContents", CREATION_SCRIPT));

        brooklynProperties.put("brooklyn.jclouds.rackspace-cloudservers-uk.image-name-regex", osRegex);
        brooklynProperties.remove("brooklyn.jclouds.rackspace-cloudservers-uk.image-id");
        brooklynProperties.put("inboundPorts", "22, 3306");
        JcloudsLocation jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve("jclouds:rackspace-cloudservers-uk");

        tapp.start(asList(jcloudsLocation));

        SshMachineLocation l = (SshMachineLocation) mysql.getLocations().iterator().next();
        //hack to get the port for mysql open; is the inbounds property not respected on rackspace??
        l.exec(asList("iptables -I INPUT -p tcp --dport 3306 -j ACCEPT"))

        new VogellaExampleAccess("com.mysql.jdbc.Driver", mysql.getAttribute(MySqlNode.DB_URL)).readModifyAndRevertDataBase();
       
    } 
}
