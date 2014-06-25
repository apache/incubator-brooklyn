package brooklyn.util.os;

import static org.testng.Assert.assertEquals;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableSet;

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

    public void testTidyPathCanonicalize() throws Exception {
        for (String path : ImmutableSet.of("/a/b", "//a///b", "/a/b/", "/a/b/.", "/q/../a/b")) {
            assertEquals(Os.tidyPath(path), "/a/b");
        }
    }

    public void testTidyPathSimplify() throws Exception {
        assertEquals(Os.tidyPath("x/y/z"), "x/y/z");
        assertEquals(Os.tidyPath(""), ".");
        assertEquals(Os.tidyPath("."), ".");
        assertEquals(Os.tidyPath(".."), "..");
        assertEquals(Os.tidyPath("./x"), "x");
        assertEquals(Os.tidyPath("../x"), "../x");
        assertEquals(Os.tidyPath("/.."), "/");
        assertEquals(Os.tidyPath("x"), "x");
        assertEquals(Os.tidyPath("/"), "/");
        assertEquals(Os.tidyPath("///"), "/");
        assertEquals(Os.tidyPath("/x\\"), "/x\\");
        assertEquals(Os.tidyPath("/x\\y/.."), "/");
    }

    public void testTidyPathHome() throws Exception {
        String userhome = System.getProperty("user.home");
        assertEquals(Os.tidyPath("~/a/b"), userhome+"/a/b");
        assertEquals(Os.tidyPath("~"), userhome);
        assertEquals(Os.tidyPath("/a/~/b"), "/a/~/b");
    }
    
    public void testMergePaths() throws Exception {
        assertEquals(Os.mergePaths("a"), "a"); 
        assertEquals(Os.mergePaths("a", "b"), "a" + File.separator + "b"); 
        assertEquals(Os.mergePaths("a/", "b"), "a/b");
        assertEquals(Os.mergePaths("a", "b/"), "a" + File.separator + "b/");
        assertEquals(Os.mergePaths("/a", "b"), "/a" + File.separator + "b");
    }

}
