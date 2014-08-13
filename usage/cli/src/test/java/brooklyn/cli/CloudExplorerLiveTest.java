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
package brooklyn.cli;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import io.airlift.command.Cli;
import io.airlift.command.ParseCommandMissingException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.cli.Main.BrooklynCommand;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class CloudExplorerLiveTest {

    private String stdout;
    private String stderr;
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        stdout = null;
        stderr = null;
    }

    @Test
    public void testNoArgsThrowsException() throws Exception {
        try {
            call(new String[0]);
            fail();
        } catch (ParseCommandMissingException e) {
            // expected
        }
    }

    // A user running these tests might not have any instances; so don't assert that there will be one
    @Test(groups={"Live", "Live-sanity"})
    public void testListInstances() throws Exception {
        call("compute", "list-instances", "--location", "jclouds:aws-ec2:eu-west-1");
        
        String errmsg = "stdout="+stdout+"; stderr="+stderr;
        
        List<String> lines = assertAndStipSingleLocationHeader(stdout);
        assertTrue(lines.get(0).equals("Instances {"), errmsg);
        assertTrue(lines.get(lines.size()-1).equals("}"), errmsg);
        assertTrue(stderr.isEmpty(), errmsg);
    }

    @Test(groups={"Live", "Live-sanity"})
    public void testListImages() throws Exception {
        call("compute", "list-images", "--location", "jclouds:softlayer:ams01");
        
        String errmsg = "stdout="+stdout+"; stderr="+stderr;
        
        // FIXME Now has location details pre-amble; fix assertions
        List<String> lines = assertAndStipSingleLocationHeader(stdout);
        assertTrue(lines.get(0).equals("Images {"), errmsg);
        assertTrue(lines.get(lines.size()-1).equals("}"), errmsg);
        assertTrue(stderr.isEmpty(), errmsg);
        
        List<String> imageLines = lines.subList(1, lines.size()-1);
        assertTrue(imageLines.size() > 0, errmsg);
        assertTrue(imageLines.get(0).matches(".*id=.*providerId=.*os=.*description=.*"), "line="+imageLines.get(0)+"; "+errmsg);
    }

    @Test(groups={"Live", "Live-sanity"})
    public void testListHardwareProfiles() throws Exception {
        call("compute", "list-hardware-profiles", "--location", "jclouds:softlayer:ams01");
        
        String errmsg = "stdout="+stdout+"; stderr="+stderr;
        
        // FIXME Now has location details pre-amble; fix assertions
        List<String> lines = assertAndStipSingleLocationHeader(stdout);
        assertTrue(lines.get(0).equals("Hardware Profiles {"), errmsg);
        assertTrue(lines.get(lines.size()-1).equals("}"), errmsg);
        assertTrue(stderr.isEmpty(), errmsg);
        
        List<String> hardwareProfileLines = lines.subList(1, lines.size()-1);
        assertTrue(hardwareProfileLines.size() > 0, errmsg);
        assertTrue(hardwareProfileLines.get(0).matches(".*cpu=.*memory=.*processors=.*"), "line="+hardwareProfileLines.get(0)+"; "+errmsg);
    }

    @Test(groups={"Live", "Live-sanity"})
    public void testGetImage() throws Exception {
        call("compute", "get-image", "--location", "jclouds:softlayer:ams01", "CENTOS_6_64");
        
        String errmsg = "stdout="+stdout+"; stderr="+stderr;
        
        // FIXME Now has location details pre-amble; fix assertions
        List<String> lines = assertAndStipSingleLocationHeader(stdout);
        assertTrue(lines.get(0).equals("Image CENTOS_6_64 {"), errmsg);
        assertTrue(lines.get(lines.size()-1).equals("}"), errmsg);
        assertTrue(stderr.isEmpty(), errmsg);
        
        List<String> imageLines = lines.subList(1, lines.size()-1);
        assertTrue(imageLines.size() > 0, errmsg);
        assertTrue(imageLines.get(0).matches(".*id=.*providerId=.*os=.*description=.*"), "line="+imageLines.get(0)+"; "+errmsg);
    }

    @Test(groups={"Live", "Live-sanity"})
    public void testGetDefaultTemplate() throws Exception {
        call("compute", "default-template", "--location", "jclouds:softlayer:ams01");
        
        String errmsg = "stdout="+stdout+"; stderr="+stderr;
        
        // FIXME Now has location details pre-amble; fix assertions
        List<String> lines = assertAndStipSingleLocationHeader(stdout);
        assertTrue(lines.get(0).equals("Default template {"), errmsg);
        assertTrue(lines.get(lines.size()-1).equals("}"), errmsg);
        assertTrue(stderr.isEmpty(), errmsg);
        
        List<String> imageLines = lines.subList(1, lines.size()-1);
        assertTrue(imageLines.size() > 0, errmsg);
        assertTrue(imageLines.get(0).matches("\tImage.*id=.*providerId=.*os=.*description=.*"), "line="+imageLines.get(0)+"; "+errmsg);
        assertTrue(imageLines.get(1).matches("\tHardware.*cpu=.*memory=.*processors=.*"), "line="+imageLines.get(1)+"; "+errmsg);
        assertTrue(imageLines.get(2).matches("\tLocation.*scope=.*"), "line="+imageLines.get(2)+"; "+errmsg);
        assertTrue(imageLines.get(3).matches("\tOptions.*"), "line="+imageLines.get(3)+"; "+errmsg);
    }

    /**
     * Expects in brooklyn.properties:
     *     brooklyn.location.named.softlayer-swift-ams01=jclouds:swift:https://ams01.objectstorage.softlayer.net/auth/v1.0
     *     brooklyn.location.named.softlayer-swift-ams01.identity=ABCDEFGH:myusername
     *     brooklyn.location.named.softlayer-swift-ams01.credential=1234567890...
     */
    @Test(groups={"Live", "Live-sanity"})
    public void testListContainers() throws Exception {
        call("blobstore", "list-containers", "--location", "named:softlayer-swift-ams01");
        
        String errmsg = "stdout="+stdout+"; stderr="+stderr;
        
        // FIXME Now has location details pre-amble; fix assertions
        List<String> lines = assertAndStipSingleLocationHeader(stdout);
        assertTrue(lines.get(0).equals("Containers {"), errmsg);
        assertTrue(lines.get(lines.size()-1).equals("}"), errmsg);
        assertTrue(stderr.isEmpty(), errmsg);
    }

    protected void call(String... args) throws Exception {
        call(new ByteArrayInputStream(new byte[0]), args);
    }

    protected void call(InputStream instream, String... args) throws Exception {
        ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();
        
        Cli<BrooklynCommand> parser = new CloudExplorer().cliBuilder().build();
        
        BrooklynCommand command = parser.parse(args);
        command.stdout = new PrintStream(stdoutStream);
        command.stderr = new PrintStream(stderrStream);
        command.stdin = instream;
        try {
            command.call();
        } finally {
            stdout = new String(stdoutStream.toByteArray());
            stderr = new String(stderrStream.toByteArray());
        }
    }
    
    private List<String> assertAndStipSingleLocationHeader(String stdout) {
        List<String> lines = ImmutableList.copyOf(Splitter.on("\n").omitEmptyStrings().split(stdout));

        String errmsg = "lines="+lines;
        
        int nextLineCount = 0;
        assertEquals(lines.get(nextLineCount++), "Location {", errmsg);
        assertEquals(lines.get(lines.size()-1), "}", errmsg);
        assertTrue(lines.get(nextLineCount++).startsWith("\tprovider: "), errmsg);
        assertTrue(lines.get(nextLineCount++).startsWith("\tdisplayName: "), errmsg);
        assertTrue(lines.get(nextLineCount++).startsWith("\tidentity: "), errmsg);
        if (lines.get(nextLineCount).startsWith("\tendpoint: ")) nextLineCount++;
        if (lines.get(nextLineCount).startsWith("\tregion: ")) nextLineCount++;
        
        List<String> result = Lists.newArrayList();
        for (String line : lines.subList(nextLineCount, lines.size()-1)) {
            assertTrue(line.startsWith("\t"), errmsg);
            result.add(line.substring(1));
        }
        return result;
    }
}
