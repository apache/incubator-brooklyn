package brooklyn.test;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;

import com.google.common.collect.Maps;

public class EntityTestUtils {

	// TODO Delete methods from TestUtils, to just have them here (or switch so TestUtils delegates here,
	// and deprecate methods in TestUtils until deleted).
	
    public static <T> void assertAttributeEquals(Entity entity, AttributeSensor<T> attribute, T expected) {
        assertEquals(entity.getAttribute(attribute), expected);
    }
    
    public static <T> void assertConfigEquals(Entity entity, ConfigKey<T> configKey, T expected) {
        assertEquals(entity.getConfig(configKey), expected);
    }
    
    public static <T> void assertAttributeEqualsEventually(final Entity entity, final AttributeSensor<T> attribute, final T expected) {
        assertAttributeEqualsEventually(Maps.newLinkedHashMap(), entity, attribute, expected);
    }
    
    public static <T> void assertAttributeEqualsEventually(Map<?,?> flags, final Entity entity, final AttributeSensor<T> attribute, final T expected) {
        TestUtils.executeUntilSucceeds(flags, new Runnable() {
                public void run() {
                    assertAttributeEquals(entity, attribute, expected);
                }});
    }

    public static <T> void assertAttributeEqualsContinually(final Entity entity, final AttributeSensor<T> attribute, final T expected) {
        assertAttributeEqualsContinually(Maps.newLinkedHashMap(), entity, attribute, expected);
    }
    
    public static <T> void assertAttributeEqualsContinually(Map<?,?> flags, final Entity entity, final AttributeSensor<T> attribute, final T expected) {
        TestUtils.assertSucceedsContinually(new Runnable() {
                public void run() {
                    assertAttributeEquals(entity, attribute, expected);
                }});
    }
}
