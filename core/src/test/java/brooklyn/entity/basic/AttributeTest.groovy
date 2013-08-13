package brooklyn.entity.basic

import static org.testng.Assert.*

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.test.entity.TestEntityImpl

public class AttributeTest {
    static AttributeSensor<String> COLOR = new BasicAttributeSensor<String>(String.class, "my.color");

    TestEntityImpl e;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        e = new TestEntityImpl();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        // nothing to tear down; entity was not managed (i.e. had no management context)
    }

    @Test
    public void canGetAndSetAttribute() {
        e.setAttribute(COLOR, "red")
        assertEquals(e.getAttribute(COLOR), "red")
    }
    
    @Test
    public void missingAttributeIsNull() {
        assertEquals(e.getAttribute(COLOR), null)
    }
    
    @Test
    public void canGetAttributeByNameParts() {
        // Initially null
        assertNull(e.getAttributeByNameParts(COLOR.nameParts))
        
        // Once set, returns val
        e.setAttribute(COLOR, "red")
        assertEquals(e.getAttributeByNameParts(COLOR.nameParts), "red")
    }
}
