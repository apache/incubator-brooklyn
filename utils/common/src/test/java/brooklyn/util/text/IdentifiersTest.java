package brooklyn.util.text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

public class IdentifiersTest {

    private static final Logger log = LoggerFactory.getLogger(IdentifiersTest.class);
    
    @Test
    public void testRandomId() {
        String id1 = Identifiers.makeRandomId(4);
        Assert.assertEquals(id1.length(), 4);
        String id2 = Identifiers.makeRandomId(4);
        Assert.assertNotEquals(id1, id2);
    }

    @Test
    public void testFromHash() {
        String id1 = Identifiers.makeIdFromHash("Hello".hashCode());
        Assert.assertTrue(!Strings.isBlank(id1));
        
        String id2 = Identifiers.makeIdFromHash("hello".hashCode());
        String id3 = Identifiers.makeIdFromHash("hello".hashCode());
        Assert.assertEquals(id2, id3);
        Assert.assertNotEquals(id1, id2);

        Assert.assertEquals(Identifiers.makeIdFromHash(0), "A");
        
        String idLong = Identifiers.makeIdFromHash(Long.MAX_VALUE);
        log.info("ID's made from hash, of 'hello' is "+id1+" and of Long.MAX_VALUE is "+idLong);
        Assert.assertTrue(idLong.length() > id1.length());
    }

    @Test
    public void testFromNegativeHash() {
        String id1 = Identifiers.makeIdFromHash(-1);
        Assert.assertTrue(!Strings.isBlank(id1));
        log.info("ID's made from hash, of -1 is "+id1+" and of Long.MIN_VALUE is "+Identifiers.makeIdFromHash(Long.MIN_VALUE));
    }

}
