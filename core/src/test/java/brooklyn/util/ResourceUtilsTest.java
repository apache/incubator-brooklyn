package brooklyn.util;

import static org.testng.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

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
    public void testMergePaths() throws Exception {
        assertEquals(ResourceUtils.mergePaths("a","b"), "a/b");
        assertEquals(ResourceUtils.mergePaths("/a//","/b/"), "/a/b/");
        assertEquals(ResourceUtils.mergePaths("foo://","/b/"), "foo:///b/");
    }

}
