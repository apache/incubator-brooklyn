package brooklyn.util;

import static org.testng.Assert.assertEquals;
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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

public class ResourceUtilsTest {

    private static final Logger log = LoggerFactory.getLogger(ResourceUtilsTest.class);
    
    private String tempFileContents = "abc";
    private ResourceUtils utils;
    private File tempFile;
    
    @BeforeClass(alwaysRun=true)
    public void setUp() throws Exception {
        utils = new ResourceUtils(this, "mycontext");
        tempFile = ResourceUtils.writeToTempFile(new ByteArrayInputStream(tempFileContents.getBytes()), "resourceutils-test", ".txt");
    }
    
    @AfterClass(alwaysRun=true)
    public void tearDown() throws Exception {
        if (tempFile != null) tempFile.delete();
    }

    @Test
    public void testWriteStreamToTempFile() throws Exception {
        File tempFileLocal = ResourceUtils.writeToTempFile(new ByteArrayInputStream("mycontents".getBytes()), "resourceutils-test", ".txt");
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
        File tempFileLocal = ResourceUtils.writeToTempFile(props, "resourceutils-test", ".txt");
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(tempFileLocal);
            Properties props2 = new Properties();
            props2.load(fis);
            assertEquals(props2.getProperty("mykey"), "myval");
        } finally {
            Closeables.closeQuietly(fis);
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
        // on windows the correct syntax is  file:///c:/path  (note the extra /);
        // however our routines also accept file://c:/path so the following is portable
        InputStream stream = utils.getResourceFromUrl("file://"+tempFile.getAbsolutePath());
        assertEquals(ResourceUtils.readFullyString(stream), tempFileContents);
    }
    
    @Test
    public void testGetResourceViaFileWithoutPrefix() throws Exception {
        InputStream stream = utils.getResourceFromUrl(tempFile.getAbsolutePath());
        assertEquals(ResourceUtils.readFullyString(stream), tempFileContents);
    }

    @Test
    public void testClassLoaderDir() throws Exception {
        String d = utils.getClassLoaderDir();
        log.info("Found resource "+this+" in: "+d);
        assertTrue(new File(d+"/brooklyn/util/").exists());
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
        assertEquals(ResourceUtils.readFullyString(stream), tempFileContents);
    }
    
    @Test(groups="Integration")
    public void testGetResourceViaSftpWithUsername() throws Exception {
        String user = System.getProperty("user.name");
        InputStream stream = utils.getResourceFromUrl("sftp://"+user+"@localhost:"+tempFile.getAbsolutePath());
        assertEquals(ResourceUtils.readFullyString(stream), tempFileContents);
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

    @Test
    public void testTidyFilePath() throws Exception {
        String userhome = System.getProperty("user.home");
        assertEquals(ResourceUtils.tidyFilePath("/a/b"), "/a/b");
        assertEquals(ResourceUtils.tidyFilePath("~/a/b"), userhome+"/a/b");
    }
    
    @Test
    public void testMergeFilePaths() throws Exception {
        assertEquals(ResourceUtils.mergeFilePaths("a"), "a"); 
        assertEquals(ResourceUtils.mergeFilePaths("a", "b"), "a/b"); 
        assertEquals(ResourceUtils.mergeFilePaths("a/", "b"), "a/b");
        assertEquals(ResourceUtils.mergeFilePaths("a", "b/"), "a/b/");
        assertEquals(ResourceUtils.mergeFilePaths("/a", "b"), "/a/b");
    }
}
