/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.util.collections;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.collections.Jsonya.Navigator;

import com.google.common.collect.ImmutableSet;

public class JsonyaTest {
    
    public static Navigator<MutableMap<Object,Object>> europeMap() {
        return Jsonya.newInstance().at("europe", "uk", "edinburgh")
                .put("population", 500*1000)
                .put("weather", "wet", "lighting", "dark")
                .root().at("europe").at("france").put("population", 80*1000*1000)
                .root();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testJsonyaMapNew() {
        MutableMap<Object, Object> m = europeMap().getRootMap();
        
        Assert.assertEquals(Jsonya.of(m).get("europe", "uk", "edinburgh", "population"), 500*1000);
        Assert.assertEquals(Jsonya.of(m).at("europe", "uk", "edinburgh", "population").get(), 500*1000);
        Assert.assertEquals(((Map<Object,Object>)Jsonya.of(m).get("europe")).keySet(), ImmutableSet.of("uk", "france"));
        Assert.assertEquals(Jsonya.of(m).at("europe").getFocusMap().keySet(), ImmutableSet.of("uk", "france"));
    }
    
    @SuppressWarnings("rawtypes")
    @Test
    public void testJsonyaMapExistingAndRootModification() {
        Navigator<MutableMap<Object, Object>> n = Jsonya.of(europeMap().getRootMap()).at("asia")
            .put(MutableMap.of("china", null))
            .put(MutableMap.of("japan", null));
        
        Assert.assertTrue( n.root().at("asia").get(Map.class).containsKey("china") );
        Assert.assertTrue( ((Map)n.root().get("asia")).containsKey("japan") );
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testJsonyaWithList() {
        Navigator<MutableMap<Object, Object>> n = europeMap();
        n.at("europe", "uk", "neighbours").list().add("ireland")
            .root().at("europe", "france", "neighbours").list().add("spain", "germany").add("switzerland")
            .root().at("europe", "france", "neighbours").add("lux");
        Object l = n.root().at("europe", "france", "neighbours").get();
        Assert.assertTrue(l instanceof List);
        Assert.assertEquals(((List)l).size(), 4);
        // this wants a map, so it creates a map in the list
        n.put("east", "germany", "south", "spain");
        Assert.assertEquals(((List)l).size(), 5);
        Map nd = (Map) ((List)l).get(4);
        Assert.assertEquals(nd.size(), 2);
        Map nd2 = (Map) n.root().get("europe", "france", "neighbours", 4);
        Assert.assertEquals(nd2.size(), 2);
    }
    
    @SuppressWarnings("rawtypes")
    @Test
    public void testCreateMapInList1() {
        MutableMap<Object, Object> map = Jsonya.at("countries").list().map().add("europe", "uk").getRootMap();
        List l = (List)map.get("countries");
        Assert.assertEquals( ((Map)l.get(0)).get("europe"), "uk" );
    }
    @SuppressWarnings("rawtypes")
    @Test
    public void testCreateMapInList2() {
        MutableMap<Object, Object> map = Jsonya.at("countries").list().map().add("europe", "uk")
            .root().at("countries").add("antarctica")
            .root().at("countries").map().add("asia", (Object)null)
                .at("asia").list().add("china", "japan").getRootMap();
        
        List l = (List)map.get("countries");
        Assert.assertEquals( ((Map)l.get(0)).get("europe"), "uk" );
    }
    
    @Test
    public void testJsonyaDeepSimple() {
        Navigator<MutableMap<Object, Object>> n = Jsonya.of(europeMap())
                .at("europe").add("spain", "plains");
        Assert.assertEquals( n.root().get("europe", "spain"), "plains" );
        Assert.assertEquals( n.getRootMap().size(), 1 );
        Assert.assertEquals( n.root().at("europe").getFocusMap().size(), 3 );
    }
    
    @Test(expectedExceptions=Exception.class)
    public void testJsonyaDeepSimpleFailure() {
        Jsonya.of(europeMap()).at("euroope").add("spain");
    }

    @Test
    public void testJsonyaDeepMoreComplicated() {
        Navigator<MutableMap<Object, Object>> n = Jsonya.of(europeMap()).at("asia")
            .list().add("china", "japan")
            .root().add( Jsonya.newInstance().at("europe", "uk", "glasgow").put("weather", "even wetter").getRootMap() );
        
        Assert.assertEquals( n.getRootMap().size(), 2 );
        Assert.assertTrue( n.root().at("asia").get(List.class).contains("china") );
        Assert.assertTrue( ((List<?>)n.root().get("asia")).contains("japan") );
        
        Assert.assertEquals(n.root().at("europe", "uk").get(Map.class).size(), 2);
        Assert.assertEquals(n.root().at("europe", "uk", "edinburgh", "weather").get(), "wet");
        Assert.assertEquals(n.root().at("europe", "uk", "glasgow", "weather").get(), "even wetter");
    }
    
    @Test
    public void testJsonyaToString() {
        Assert.assertEquals(europeMap().toString(), 
            "{ \"europe\": { \"uk\": { \"edinburgh\": { \"population\": 500000, \"weather\": \"wet\", \"lighting\": \"dark\" } },"
            + " \"france\": { \"population\": 80000000 } } }");
    }

    @Test
    public void testPrimitivedAndLiteralledMap() {
        Object foo = new Object() {
            public String toString() { return "FOO"; }
        };
        
        MutableMap<Object, Object> map = MutableMap.<Object,Object>of("a", 1, 2, Arrays.<Object>asList(true, 8, "8"), 'C', foo);
        
        Map<Object, Object> mapL = Jsonya.newInstanceLiteral().put(map).getRootMap();
        Assert.assertEquals(mapL, map);
        Assert.assertEquals(mapL.get('C'), foo);
        
        Map<Object, Object> mapP = Jsonya.newInstancePrimitive().put(map).getRootMap();
        Assert.assertNotEquals(mapP, map);
        Assert.assertEquals(mapP.get('C'), foo.toString());
        Assert.assertEquals(MutableMap.copyOf(mapP).add('C', null), MutableMap.copyOf(map).add('C', null));
    }

    @Test
    public void testJsonyaBadPathNull() {
        Navigator<MutableMap<Object, Object>> m = europeMap();
        // does not create (but if we 'pushed' it would)
        Assert.assertNull( m.at("europe",  "spain", "barcelona").get() );
        Assert.assertNull( m.root().at("europe").at("spain").at("barcelona").get() );
    }
    @Test
    public void testJsonyaMaybe() {
        Navigator<MutableMap<Object, Object>> m = europeMap();
        Assert.assertEquals( m.at("europe",  "spain", "barcelona").getMaybe().or("norealabc"), "norealabc" );
        Assert.assertEquals(m.root().at("europe").getFocusMap().keySet(), MutableSet.of("uk", "france"));
    }
    
    @Test
    public void testJsonyaPushPop() {
        Navigator<MutableMap<Object, Object>> m = europeMap();
        Assert.assertTrue(m.getFocusMap().containsKey("europe"));
        Assert.assertFalse(m.getFocusMap().containsKey("edinburgh"));
        m.push();
        
        m.at("europe", "uk");
        Assert.assertTrue(m.getFocusMap().containsKey("edinburgh"));
        Assert.assertFalse(m.getFocusMap().containsKey("europe"));
        
        m.pop();
        Assert.assertTrue(m.getFocusMap().containsKey("europe"));
        Assert.assertFalse(m.getFocusMap().containsKey("edinburgh"));

        // also check 'get' does not change focus
        m.get("europe", "uk");
        Assert.assertTrue(m.getFocusMap().containsKey("europe"));
        Assert.assertFalse(m.getFocusMap().containsKey("edinburgh"));
    }

}
