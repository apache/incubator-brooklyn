package brooklyn.internal.storage.impl;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.AbstractMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import brooklyn.internal.storage.impl.inmemory.InmemoryDatagrid;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.internal.storage.DataGrid;
import brooklyn.internal.storage.Reference;

import com.beust.jcommander.internal.Lists;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class BrooklynStorageImplTest {
    
    private DataGrid datagrid;
    private BrooklynStorage storage;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        // TODO Note that InmemoryDatagrid's ConcurrentMap currently returns snapshot for entrySet() and values()
        // so the tests here aren't particularly good for confirming it'll work against a real datagrid...
        datagrid = new InmemoryDatagrid();
        storage = new BrooklynStorageImpl(datagrid);
    }

    @Test
    public void testReferenceGetAndSet() throws Exception {
        Reference<Object> ref = storage.getReference("mykey");
        assertNull(ref.get());
        assertTrue(ref.isNull());
        assertFalse(ref.contains("different"));
        assertTrue(ref.contains(null));
        
        ref.set("myval");
        assertEqualsCommutative(ref.get(), storage.getReference("mykey").get(), "myval");
        assertFalse(ref.isNull());
        assertFalse(ref.contains("different"));
        assertTrue(ref.contains("myval"));
        
        ref.clear();
        assertNull(ref.get());
    }
    
    @Test
    public void testReferenceAcceptsNullValue() throws Exception {
        Reference<Object> ref = storage.getReference("mykey");
        ref.set(null);
        assertNull(ref.get());
        assertTrue(ref.isNull());
        assertTrue(ref.contains(null));
    }
    
    @Test
    public void testCreateMapReturnsSameEachTime() throws Exception {
        storage.getMap("mykey").put("k1", "v1");
        assertEqualsCommutative(storage.getMap("mykey"), ImmutableMap.of("k1", "v1"));
    }
    
    @Test
    public void testMapOperations() throws Exception {
        Map<Object, Object> map = storage.getMap("mykey");
        
        map.put("k1", "v1");
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of("k1", "v1"));
        assertEqualsCommutative(map.keySet(), storage.getMap("mykey").keySet(), ImmutableSet.of("k1"));
        assertEqualsCommutative(ImmutableList.copyOf(map.values()), ImmutableList.copyOf(storage.getMap("mykey").values()), ImmutableList.of("v1"));
        
        assertEquals(map.size(), 1);
        assertEquals(map.get("k1"), "v1");
        assertTrue(map.containsKey("k1"));
        assertTrue(map.containsValue("v1"));
        assertFalse(map.containsKey("notthere"));
        assertFalse(map.containsValue("notthere"));
        assertFalse(map.isEmpty());
        
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of("k1", "v1"));
        
        map.put("k1", "v2");
        assertEquals(map.get("k1"), "v2");
        assertEquals(map.size(), 1);
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of("k1", "v2"));
        
        map.remove("k1");
        assertTrue(map.isEmpty());
        assertEquals(map.size(), 0);
        assertEquals(map.get("k1"), null);
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of());
        
        map.putAll(ImmutableMap.of("k1", "v3", "k2", "v4"));
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of("k1", "v3", "k2", "v4"));
        
        map.clear();
        assertEquals(map.size(), 0);
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of());
    }
    
    // TODO InmemoryDatagrid's map.entrySet() returns an immutable snapshot
    // Want to test against a real datagrid instead.
    @Test(enabled=false)
    public void testMapEntrySetIterator() throws Exception {
        Map<Object,Object> map = storage.getMap("mykey");
        map.put("k1", "v1");
        assertEquals(iteratorToList(map.entrySet().iterator()), ImmutableList.of(newMapEntry("k1", "v1")));
        
        // Remove entry while iterating; will use snapshot so still contain k1
        Iterator<Map.Entry<Object, Object>> iter1 = map.entrySet().iterator();
        assertTrue(iter1.hasNext());
        map.remove("k1");
        assertEquals(iteratorToList(iter1), ImmutableList.of(newMapEntry("k1", "v1")));
        
        // iter.remove removes value
        map.clear();
        map.put("k1", "v1"); map.put("k2", "v2");
        Iterator<Map.Entry<Object, Object>> iter2 = map.entrySet().iterator();
        assertEquals(newMapEntry("k1", "v1"), iter2.next());
        iter2.remove();
        assertEquals(newMapEntry("k2", "v2"), iter2.next());
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of("k2", "v2"));
        
        // iter.remove when value has already been removed
        map.clear();
        map.put("k1", "v1"); map.put("k2", "v2");
        Iterator<Map.Entry<Object, Object>> iter3 = map.entrySet().iterator();
        assertEquals(newMapEntry("k1", "v1"), iter3.next());
        map.remove("k1");
        iter3.remove();
        assertEquals(newMapEntry("k2", "v2"), iter3.next());
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of("k2", "v2"));
        
        // iter.remove when value has already been removed, but was then re-added!
        // TODO is this really the desired behaviour?
        map.clear();
        map.put("k1", "v1"); map.put("k2", "v2");
        Iterator<Map.Entry<Object, Object>> iter4 = map.entrySet().iterator();
        assertEquals(newMapEntry("k1", "v1"), iter4.next());
        map.remove("k1");
        map.put("k1", "v1b");
        iter4.remove();
        assertEquals(newMapEntry("k2", "v2"), iter4.next());
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of("k2", "v2"));
    }
    
    @Test
    public void testMapKeySetIterator() throws Exception {
        Map<Object,Object> map = storage.getMap("mykey");
        map.put("k1", "v1");
        assertEquals(iteratorToList(map.keySet().iterator()), ImmutableList.of("k1"));
        
        // Remove key while iterating; will use snapshot so still contain k1
        Iterator<Object> iter1 = map.keySet().iterator();
        assertTrue(iter1.hasNext());
        map.remove("k1");
        assertEquals(iteratorToList(iter1), ImmutableList.of("k1"));
        
        // iter.remove removes value
        map.clear();
        map.put("k1", "v1"); map.put("k2", "v2");
        Iterator<Object> iter2 = map.keySet().iterator();
        assertEquals("k1", iter2.next());
        iter2.remove();
        assertEquals("k2", iter2.next());
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of("k2", "v2"));
        
        // iter.remove when value has already been removed
        map.clear();
        map.put("k1", "v1"); map.put("k2", "v2");
        Iterator<Object> iter3 = map.keySet().iterator();
        assertEquals("k1", iter3.next());
        map.remove("k1");
        iter3.remove();
        assertEquals("k2", iter3.next());
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of("k2", "v2"));
        
        // iter.remove when value has already been removed, but was then re-added!
        // TODO is this really the desired behaviour?
        map.clear();
        map.put("k1", "v1"); map.put("k2", "v2");
        Iterator<Object> iter4 = map.keySet().iterator();
        assertEquals("k1", iter4.next());
        map.remove("k1");
        map.put("k1", "v1b");
        iter4.remove();
        assertEquals("k2", iter4.next());
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of("k2", "v2"));
    }
    
    // TODO InmemoryDatagrid.getMap().values() returning snapshot, so iter.remove not supported.
    // Want to test against a real datagrid instead.
    @Test(enabled=false)
    public void testMapValuesIterator() throws Exception {
        Map<Object,Object> map = storage.getMap("mykey");
        map.put("k1", "v1");
        assertEquals(ImmutableList.copyOf(iteratorToList(map.values().iterator())), ImmutableList.of("v1"));
        
        // Remove key while iterating; will use snapshot so still contain k1
        Iterator<Object> iter1 = map.values().iterator();
        assertTrue(iter1.hasNext());
        map.remove("v1");
        assertEquals(ImmutableList.copyOf(iteratorToList(iter1)), ImmutableList.of("v1"));
        
        // iter.remove removes value
        map.clear();
        map.put("k1", "v1"); map.put("k2", "v2");
        Iterator<Object> iter2 = map.values().iterator();
        assertEquals("v1", iter2.next());
        iter2.remove();
        assertEquals("v2", iter2.next());
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of("k2", "v2"));
        
        // iter.remove when value has already been removed
        map.clear();
        map.put("k1", "v1"); map.put("k2", "v2");
        Iterator<Object> iter3 = map.values().iterator();
        assertEquals("v1", iter3.next());
        map.remove("k1");
        iter3.remove();
        assertEquals("v2", iter3.next());
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of("k2", "v2"));
        
        // iter.remove when value has already been removed, but was then re-added!
        // TODO is this really the desired behaviour?
        map.clear();
        map.put("k1", "v1"); map.put("k2", "v2");
        Iterator<Object> iter4 = map.values().iterator();
        assertEquals("v1", iter4.next());
        map.remove("k1");
        map.put("k1", "v1b");
        iter4.remove();
        assertEquals("v2", iter4.next());
        assertEqualsCommutative(map, storage.getMap("mykey"), ImmutableMap.of("k2", "v2"));
    }
    
    private void assertEqualsCommutative(Object o1, Object o2) {
        assertEquals(o1, o2);
        assertEquals(o2, o1);
    }
    
    private void assertEqualsCommutative(Object o1, Object o2, Object o3) {
        assertEquals(o1, o3);
        assertEquals(o3, o1);
        assertEquals(o2, o3);
        assertEquals(o3, o2);
    }
    
    private <T> List<T> iteratorToList(Iterator<T> iter) {
        List<T> result = Lists.newArrayList();
        while (iter.hasNext()) {
            result.add(iter.next());
        }
        return result;
    }
    
    private <K,V> Map.Entry<K,V> newMapEntry(K k, V v) {
        return new AbstractMap.SimpleEntry<K, V>(k, v);
    }
}
