package brooklyn.location.basic.jclouds;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.JcloudsResolver;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.basic.jclouds.JcloudsLocation.JcloudsSshMachineLocation;
import brooklyn.test.TestUtils;
import brooklyn.util.MutableMap;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Throwables;

public class SimpleJcloudsLocationUserLoginAndConfigTest {

    private static final Logger log = LoggerFactory.getLogger(SimpleJcloudsLocationUserLoginAndConfigTest.class);
    
    @SuppressWarnings("rawtypes")
    @Test(groups="Live")
    public void testJcloudsCreateBogStandard() throws Exception {
        log.info("TEST testJcloudsCreateBogStandard");
        JcloudsLocation l = JcloudsResolver.resolve("aws-ec2:us-east-1");
        JcloudsSshMachineLocation m1 = l.obtain();
        try {
            Map details = MutableMap.of("id", m1.getJcloudsId(), "hostname", m1.getAddress().getHostAddress(), "user", m1.getUser());
            log.info("got machine "+m1+" at "+l+": "+details+"; now trying to rebind");
            String result;
            // echo conflates spaces of arguments
            result = execWithOutput(m1, Arrays.asList("echo trying  m1", "hostname", "date"));
            Assert.assertTrue(result.contains("trying m1"));
            
            log.info("now trying rebind "+m1);
            JcloudsSshMachineLocation m2 = l.rebindMachine(details);
            result = execWithOutput(m2, Arrays.asList("echo trying  m2", "hostname", "date"));
            Assert.assertTrue(result.contains("trying m2"));
        } finally {
            l.release(m1);
        }
    }

    @SuppressWarnings("rawtypes")
    @Test(groups="Live")
    public void testJcloudsCreateBogStandardWithUserBrooklyn() throws Exception {
        log.info("TEST testJcloudsCreateBogStandardWithUserBrooklyn");
        JcloudsLocation l = JcloudsResolver.resolve("aws-ec2:us-east-1");
        JcloudsSshMachineLocation m1 = l.obtain(MutableMap.of("user", "brooklyn"));
        try {
            Map details = MutableMap.of("id", m1.getJcloudsId(), "hostname", m1.getAddress().getHostAddress(), "user", m1.getUser());
            log.info("got machine "+m1+" at "+l+": "+details+"; now trying to rebind");
            String result;
            // echo conflates spaces of arguments
            result = execWithOutput(m1, Arrays.asList("echo trying  m1", "hostname", "date"));
            Assert.assertTrue(result.contains("trying m1"));
            
            log.info("now trying rebind "+m1);
            JcloudsSshMachineLocation m2 = l.rebindMachine(details);
            result = execWithOutput(m2, Arrays.asList("echo trying  m2", "hostname", "date"));
            Assert.assertTrue(result.contains("trying m2"));
            
            Assert.assertEquals(m2.getUser(), "brooklyn");
        } finally {
            l.release(m1);
        }
    }
    
    @SuppressWarnings("rawtypes")
    @Test(groups="Live")
    public void testJcloudsCreateUserMetadata() throws Exception {
        log.info("TEST testJcloudsCreateBogStandard");
        JcloudsLocation l = JcloudsResolver.resolve("aws-ec2:us-east-1");
        String key = "brooklyn-test-user-data";
        String value = "test-"+Identifiers.makeRandomId(4);
        JcloudsSshMachineLocation m1 = l.obtain(MutableMap.of("userMetadata", key+"="+value));
        try {
            Map details = MutableMap.of("id", m1.getJcloudsId(), "hostname", m1.getAddress().getHostAddress(), "user", m1.getUser(),
                    "userMetadata", key+"="+value);
            Assert.assertEquals(m1.node.getUserMetadata().get(key), value);
            
            log.info("got machine "+m1+" at "+l+": "+details+"; now trying to rebind");
            String result;
            // echo conflates spaces of arguments
            result = execWithOutput(m1, Arrays.asList("echo trying  m1", "hostname", "date"));
            Assert.assertTrue(result.contains("trying m1"));
            
            log.info("now trying rebind "+m1);
            JcloudsSshMachineLocation m2 = l.rebindMachine(details);
            result = execWithOutput(m2, Arrays.asList("echo trying  m2", "hostname", "date"));
            Assert.assertTrue(result.contains("trying m2"));
            Assert.assertEquals(m2.node.getUserMetadata().get(key), value);
        } finally {
            l.release(m1);
        }
    }

    // a curious image, centos, but user is ec2-user, and handily not correctly auto-detected
    // test we can specify a loginUser different from user, and that user is created etc...
    // imageId=us-east-1/ami-f95cf390
    public static final String EC2_CENTOS_IMAGE = "us-east-1/ami-f95cf390";
    
