package brooklyn.util.os;

import static org.testng.Assert.assertEquals;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class OsTest {

    private static final Logger log = LoggerFactory.getLogger(OsTest.class);
    
    public void testTmp() {
        log.info("tmp dir is: "+Os.tmp());
        Assert.assertNotNull(Os.tmp());
    }
    
    public void testHome() {
        log.info("home dir is: "+Os.home());
        Assert.assertNotNull(Os.home());        
    }
    
    public void testUser() {
        log.info("user name is: "+Os.user());
        Assert.assertNotNull(Os.user());        
    }

    public void testTidyFilePath() throws Exception {
        String userhome = System.getProperty("user.home");
        assertEquals(Os.tidyPath("/a/b"), "/a/b");
        assertEquals(Os.tidyPath("~/a/b"), userhome+"/a/b");
    }
    
    public void testMergePaths() throws Exception {
        assertEquals(Os.mergePaths("a"), "a"); 
        assertEquals(Os.mergePaths("a", "b"), "a/b"); 
        assertEquals(Os.mergePaths("a/", "b"), "a/b");
        assertEquals(Os.mergePaths("a", "b/"), "a/b/");
        assertEquals(Os.mergePaths("/a", "b"), "/a/b");
    }

}
