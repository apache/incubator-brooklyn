package brooklyn.util.os;

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
    
}