    @Test(groups="Live")
    public void testJcloudsMissingUser() throws Exception {
        log.info("TEST testJcloudsMissingUser");
        final JcloudsLocation l = JcloudsResolver.resolve("aws-ec2:us-east-1");
        TestUtils.assertFails(new Runnable() {
            public void run() {
                try {
                    // wait up to 30s for login (override default of 5m so test runs faster)
                    l.obtain(MutableMap.of("imageId", EC2_CENTOS_IMAGE,
                            "waitForSshable", 30*1000));
                    log.info("whoops we logged in");
                } catch (NoMachinesAvailableException e) {
                    log.info("got error as expected, for missing user: "+e);
                    Throwables.propagate(e);
                }
            }
        });
    }

    @SuppressWarnings("rawtypes")
    @Test(groups="Live")
    public void testJcloudsWithSpecificLoginUserAndSameUser() throws Exception {
        log.info("TEST testJcloudsWithSpecificLoginUserAndSameUser");
        JcloudsLocation l = JcloudsResolver.resolve("aws-ec2:us-east-1");
        JcloudsSshMachineLocation m1 = l.obtain(MutableMap.of("imageId", EC2_CENTOS_IMAGE,
                "loginUser", "ec2-user",
                "user", "ec2-user",
                "waitForSshable", 30*1000));
        try {
            Map details = MutableMap.of("id", m1.getJcloudsId(), "hostname", m1.getAddress().getHostAddress(), "user", m1.getUser());
            log.info("got machine "+m1+" at "+l+": "+details+"; now trying to rebind");
            String result;
            // echo conflates spaces of arguments
            result = execWithOutput(m1, Arrays.asList("echo trying  m1", "hostname", "date"));
            Assert.assertTrue(result.contains("trying m1"));
            
            log.info("now trying rebind "+m1);
            JcloudsSshMachineLocation m2 = l.rebindMachine(details);
            result = execWithOutput(m2, Arrays.asList("echo trying  m2", "hostname", "date"));
            Assert.assertTrue(result.contains("trying m2"));
            
            Assert.assertEquals(m2.getUser(), "ec2-user");
        } finally {
            l.release(m1);
        }
    }

    @SuppressWarnings("rawtypes")
    @Test(groups="Live")
    public void testJcloudsWithSpecificLoginUserAndNewUser() throws Exception {
        log.info("TEST testJcloudsWithSpecificLoginUserAndNewUser");
        JcloudsLocation l = JcloudsResolver.resolve("aws-ec2:us-east-1");
        JcloudsSshMachineLocation m1 = l.obtain(MutableMap.of("imageId", EC2_CENTOS_IMAGE,
                "loginUser", "ec2-user",
                "user", "newbob",
                "waitForSshable", 30*1000));
        try {
            Map details = MutableMap.of("id", m1.getJcloudsId(), "hostname", m1.getAddress().getHostAddress(), "user", m1.getUser());
            log.info("got machine "+m1+" at "+l+": "+details+"; now trying to rebind");
            String result;
            // echo conflates spaces of arguments
            result = execWithOutput(m1, Arrays.asList("echo trying  m1", "hostname", "date"));
            Assert.assertTrue(result.contains("trying m1"));
            
            log.info("now trying rebind "+m1);
            JcloudsSshMachineLocation m2 = l.rebindMachine(details);
            result = execWithOutput(m2, Arrays.asList("echo trying  m2", "hostname", "date"));
            Assert.assertTrue(result.contains("trying m2"));
            
            Assert.assertEquals(m2.getUser(), "newbob");
        } finally {
            l.release(m1);
        }
    }

    @SuppressWarnings("rawtypes")
    @Test(groups="Live")
    public void testJcloudsWithSpecificLoginUserAndDefaultUser() throws Exception {
        log.info("TEST testJcloudsWithSpecificLoginUserAndDefaultUser");
        JcloudsLocation l = JcloudsResolver.resolve("aws-ec2:us-east-1");
        JcloudsSshMachineLocation m1 = l.obtain(MutableMap.of("imageId", EC2_CENTOS_IMAGE,
                "loginUser", "ec2-user",
                "waitForSshable", 30*1000));
        try {
            Map details = MutableMap.of("id", m1.getJcloudsId(), "hostname", m1.getAddress().getHostAddress(), "user", m1.getUser());
            log.info("got machine "+m1+" at "+l+": "+details+"; now trying to rebind");
            String result;
            // echo conflates spaces of arguments
            result = execWithOutput(m1, Arrays.asList("echo trying  m1", "hostname", "date"));
            Assert.assertTrue(result.contains("trying m1"));
            
            log.info("now trying rebind "+m1);
            JcloudsSshMachineLocation m2 = l.rebindMachine(details);
            result = execWithOutput(m2, Arrays.asList("echo trying  m2", "hostname", "date"));
            Assert.assertTrue(result.contains("trying m2"));
        } finally {
            l.release(m1);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private String execWithOutput(SshMachineLocation m, List commands) {
        Map flags = new LinkedHashMap();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        flags.put("out", stdout);
        flags.put("err", stderr);
        m.execCommands(flags, "test", commands);
        log.info("output from "+commands+":\n"+new String(stdout.toByteArray()));
        return new String(stdout.toByteArray());
    }


}
