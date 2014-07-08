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
package brooklyn.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.util.os.Os;
import brooklyn.util.stream.Streams;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

public class ResourceUtilsTest {

    private static final Logger log = LoggerFactory.getLogger(ResourceUtilsTest.class);
    
    private String tempFileContents = "abc";
    private ResourceUtils utils;
    private File tempFile;
    
    @BeforeClass(alwaysRun=true)
    public void setUp() throws Exception {
        utils = ResourceUtils.create(this, "mycontext");
        tempFile = Os.writeToTempFile(new ByteArrayInputStream(tempFileContents.getBytes()), "resourceutils-test", ".txt");
    }
    
    @AfterClass(alwaysRun=true)
    public void tearDown() throws Exception {
        if (tempFile != null) tempFile.delete();
    }

    @Test
    public void testWriteStreamToTempFile() throws Exception {
        File tempFileLocal = Os.writeToTempFile(new ByteArrayInputStream("mycontents".getBytes()), "resourceutils-test", ".txt");
        try {
            List<String> lines = Files.readLines(tempFileLocal, Charsets.UTF_8);
            assertEquals(lines, ImmutableList.of("mycontents"));
        } finally {
            tempFileLocal.delete();
        }
    }

    @Test
    public void testPropertiesStreamToTempFile() throws Exception {
        Properties props = new Properties();
        props.setProperty("mykey", "myval");
        File tempFileLocal = Os.writePropertiesToTempFile(props, "resourceutils-test", ".txt");
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(tempFileLocal);
            Properties props2 = new Properties();
            props2.load(fis);
            assertEquals(props2.getProperty("mykey"), "myval");
        } finally {
            Streams.closeQuietly(fis);
            tempFileLocal.delete();
        }
    }

    @Test
    public void testGetResourceViaClasspathWithPrefix() throws Exception {
        InputStream stream = utils.getResourceFromUrl("classpath://brooklyn/config/sample.properties");
        assertNotNull(stream);
    }
    
    @Test
    public void testGetResourceViaClasspathWithoutPrefix() throws Exception {
        InputStream stream = utils.getResourceFromUrl("/brooklyn/config/sample.properties");
        assertNotNull(stream);
    }

    @Test
    public void testGetResourceViaFileWithPrefix() throws Exception {
        // The correct format for file URLs is file:///<absolute path>.
        // On UNIX file:///tmp.
        // On Windows both file:/C:/temp and file:///C:/temp are supported by Java, 
        // while Windows itself supports the latter only. 
        // Note that file://C:/temp is *wrong*, because C: is interpreted as the host
        InputStream stream = utils.getResourceFromUrl(tempFile.toURI().toURL().toString());
        assertEquals(Streams.readFullyString(stream), tempFileContents);
    }
    
    @Test
    public void testGetResourceViaFileWithoutPrefix() throws Exception {
        InputStream stream = utils.getResourceFromUrl(tempFile.getAbsolutePath());
        assertEquals(Streams.readFullyString(stream), tempFileContents);
    }

    @Test
    public void testClassLoaderDir() throws Exception {
        String d = utils.getClassLoaderDir();
        log.info("Found resource "+this+" in: "+d);
        assertTrue(new File(d, "brooklyn/util/").exists());
    }

    @Test
    public void testClassLoaderDirFromJar() throws Exception {
        String d = utils.getClassLoaderDir("java/lang/Object.class");
        log.info("Found Object in: "+d);
        assertTrue(d.toLowerCase().endsWith(".jar"));
    }

    @Test
    public void testClassLoaderDirFromJarWithSlash() throws Exception {
        String d = utils.getClassLoaderDir("/java/lang/Object.class");
        log.info("Found Object in: "+d);
        assertTrue(d.toLowerCase().endsWith(".jar"));
    }

    @Test(expectedExceptions={NoSuchElementException.class})
    public void testClassLoaderDirNotFound() throws Exception {
        String d = utils.getClassLoaderDir("/somewhere/not/found/XXX.xxx");
        // above should fail
        log.warn("Uh oh found iamginary resource in: "+d);
    }

    @Test(groups="Integration")
    public void testGetResourceViaSftp() throws Exception {
        InputStream stream = utils.getResourceFromUrl("sftp://localhost:"+tempFile.getAbsolutePath());
        assertEquals(Streams.readFullyString(stream), tempFileContents);
    }
    
    @Test(groups="Integration")
    public void testGetResourceViaSftpWithUsername() throws Exception {
        String user = System.getProperty("user.name");
        InputStream stream = utils.getResourceFromUrl("sftp://"+user+"@localhost:"+tempFile.getAbsolutePath());
        assertEquals(Streams.readFullyString(stream), tempFileContents);
    }

    @Test
    public void testDataUrl() throws Exception {
        assertEquals(utils.getResourceAsString("data:,hello"), "hello");
        assertEquals(utils.getResourceAsString("data:,hello%20world"), "hello world");
        // above is correct. below are not valid ... but we accept them anyway
        assertEquals(utils.getResourceAsString("data:hello"), "hello");
        assertEquals(utils.getResourceAsString("data://hello"), "hello");
        assertEquals(utils.getResourceAsString("data:hello world"), "hello world");
    }
    
    // See https://github.com/brooklyncentral/brooklyn/issues/1338
    @Test(groups={"Integration", "WIP"})
    public void testResourceFromUrlFollowsRedirect() throws Exception {
        String contents = new ResourceUtils(this).getResourceAsString("http://bit.ly/brooklyn-visitors-creation-script");
        assertFalse(contents.contains("bit.ly"), "contents="+contents);
    }
}
