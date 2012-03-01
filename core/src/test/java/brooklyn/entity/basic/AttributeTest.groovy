package brooklyn.entity.basic;

import static org.testng.Assert.*

import org.testng.annotations.Test

import brooklyn.entity.LocallyManagedEntity
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicAttributeSensor

public class AttributeTest {
    static AttributeSensor<String> COLOR = new BasicAttributeSensor<String>(String.class, "my.color");

    @Test
    public void canGetAndSetAttribute() {
        LocallyManagedEntity e = []
        e.setAttribute(COLOR, "red")
        assertEquals(e.getAttribute(COLOR), "red")
    }
    
    @Test
    public void missingAttributeIsNull() {
        LocallyManagedEntity e = []
        assertEquals(e.getAttribute(COLOR), null)
    }
    
    @Test
    public void canGetAttributeByNameParts() {
        LocallyManagedEntity e = []
        
        // Initially null
        assertNull(e.getAttributeByNameParts(COLOR.nameParts))
        
        // Once set, returns val
        e.setAttribute(COLOR, "red")
        assertEquals(e.getAttributeByNameParts(COLOR.nameParts), "red")
    }
}
