package brooklyn.entity.basic;

import org.testng.Assert;
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
        Assert.assertEquals(e.getAttribute(COLOR), "red")
    }
    
    @Test
    public void missingAttributeIsNull() {
        LocallyManagedEntity e = []
        Assert.assertEquals(e.getAttribute(COLOR), null)
    }

}
