package brooklyn.internal.storage.impl;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        datagrid = new PseudoDatagrid();
        storage = new BrooklynStorageImpl(datagrid);
    }

    @Test
    public void testSimpleGetAndPut() throws Exception {
        assertNull(storage.get("mykey"));
        storage.put("mykey", "myval");
        assertEquals(storage.get("mykey"), "myval");
    }
    
    @Test
    public void testReferenceGetAndSet() throws Exception {
        Reference<Object> ref = storage.createReference("mykey");
        assertNull(ref.get());
        ref.set("myval");
        assertEqualsCommutative(ref.get(), storage.createReference("mykey").get(), "myval");
    }
    
    @Test
    public void testCreateMapReturnsSameEachTime() throws Exception {
        storage.createMap("mykey").put("k1", "v1");
        assertEqualsCommutative(storage.createMap("mykey"), ImmutableMap.of("k1", "v1"));
    }
    
    @Test
    public void testCreateSetReturnsSameEachTime() throws Exception {
        storage.createSet("mykey").add("k1");
        assertEqualsCommutative(storage.createSet("mykey"), ImmutableSet.of("k1"));
    }
    
    @Test
    public void testMapOperations() throws Exception {
        Map<Object, Object> map = storage.createMap("mykey");
        
        map.put("k1", "v1");
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of("k1", "v1"));
        assertEqualsCommutative(map.keySet(), storage.createMap("mykey").keySet(), ImmutableSet.of("k1"));
        assertEqualsCommutative(ImmutableList.copyOf(map.values()), ImmutableList.copyOf(storage.createMap("mykey").values()), ImmutableList.of("v1"));
        
        assertEquals(map.size(), 1);
        assertEquals(map.get("k1"), "v1");
        assertTrue(map.containsKey("k1"));
        assertTrue(map.containsValue("v1"));
        assertFalse(map.containsKey("notthere"));
        assertFalse(map.containsValue("notthere"));
        assertFalse(map.isEmpty());
        
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of("k1", "v1"));
        
        map.put("k1", "v2");
        assertEquals(map.get("k1"), "v2");
        assertEquals(map.size(), 1);
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of("k1", "v2"));
        
        map.remove("k1");
        assertTrue(map.isEmpty());
        assertEquals(map.size(), 0);
        assertEquals(map.get("k1"), null);
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of());
        
        map.putAll(ImmutableMap.of("k1", "v3", "k2", "v4"));
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of("k1", "v3", "k2", "v4"));
        
        map.clear();
        assertEquals(map.size(), 0);
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of());
    }
    
    @Test
    public void testSetOperations() throws Exception {
        Set<Object> set = storage.createSet("mykey");
        
        set.add("k1");
        assertEqualsCommutative(set, storage.createSet("mykey"), ImmutableSet.of("k1"));
        
        assertEquals(set.size(), 1);
        assertTrue(set.contains("k1"));
        assertFalse(set.contains("notthere"));
        assertFalse(set.isEmpty());
        assertEquals(Arrays.asList(set.toArray()), ImmutableList.of("k1"));
        assertEqualsCommutative(set, storage.createSet("mykey"), ImmutableSet.of("k1"));
        
        set.add("k1");
        assertEquals(set.size(), 1);
        assertEqualsCommutative(set, storage.createSet("mykey"), ImmutableSet.of("k1"));
        
        set.remove("k1");
        assertTrue(set.isEmpty());
        assertEquals(set.size(), 0);
        assertFalse(set.contains("k1"));
        assertEqualsCommutative(set, storage.createSet("mykey"), ImmutableSet.of());
        
        set.addAll(ImmutableList.of("k2", "k3"));
        assertEqualsCommutative(set, storage.createSet("mykey"), ImmutableSet.of("k2", "k3"));
        
        set.clear();
        assertEquals(set.size(), 0);
        assertEqualsCommutative(set, storage.createSet("mykey"), ImmutableSet.of());
        
        set.add("k3");
        set.removeAll(ImmutableList.of("k3"));
        assertEqualsCommutative(set, storage.createSet("mykey"), ImmutableSet.of());
        
        set.add("k4");
        set.retainAll(ImmutableList.of("k4"));
        assertEqualsCommutative(set, storage.createSet("mykey"), ImmutableSet.of("k4"));
        
        set.retainAll(ImmutableList.of("different"));
        assertEqualsCommutative(set, storage.createSet("mykey"), ImmutableSet.of());
    }
    
    @Test
    public void testSetIterator() throws Exception {
        Set<Object> set = storage.createSet("mykey");
        set.add("k1");
        assertEquals(iteratorToList(set.iterator()), ImmutableList.of("k1"));
        
        // Remove entry while iterating; will use snapshot so still contain k1
        Iterator<Object> iter1 = set.iterator();
        assertTrue(iter1.hasNext());
        set.remove("k1");
        assertEquals(iteratorToList(iter1), ImmutableList.of("k1"));
        
        // iter.remove removes value
        set.clear();
        set.addAll(ImmutableList.of("k1", "k2"));
        Iterator<Object> iter2 = set.iterator();
        assertEquals("k1", iter2.next());
        iter2.remove();
        assertEquals("k2", iter2.next());
        assertEqualsCommutative(set, storage.createSet("mykey"), ImmutableSet.of("k2"));
        
        // iter.remove when value has already been removed
        set.clear();
        set.addAll(ImmutableList.of("k1", "k2"));
        Iterator<Object> iter3 = set.iterator();
        assertEquals("k1", iter3.next());
        set.remove("k1");
        iter3.remove();
        assertEquals("k2", iter3.next());
        assertEqualsCommutative(set, storage.createSet("mykey"), ImmutableSet.of("k2"));
        
        // iter.remove when value has already been removed, but was then re-added!
        // TODO is this really the desired behaviour?
        set.clear();
        set.addAll(ImmutableList.of("k1", "k2"));
        Iterator<Object> iter4 = set.iterator();
        assertEquals("k1", iter4.next());
        set.remove("k1");
        set.add("k1");
        iter4.remove();
        assertEquals("k2", iter4.next());
        assertEqualsCommutative(set, storage.createSet("mykey"), ImmutableSet.of("k2"));
    }
    
    @Test
    public void testMapEntrySetIterator() throws Exception {
        Map<Object,Object> map = storage.createMap("mykey");
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
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of("k2", "v2"));
        
        // iter.remove when value has already been removed
        map.clear();
        map.put("k1", "v1"); map.put("k2", "v2");
        Iterator<Map.Entry<Object, Object>> iter3 = map.entrySet().iterator();
        assertEquals(newMapEntry("k1", "v1"), iter3.next());
        map.remove("k1");
        iter3.remove();
        assertEquals(newMapEntry("k2", "v2"), iter3.next());
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of("k2", "v2"));
        
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
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of("k2", "v2"));
    }
    
    @Test
    public void testMapKeySetIterator() throws Exception {
        Map<Object,Object> map = storage.createMap("mykey");
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
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of("k2", "v2"));
        
        // iter.remove when value has already been removed
        map.clear();
        map.put("k1", "v1"); map.put("k2", "v2");
        Iterator<Object> iter3 = map.keySet().iterator();
        assertEquals("k1", iter3.next());
        map.remove("k1");
        iter3.remove();
        assertEquals("k2", iter3.next());
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of("k2", "v2"));
        
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
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of("k2", "v2"));
    }
    
    // TODO Not yet supporting live view of map.values()
    @Test(enabled=false)
    public void testMapValuesIterator() throws Exception {
        Map<Object,Object> map = storage.createMap("mykey");
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
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of("k2", "v2"));
        
        // iter.remove when value has already been removed
        map.clear();
        map.put("k1", "v1"); map.put("k2", "v2");
        Iterator<Object> iter3 = map.values().iterator();
        assertEquals("v1", iter3.next());
        map.remove("k1");
        iter3.remove();
        assertEquals("v2", iter3.next());
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of("k2", "v2"));
        
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
        assertEqualsCommutative(map, storage.createMap("mykey"), ImmutableMap.of("k2", "v2"));
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
