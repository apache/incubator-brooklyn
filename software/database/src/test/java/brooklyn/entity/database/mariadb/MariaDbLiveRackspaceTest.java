package brooklyn.entity.database.mariadb;

import org.testng.annotations.Test;

import brooklyn.entity.database.VogellaExampleAccess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.jclouds.JcloudsLocation;

import com.google.common.collect.ImmutableList;

/**
 * The MariaDbLiveTest installs MariaDb on various operating systems like Ubuntu, CentOS, Red Hat etc. To make sure that
 * MariaDb works like expected on these Operating Systems.
 */
public class MariaDbLiveRackspaceTest extends MariaDbIntegrationTest {
    @Test(groups = {"Live"})
    public void test_Debian_6() throws Exception {
        test("Debian 6");
    }

    @Test(groups = {"Live"})
    public void test_Ubuntu_10_0() throws Exception {
        test("Ubuntu 10.0");
    }

    @Test(groups = {"Live", "Live-sanity"})
    public void test_Ubuntu_12_0() throws Exception {
        test("Ubuntu 12.0");
    }

    @Test(groups = {"Live"})
    public void test_Ubuntu_13() throws Exception {
        test("Ubuntu 13");
    }

    @Test(groups = {"Live"})
    public void test_CentOS_6() throws Exception {
        test("CentOS 6");
    }

    @Test(groups = {"Live"})
    public void test_CentOS_5() throws Exception {
        test("CentOS 5");
    }

    @Test(groups = {"Live"})
    public void test_Fedora() throws Exception {
        test("Fedora ");
    }

    @Test(groups = {"Live"})
    public void test_Red_Hat_Enterprise_Linux_6() throws Exception {
        test("Red Hat Enterprise Linux 6");
    }

    @Test(groups = {"Live"})
    public void test_localhost() throws Exception {
        super.test_localhost();
    }

    public void test(String osRegex) throws Exception {
        MariaDbNode mariadb = tapp.createAndManageChild(EntitySpec.create(MariaDbNode.class)
                .configure("creationScriptContents", CREATION_SCRIPT));

        brooklynProperties.put("brooklyn.location.jclouds.rackspace-cloudservers-uk.imageNameRegex", osRegex);
        brooklynProperties.remove("brooklyn.location.jclouds.rackspace-cloudservers-uk.image-id");
        brooklynProperties.put("inboundPorts", "22, 3306");
        JcloudsLocation jcloudsLocation = (JcloudsLocation) managementContext.getLocationRegistry().resolve("jclouds:rackspace-cloudservers-uk");

        tapp.start(ImmutableList.of(jcloudsLocation));

        SshMachineLocation l = (SshMachineLocation) mariadb.getLocations().iterator().next();
        //hack to get the port for mysql open; is the inbounds property not respected on rackspace??
        l.exec(ImmutableList.of("iptables -I INPUT -p tcp --dport 3306 -j ACCEPT"));

        new VogellaExampleAccess("com.mysql.jdbc.Driver", mariadb.getAttribute(MariaDbNode.DB_URL)).readModifyAndRevertDataBase();
       
    } 
}
