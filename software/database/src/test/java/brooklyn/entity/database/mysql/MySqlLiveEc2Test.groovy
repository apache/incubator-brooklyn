package brooklyn.entity.database.mysql


import brooklyn.config.BrooklynProperties;
import brooklyn.entity.database.VogellaExampleAccess;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.location.basic.LocationRegistry;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.basic.jclouds.JcloudsLocation;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;

public class MySqlLiveEc2Test extends MySqlIntegrationTest {

    @Test(groups = ["Live"])
    public void test_Debian_6() {
        test(".*squeeze*.*64.*");
    }

//    @Test(groups = ["Live"])
//    public void test_Ubuntu_10_0() {
//        test("Ubuntu 10.0");
//    }
//
//    @Test(groups = ["Live"])
//    public void test_Ubuntu_11_0() {
//        test("Ubuntu 11.0");
//    }
//
//    @Test(groups = ["Live"])
//    public void test_Ubuntu_12_0() {
//        test("Ubuntu 12.0");
//    }
//
//    @Test(groups = ["Live"])
//    public void test_CentOS_6_0() {
//        test("CentOS 6.0");
//    }
//
//    @Test(groups = ["Live"])
//    public void test_CentOS_5_6() {
//        test("CentOS 5.6");
//    }
//
//    @Test(groups = ["Live"])
//    public void test_Fedora_17() {
//        test("Fedora 17");
//    }
//
//    @Test(groups = ["Live"])
//    public void test_Red_Hat_Enterprise_Linux_6() {
//        test("Red Hat Enterprise Linux 6");
//    }

    public void test(String osRegex) throws Exception {
        MySqlNode mysql = tapp.createAndManageChild(BasicEntitySpec.newInstance(MySqlNode.class)
                .configure("creationScriptContents", CREATION_SCRIPT));


        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newDefault();
        brooklynProperties.put("brooklyn.jclouds.aws-ec2.image-description-regex", osRegex);
        brooklynProperties.remove("brooklyn.jclouds.aws-ec2.image-id");
        brooklynProperties.put("loginUser","root");

        brooklynProperties.put("inboundPorts", [22, 3306]);
        LocationRegistry locationRegistry = new LocationRegistry(brooklynProperties);

        JcloudsLocation jcloudsLocation = (JcloudsLocation) locationRegistry.resolve("aws-ec2:us-east-1");

        tapp.start(asList(jcloudsLocation));

        SshMachineLocation l = (SshMachineLocation) mysql.getLocations().iterator().next();
        //hack to get the port for mysql open; is the inbounds property not respected on rackspace??
        l.exec(asList("iptables -I INPUT -p tcp --dport 3306 -j ACCEPT"))

        String host = mysql.getAttribute(MySqlNode.HOSTNAME);
        int port = mysql.getAttribute(MySqlNode.MYSQL_PORT);
        new VogellaExampleAccess().readDataBase("com.mysql.jdbc.Driver", "mysql", host, port);
    }
}

