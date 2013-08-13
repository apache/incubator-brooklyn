package brooklyn.entity.proxying;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;

public class BasicEntityTypeRegistryTest {

    private BasicEntityTypeRegistry registry;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        registry = new BasicEntityTypeRegistry();
    }

    @AfterMethod
    public void tearDown(){
        // nothing to tear down; no management context created
    }

    @Test
    public void testRegisterAllowsOverridingKey() {
        registry.registerImplementation(MyEntity.class, MyEntityImpl.class);
        registry.registerImplementation(MyEntity.class, MyEntityImpl2.class);
        assertEquals(registry.getImplementedBy(MyEntity.class), MyEntityImpl2.class);
    }

    @Test
    public void testRegisterForbidsDuplicateValues() {
        registry.registerImplementation(MyEntity.class, MyEntityImpl2.class);
        try {
            registry.registerImplementation(MyEntity2.class, MyEntityImpl2.class);
        } catch (IllegalArgumentException e) {
            if (!e.toString().contains("already registered against type")) throw e;
        }
    }

    @Test
    public void testGetImplementionLooksUpAnnotations() {
        assertEquals(registry.getImplementedBy(MyEntity.class), MyEntityImpl.class);
    }

    @Test
    public void testGetImplementionUsesRegistryFirst() {
        registry.registerImplementation(MyEntity.class, MyEntityImpl2.class);
        assertEquals(registry.getImplementedBy(MyEntity.class), MyEntityImpl2.class);
    }

    @Test
    public void testGetImplementionThrowsIfNoRegistryOrAnnotation() {
        try {
            Class<?> result = registry.getImplementedBy(MyEntityWithoutAnnotation.class);
            fail("result="+result);
        } catch (IllegalArgumentException e) {
            if (!e.toString().contains("MyEntityWithoutAnnotation is not annotated")) throw e;
        }
    }

    @Test
    public void testGetEntityTypeOfLooksUpAnnotation() {
        assertEquals(registry.getEntityTypeOf(MyEntityImpl.class), MyEntity.class);
    }

    @Test
    public void testGetEntityTypeOfLooksUpRegistry() {
        registry.registerImplementation(MyEntity.class, MyEntityImpl2.class);
        assertEquals(registry.getEntityTypeOf(MyEntityImpl2.class), MyEntity.class);
    }

    @Test
    public void testGetEntityTypeOfThrowsIfNoRegistryOrAnnotation() {
        try {
            Class<?> result = registry.getEntityTypeOf(MyEntityImpl2.class);
            fail("result="+result);
        } catch (IllegalArgumentException e) {
            if (!e.toString().matches(".*Interfaces of .* not annotated.*")) throw e;
        }
    }

    @Test
    public void testGetEntityTypeOfLooksUpAnnotationOnIndirectlyImplementedClasses() {
        assertEquals(registry.getEntityTypeOf(MyIndirectEntityImpl.class), MyIndirectEntity.class);
    }

    public interface MyEntityWithoutAnnotation extends Entity {
    }

    @ImplementedBy(MyEntityImpl.class)
    public interface MyEntity extends Entity {
    }

    public interface MyEntity2 extends Entity {
    }

    public static class MyEntityImpl extends AbstractEntity implements MyEntity {
    }
    
    public static class MyEntityImpl2 extends AbstractEntity implements MyEntity, MyEntity2 {
    }
    
    @ImplementedBy(MyIndirectEntityImpl.class)
    public interface MyIndirectEntity extends Entity {
    }
    
    public interface MyIndirectEntitySub extends MyIndirectEntity {
    }
    
    public static class MyIndirectEntityImpl extends AbstractEntity implements MyIndirectEntitySub {
    }
}
