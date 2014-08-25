/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.location.jclouds;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.Identifiers;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class SimpleJcloudsLocationUserLoginAndConfigLiveTest extends AbstractJcloudsLiveTest {

    // FIXME And tidy up this one
    
    private static final String LOCATION_SPEC = AWS_EC2_PROVIDER + ":" + AWS_EC2_USEAST_REGION_NAME;
    
    private static final Logger log = LoggerFactory.getLogger(SimpleJcloudsLocationUserLoginAndConfigLiveTest.class);
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        jcloudsLocation = resolve(LOCATION_SPEC);
    }
    
    @SuppressWarnings("rawtypes")
    @Test(groups="Live")
    public void testJcloudsCreateBogStandard() throws Exception {
        log.info("TEST testJcloudsCreateBogStandard");
        JcloudsSshMachineLocation m1 = obtainMachine(ImmutableMap.of());

        Map details = MutableMap.of("id", m1.getJcloudsId(), "hostname", m1.getAddress().getHostAddress(), "user", m1.getUser());
        log.info("got machine "+m1+" at "+jcloudsLocation+": "+details+"; now trying to rebind");
        String result;
        // echo conflates spaces of arguments
        result = execWithOutput(m1, Arrays.asList("echo trying  m1", "hostname", "date"));
        Assert.assertTrue(result.contains("trying m1"));
        
        log.info("now trying rebind "+m1);
        JcloudsSshMachineLocation m2 = jcloudsLocation.rebindMachine(details);
        result = execWithOutput(m2, Arrays.asList("echo trying  m2", "hostname", "date"));
        Assert.assertTrue(result.contains("trying m2"));
    }

    @SuppressWarnings("rawtypes")
    @Test(groups="Live")
    public void testJcloudsCreateBogStandardWithUserBrooklyn() throws Exception {
        log.info("TEST testJcloudsCreateBogStandardWithUserBrooklyn");
        JcloudsSshMachineLocation m1 = obtainMachine(MutableMap.of("user", "brooklyn"));

        Map details = MutableMap.of("id", m1.getJcloudsId(), "hostname", m1.getAddress().getHostAddress(), "user", m1.getUser());
        log.info("got machine "+m1+" at "+jcloudsLocation+": "+details+"; now trying to rebind");
        String result;
        // echo conflates spaces of arguments
        result = execWithOutput(m1, Arrays.asList("echo trying  m1", "hostname", "date"));
        Assert.assertTrue(result.contains("trying m1"));
        
        log.info("now trying rebind "+m1);
        JcloudsSshMachineLocation m2 = jcloudsLocation.rebindMachine(details);
        result = execWithOutput(m2, Arrays.asList("echo trying  m2", "hostname", "date"));
        Assert.assertTrue(result.contains("trying m2"));
        
        Assert.assertEquals(m2.getUser(), "brooklyn");
    }
    
    @SuppressWarnings("rawtypes")
    @Test(groups="Live")
    public void testJcloudsCreateUserMetadata() throws Exception {
        log.info("TEST testJcloudsCreateBogStandard");
        String key = "brooklyn-test-user-data";
        String value = "test-"+Identifiers.makeRandomId(4);
        JcloudsSshMachineLocation m1 = obtainMachine(MutableMap.of("userMetadata", key+"="+value));

        Map details = MutableMap.of("id", m1.getJcloudsId(), "hostname", m1.getAddress().getHostAddress(), "user", m1.getUser(),
                "userMetadata", key+"="+value);
        Assert.assertEquals(m1.node.getUserMetadata().get(key), value);
        
        log.info("got machine "+m1+" at "+jcloudsLocation+": "+details+"; now trying to rebind");
        String result;
        // echo conflates spaces of arguments
        result = execWithOutput(m1, Arrays.asList("echo trying  m1", "hostname", "date"));
        Assert.assertTrue(result.contains("trying m1"));
        
        log.info("now trying rebind "+m1);
        JcloudsSshMachineLocation m2 = jcloudsLocation.rebindMachine(details);
        result = execWithOutput(m2, Arrays.asList("echo trying  m2", "hostname", "date"));
        Assert.assertTrue(result.contains("trying m2"));
        Assert.assertEquals(m2.node.getUserMetadata().get(key), value);
    }

    // a curious image, centos, but user is ec2-user, and handily not correctly auto-detected
    // test we can specify a loginUser different from user, and that user is created etc...
    // imageId=us-east-1/ami-f95cf390
    public static final String EC2_CENTOS_IMAGE = "us-east-1/ami-f95cf390";
    
    @Test(groups="Live")
    public void testJcloudsMissingUser() throws Exception {
        log.info("TEST testJcloudsMissingUser");
        try {
            // wait up to 30s for login (override default of 5m so test runs faster)
            obtainMachine(MutableMap.of("imageId", EC2_CENTOS_IMAGE, "waitForSshable", 30*1000));
            log.info("whoops we logged in");
        } catch (NoMachinesAvailableException e) {
            log.info("got error as expected, for missing user: "+e); // success
        }
    }

    @SuppressWarnings("rawtypes")
    @Test(groups="Live")
    public void testJcloudsWithSpecificLoginUserAndSameUser() throws Exception {
        log.info("TEST testJcloudsWithSpecificLoginUserAndSameUser");
        JcloudsSshMachineLocation m1 = obtainMachine(MutableMap.of(
                "imageId", EC2_CENTOS_IMAGE,
                "loginUser", "ec2-user",
                "user", "ec2-user",
                "waitForSshable", 30*1000));

        Map details = MutableMap.of("id", m1.getJcloudsId(), "hostname", m1.getAddress().getHostAddress(), "user", m1.getUser());
        log.info("got machine "+m1+" at "+jcloudsLocation+": "+details+"; now trying to rebind");
        String result;
        // echo conflates spaces of arguments
        result = execWithOutput(m1, Arrays.asList("echo trying  m1", "hostname", "date"));
        Assert.assertTrue(result.contains("trying m1"));
        
        log.info("now trying rebind "+m1);
        JcloudsSshMachineLocation m2 = jcloudsLocation.rebindMachine(details);
        result = execWithOutput(m2, Arrays.asList("echo trying  m2", "hostname", "date"));
        Assert.assertTrue(result.contains("trying m2"));
        
        Assert.assertEquals(m2.getUser(), "ec2-user");
    }

    @SuppressWarnings("rawtypes")
    @Test(groups="Live")
    public void testJcloudsWithSpecificLoginUserAndNewUser() throws Exception {
        log.info("TEST testJcloudsWithSpecificLoginUserAndNewUser");
        JcloudsSshMachineLocation m1 = obtainMachine(MutableMap.of(
                "imageId", EC2_CENTOS_IMAGE,
                "loginUser", "ec2-user",
                "user", "newbob",
                "waitForSshable", 30*1000));

        Map details = MutableMap.of("id", m1.getJcloudsId(), "hostname", m1.getAddress().getHostAddress(), "user", m1.getUser());
        log.info("got machine "+m1+" at "+jcloudsLocation+": "+details+"; now trying to rebind");
        String result;
        // echo conflates spaces of arguments
        result = execWithOutput(m1, Arrays.asList("echo trying  m1", "hostname", "date"));
        Assert.assertTrue(result.contains("trying m1"));
        
        log.info("now trying rebind "+m1);
        JcloudsSshMachineLocation m2 = jcloudsLocation.rebindMachine(details);
        result = execWithOutput(m2, Arrays.asList("echo trying  m2", "hostname", "date"));
        Assert.assertTrue(result.contains("trying m2"));
        
        Assert.assertEquals(m2.getUser(), "newbob");
    }

    @SuppressWarnings("rawtypes")
    @Test(groups="Live")
    public void testJcloudsWithSpecificLoginUserAndDefaultUser() throws Exception {
        log.info("TEST testJcloudsWithSpecificLoginUserAndDefaultUser");
        JcloudsSshMachineLocation m1 = obtainMachine(MutableMap.of(
                "imageId", EC2_CENTOS_IMAGE,
                "loginUser", "ec2-user",
                "waitForSshable", 30*1000));

        Map details = MutableMap.of("id", m1.getJcloudsId(), "hostname", m1.getAddress().getHostAddress(), "user", m1.getUser());
        log.info("got machine "+m1+" at "+jcloudsLocation+": "+details+"; now trying to rebind");
        String result;
        // echo conflates spaces of arguments
        result = execWithOutput(m1, Arrays.asList("echo trying  m1", "hostname", "date"));
        Assert.assertTrue(result.contains("trying m1"));
        
        log.info("now trying rebind "+m1);
        JcloudsSshMachineLocation m2 = jcloudsLocation.rebindMachine(details);
        result = execWithOutput(m2, Arrays.asList("echo trying  m2", "hostname", "date"));
        Assert.assertTrue(result.contains("trying m2"));
    }

    @Test(groups="Live")
    public void testJcloudsCreateWithNoSudoGranted() throws Exception {
        log.info("TEST testJcloudsCreateWithNoSudoGranted");
        JcloudsSshMachineLocation m = obtainMachine(MutableMap.of(
                "grantUserSudo", false,
                "waitForSshable", 30*1000));

        int exitCode = execWithExitCode(m, ImmutableList.of(BashCommands.sudo("echo yes")));
        Assert.assertFalse(exitCode == 0, "exit code for sudo command should not have been 0");
    }

    private String execWithOutput(SshMachineLocation m, List<String> commands) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        exec(m, commands, stdout, stderr);
        return new String(stdout.toByteArray());
    }

    private int execWithExitCode(SshMachineLocation m, List<String> commands) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        return exec(m, commands, stdout, stderr);
    }

    private int exec(SshMachineLocation m, List<String> commands, ByteArrayOutputStream stdout, ByteArrayOutputStream stderr) {
        Map<String, Object> flags = Maps.newLinkedHashMap();
        flags.put("out", stdout);
        flags.put("err", stderr);
        int exitCode = m.execCommands(flags, "test", commands);
        log.info("stdout from "+commands+":\n"+new String(stdout.toByteArray()));
        log.info("stderr from "+commands+":\n"+new String(stderr.toByteArray()));
        log.info("exit code: " + exitCode);
        return exitCode;
    }

    private JcloudsLocation resolve(String spec) {
        return (JcloudsLocation) managementContext.getLocationRegistry().resolve("jclouds:"+spec);
    }
}
