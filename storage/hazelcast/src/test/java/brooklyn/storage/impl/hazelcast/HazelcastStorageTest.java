package brooklyn.storage.impl.hazelcast;

import brooklyn.config.BrooklynProperties;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.internal.storage.Reference;
import brooklyn.internal.storage.impl.hazelcast.HazelcastBrooklynStorageFactory;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.collections.MutableList;
import com.hazelcast.core.Hazelcast;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

public class HazelcastStorageTest {

    private LocalManagementContext managementContext;
    private BrooklynStorage storage;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        managementContext = new LocalManagementContext(BrooklynProperties.Factory.newDefault(),
                new HazelcastBrooklynStorageFactory());
        storage = managementContext.getStorage();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        if (managementContext != null) managementContext.terminate();
        Hazelcast.shutdownAll();
    }

    @Test
    public void testGetMap() {
        Map<String,String> map = storage.getMap("somemap");
        map.put("foo", "bar");
        assertEquals( map.get("foo"),"bar");
    }

    @Test
    public void testGetReference() {
        Reference<String> ref = storage.getReference("someReference");
        ref.set("bar");
        assertEquals(ref.get(), "bar");
    }

    @Test
    public void testNonConcurrentList(){
        Reference<List<String>> ref = storage.getNonConcurrentList("someReference");
        ref.set(MutableList.of("bar"));

        assertEquals(ref.get().get(0),"bar");
    }

    @Test
    public void removeReference(){
        Reference<String> ref = storage.getReference("someReference");
        ref.set("bar");
        storage.remove("someReference");
        assertEquals(ref.get(), null);
    }


    @Test
    public void removeMap(){
        Map<String,String> map = storage.getMap("somemap");
        map.put("foo", "bar");
        storage.remove("somemap");
        assertEquals(null, map.get("foo"));
    }
}
