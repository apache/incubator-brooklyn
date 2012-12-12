package brooklyn.entity.database;


import brooklyn.config.BrooklynProperties
import brooklyn.entity.basic.Entities
import brooklyn.entity.database.mysql.MySqlIntegrationTest
import brooklyn.entity.database.mysql.MySqlNode
import brooklyn.location.basic.LocationRegistry
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.test.entity.TestApplication
import org.testng.annotations.Test

import static java.util.Arrays.asList
import brooklyn.entity.Application
import brooklyn.entity.basic.SoftwareProcessEntity

/**
 * The MySqlLiveTest installs a database on various operating systems like Ubuntu, CentOS, Red Hat etc. To make sure that
 * it works like expected on these Operating Systems.
 */
public abstract class BaseDatabaseLiveTest {
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

    public abstract void test(String osRegex) throws Exception;
    
}
