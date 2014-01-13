package brooklyn.entity.rebind.persister;

import static org.testng.Assert.assertEquals;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class XmlMementoSerializerTest {

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
        TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class);
        ManagementContext managementContext = app.getManagementContext();
        try {
            serializer = new XmlMementoSerializer<Object>(XmlMementoSerializerTest.class.getClassLoader(), managementContext);
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
            Location loc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
            serializer = new XmlMementoSerializer<Object>(XmlMementoSerializerTest.class.getClassLoader(), managementContext);
            assertSerializeAndDeserialize(loc);
        } finally {
            Entities.destroyAll(managementContext);
        }
    }
    
    private void assertSerializeAndDeserialize(Object obj) throws Exception {
        String serializedForm = serializer.toString(obj);
        System.out.println("serializedForm="+serializedForm);
        Object deserialized = serializer.fromString(serializedForm);
        assertEquals(deserialized, obj, "serializedForm="+serializedForm);
    }
}
