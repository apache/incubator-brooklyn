package brooklyn.entity.rebind.persister;

import static org.testng.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

public class XmlMementoSerializerTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(XmlMementoSerializerTest.class);

    private XmlMementoSerializer<Object> serializer;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        serializer = new XmlMementoSerializer<Object>(XmlMementoSerializerTest.class.getClassLoader());
    }

    @Test
    public void testMutableSet() throws Exception {
        Set<?> obj = MutableSet.of("123");
        assertSerializeAndDeserialize(obj);
    }

    @Test
    public void testLinkedHashSet() throws Exception {
        Set<String> obj = new LinkedHashSet<String>();
        obj.add("123");
        assertSerializeAndDeserialize(obj);
    }

    @Test
    public void testImmutableSet() throws Exception {
        Set<String> obj = ImmutableSet.of("123");
        assertSerializeAndDeserialize(obj);
    }

    @Test
    public void testMutableList() throws Exception {
        List<?> obj = MutableList.of("123");
        assertSerializeAndDeserialize(obj);
    }

    @Test
    public void testLinkedList() throws Exception {
        List<String> obj = new LinkedList<String>();
        obj.add("123");
        assertSerializeAndDeserialize(obj);
    }

    @Test
    public void testImmutableList() throws Exception {
        List<String> obj = ImmutableList.of("123");
        assertSerializeAndDeserialize(obj);
    }

    @Test
    public void testMutableMap() throws Exception {
        Map<?,?> obj = MutableMap.of("mykey", "myval");
        assertSerializeAndDeserialize(obj);
    }

    @Test
    public void testLinkedHashMap() throws Exception {
        Map<String,String> obj = new LinkedHashMap<String,String>();
        obj.put("mykey", "myval");
        assertSerializeAndDeserialize(obj);
    }

    @Test
    public void testImmutableMap() throws Exception {
        Map<?,?> obj = ImmutableMap.of("mykey", "myval");
        assertSerializeAndDeserialize(obj);
    }

    @Test
    public void testEntity() throws Exception {
        final TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class);
        ManagementContext managementContext = app.getManagementContext();
        try {
            serializer.setLookupContext(new LookupContextImpl(ImmutableList.of(app), ImmutableList.<Location>of(), ImmutableList.<Policy>of(), ImmutableList.<Enricher>of()));
            assertSerializeAndDeserialize(app);
        } finally {
            Entities.destroyAll(managementContext);
        }
    }

    @Test
    public void testLocation() throws Exception {
        TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class);
        ManagementContext managementContext = app.getManagementContext();
        try {
            @SuppressWarnings("deprecation")
            final Location loc = managementContext.getLocationManager().createLocation(LocationSpec.create(brooklyn.location.basic.SimulatedLocation.class));
            serializer.setLookupContext(new LookupContextImpl(ImmutableList.<Entity>of(), ImmutableList.of(loc), ImmutableList.<Policy>of(), ImmutableList.<Enricher>of()));
            assertSerializeAndDeserialize(loc);
        } finally {
            Entities.destroyAll(managementContext);
        }
    }

    @Test
    public void testFieldReffingEntity() throws Exception {
        final TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class);
        ReffingEntity reffer = new ReffingEntity(app);
        ManagementContext managementContext = app.getManagementContext();
        try {
            serializer.setLookupContext(new LookupContextImpl(ImmutableList.of(app), ImmutableList.<Location>of(), ImmutableList.<Policy>of(), ImmutableList.<Enricher>of()));
            ReffingEntity reffer2 = assertSerializeAndDeserialize(reffer);
            assertEquals(reffer2.entity, app);
        } finally {
            Entities.destroyAll(managementContext);
        }
    }

    @Test
    public void testUntypedFieldReffingEntity() throws Exception {
        final TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class);
        ReffingEntity reffer = new ReffingEntity((Object)app);
        ManagementContext managementContext = app.getManagementContext();
        try {
            serializer.setLookupContext(new LookupContextImpl(ImmutableList.of(app), ImmutableList.<Location>of(), ImmutableList.<Policy>of(), ImmutableList.<Enricher>of()));
            ReffingEntity reffer2 = assertSerializeAndDeserialize(reffer);
            assertEquals(reffer2.obj, app);
        } finally {
            Entities.destroyAll(managementContext);
        }
    }

    public static class ReffingEntity {
        public Entity entity;
        public Object obj;
        public ReffingEntity(Entity entity) {
            this.entity = entity;
        }
        public ReffingEntity(Object obj) {
            this.obj = obj;
        }
        @Override
        public boolean equals(Object o) {
            return (o instanceof ReffingEntity) && Objects.equal(entity, ((ReffingEntity)o).entity) && Objects.equal(obj, ((ReffingEntity)o).obj);
        }
        @Override
        public int hashCode() {
            return Objects.hashCode(entity, obj);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T assertSerializeAndDeserialize(T obj) throws Exception {
        String serializedForm = serializer.toString(obj);
        System.out.println("serializedForm="+serializedForm);
        Object deserialized = serializer.fromString(serializedForm);
        assertEquals(deserialized, obj, "serializedForm="+serializedForm);
        return (T) deserialized;
    }

    static class LookupContextImpl implements LookupContext {
        private final Map<String, Entity> entities;
        private final Map<String, Location> locations;
        private final Map<String, Policy> policies;
        private final Map<String, Enricher> enrichers;

        LookupContextImpl(Iterable<? extends Entity> entities, Iterable<? extends Location> locations,
                Iterable<? extends Policy> policies, Iterable<? extends Enricher> enrichers) {
            this.entities = Maps.newLinkedHashMap();
            this.locations = Maps.newLinkedHashMap();
            this.policies = Maps.newLinkedHashMap();
            this.enrichers = Maps.newLinkedHashMap();
            for (Entity entity : entities) this.entities.put(entity.getId(), entity);
            for (Location location : locations) this.locations.put(location.getId(), location);
            for (Policy policy : policies) this.policies.put(policy.getId(), policy);
            for (Enricher enricher : enrichers) this.enrichers.put(enricher.getId(), enricher);
        }
        LookupContextImpl(Map<String,? extends Entity> entities, Map<String,? extends Location> locations,
                Map<String,? extends Policy> policies, Map<String,? extends Enricher> enrichers) {
            this.entities = ImmutableMap.copyOf(entities);
            this.locations = ImmutableMap.copyOf(locations);
            this.policies = ImmutableMap.copyOf(policies);
            this.enrichers = ImmutableMap.copyOf(enrichers);
        }
        @Override public Entity lookupEntity(String id) {
            if (entities.containsKey(id)) {
                return entities.get(id);
            }
            throw new NoSuchElementException("no entity with id "+id+"; contenders are "+entities.keySet());
        }
        @Override public Location lookupLocation(String id) {
            if (locations.containsKey(id)) {
                return locations.get(id);
            }
            throw new NoSuchElementException("no location with id "+id+"; contenders are "+locations.keySet());
        }
        @Override public Policy lookupPolicy(String id) {
            if (policies.containsKey(id)) {
                return policies.get(id);
            }
            throw new NoSuchElementException("no policy with id "+id+"; contenders are "+policies.keySet());
        }
        @Override public Enricher lookupEnricher(String id) {
            if (enrichers.containsKey(id)) {
                return enrichers.get(id);
            }
            throw new NoSuchElementException("no enricher with id "+id+"; contenders are "+enrichers.keySet());
        }
    };
}
