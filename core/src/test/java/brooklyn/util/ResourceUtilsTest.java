package brooklyn.util;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ResourceUtilsTest {

    private String tempFileContents = "abc";
    private ResourceUtils utils;
    private File tempFile;
    
    @BeforeClass(alwaysRun=true)
    public void setUp() throws Exception {
        utils = new ResourceUtils(ResourceUtilsTest.class.getClassLoader(), "mycontext");
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
        InputStream stream = utils.getResourceFromUrl("file://"+tempFile.getAbsolutePath());
        assertEquals(ResourceUtils.readFullyString(stream), tempFileContents);
    }
    
    @Test
    public void testGetResourceViaFileWithoutPrefix() throws Exception {
        InputStream stream = utils.getResourceFromUrl(tempFile.getAbsolutePath());
        assertEquals(ResourceUtils.readFullyString(stream), tempFileContents);
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
}
