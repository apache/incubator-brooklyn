package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;

import brooklyn.management.internal.LocalManagementContext;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.test.entity.TestEntityImpl;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.TypeToken;

public class MementoTransformerTest {

    private Entity entity;
    private RebindContextImpl rebindContext;
    private SshMachineLocation location;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        location = new SshMachineLocation(MutableMap.of("address", "localhost"));
        entity = new TestEntityImpl();
        rebindContext = new RebindContextImpl(MementoTransformerTest.class.getClassLoader());
        rebindContext.registerLocation(location.getId(), location);
        rebindContext.registerEntity(entity.getId(), entity);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        LocalManagementContext.terminateAll();
    }

    @Test
    public void testTransformLocation() throws Exception {
        assertTransformsLocationIds(location, Location.class);
    }
    
    @Test
    public void testTransformLocationSet() throws Exception {
        assertTransformsLocationIds(ImmutableSet.of(location), Set.class);
    }
    
    @Test
    public void testTransformLocationList() throws Exception {
        assertTransformsLocationIds(ImmutableList.of(location), List.class);
    }
    
    @Test
    public void testTransformLocationMaop() throws Exception {
        assertTransformsLocationIds(ImmutableMap.of("a", location), Map.class);
    }
    
    @Test
    public void testTransformEntity() throws Exception {
        assertTransformsEntityIds(entity, Entity.class);
    }
    
    @Test
    public void testTransformEntitySet() throws Exception {
        assertTransformsEntityIds(ImmutableSet.of(entity), Set.class);
    }
    
    @Test
    public void testTransformEntityList() throws Exception {
        assertTransformsEntityIds(ImmutableList.of(entity), List.class);
    }
    
    @Test
    public void testTransformMapWithEntityValueUsingClazz() throws Exception {
        assertTransformsEntityIds(ImmutableMap.of("a", entity), Map.class);
    }
    
    @SuppressWarnings("serial")
    @Test
    public void testTransformMapWithEntityValueUsingTypeToken() throws Exception {
        assertTransformsEntityIds(ImmutableMap.of("a", entity), new TypeToken<Map<String,Entity>>() {});
    }
    
    @SuppressWarnings("serial")
    @Test
    public void testTransformMapWithEntityKey() throws Exception {
        assertTransformsEntityIds(ImmutableMap.of(entity, "a"), new TypeToken<Map<Entity,String>>() {});
    }
    
    private void assertTransformsLocationIds(Object orig, Class<?> type) throws Exception {
        Object transformed = MementoTransformer.transformLocationsToIds(orig);
        Object result = MementoTransformer.transformIdsToLocations(rebindContext, transformed, type, true);
        assertEquals(result, orig, "transformed="+transformed);
    }
    
    private void assertTransformsEntityIds(Object orig, Class<?> type) throws Exception {
        Object transformed = MementoTransformer.transformEntitiesToIds(orig);
        Object result = MementoTransformer.transformIdsToEntities(rebindContext, transformed, type, true);
        assertEquals(result, orig, "transformed="+transformed);
    }
    
    private void assertTransformsEntityIds(Object orig, TypeToken<?> type) throws Exception {
        Object transformed = MementoTransformer.transformEntitiesToIds(orig);
        Object result = MementoTransformer.transformIdsToEntities(rebindContext, transformed, type, true);
        assertEquals(result, orig, "transformed="+transformed);
    }
}
