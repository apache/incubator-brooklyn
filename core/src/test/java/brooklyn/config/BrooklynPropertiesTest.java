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
package brooklyn.config;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.NoSuchElementException;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableMap;

@Test
public class BrooklynPropertiesTest {

    @Test
    public void testGetFirst() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty().addFromMap(ImmutableMap.of("akey", "aval", "bkey", "bval"));
        
        assertEquals(props.getFirst("akey"), "aval");
        assertEquals(props.getFirst("akey", "bkey"), "aval");
        assertEquals(props.getFirst("akey", "notThere"), "aval");
               
        assertEquals(props.getFirst("notThere"), null);
        assertEquals(props.getFirst("notThere", "notThere2"), null);
    }

    @Test
    public void testGetFirstUsingFailIfNone() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty().addFromMap(ImmutableMap.of("akey", "aval", "bkey", "bval"));
        assertEquals(props.getFirst(MutableMap.of("failIfNone", true), "akey"), "aval");

        try {
            props.getFirst(MutableMap.of("failIfNone", true), "notThrere");
            fail();
        } catch (NoSuchElementException e) {
            // success
        }
    }

    @Test
    public void testGetFirstUsingFailIfNoneFalse() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty().addFromMap(ImmutableMap.of("akey", "aval", "bkey", "bval"));
        assertEquals(props.getFirst(MutableMap.of("failIfNone", false), "notThrere"), null);
    }

    @Test
    public void testGetFirstUsingDefaultIfNone() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty().addFromMap(ImmutableMap.of("akey", "aval", "bkey", "bval"));
        assertEquals(props.getFirst(MutableMap.of("defaultIfNone", "mydef"), "akey"), "aval");
        assertEquals(props.getFirst(MutableMap.of("defaultIfNone", "mydef"), "notThere"), "mydef");
    }
    
    /*
     * sample.properties:
     *   P1=Property 1
     *   P2=Property 2
     * 
     * more-sample.properties:
     *   P3=Property 3
     *   P1=Property 1 v2
     * 
     * tricky.properties:
     *   P1=Property 1 v3
     *   P1=Property 1 v4
     *   a.b.c=d.e.f
     *   a=b=c
     *   aa=$a
     */
    
    @Test
    public void testAddFromUrlSimple() {
        BrooklynProperties p1 = BrooklynProperties.Factory.newEmpty().addFromUrl("brooklyn/config/sample.properties");
        assertForSample(p1);
    }
    
    @Test
    public void testAddFromUrlClasspath() {
        BrooklynProperties p1 = BrooklynProperties.Factory.newEmpty().addFromUrl("classpath://brooklyn/config/sample.properties");
        assertForSample(p1);
    }

    @Test
    public void testAddMultipleFromUrl() {
        BrooklynProperties p1 = BrooklynProperties.Factory.newEmpty().
                addFromUrl("brooklyn/config/sample.properties").
                addFromUrl("brooklyn/config/more-sample.properties");
        assertForMoreSample(p1);
    }

//            P1=Property 1 v3
//            P1=Property 1 v4
//            a.b.c=d.e.f
//            a=b=c
//            aa=$a

    @Test
    public void testTrickyAddMultipleFromUrl() {
        BrooklynProperties p1 = BrooklynProperties.Factory.newEmpty().
                addFromUrl("brooklyn/config/sample.properties").
                addFromUrl("brooklyn/config/tricky.properties");
        assertForSampleAndTricky(p1);
    }

    private void assertForSample(BrooklynProperties p) {
        Assert.assertEquals(p.getFirst("P1"), "Property 1");
        Assert.assertEquals(p.getFirst("P2"), "Property 2");
        Assert.assertEquals(p.getFirst("P0", "P3"), null);
    }
    
    private void assertForMoreSample(BrooklynProperties p) {
        Assert.assertEquals(p.getFirst("P1"), "Property 1 v2");
        Assert.assertEquals(p.getFirst("P2"), "Property 2");
        Assert.assertEquals(p.getFirst("P0", "P3"), "Property 3");
    }
    
    private void assertForSampleAndTricky(BrooklynProperties p) {
        Assert.assertEquals(p.getFirst("P1"), "Property 1 v4");
        Assert.assertEquals(p.getFirst("P2"), "Property 2");
        Assert.assertEquals(p.getFirst("P0", "P3"), null);
        Assert.assertEquals(p.getFirst("a.b.c"), "d.e.f");
        Assert.assertEquals(p.getFirst("a"), "b=c");
        Assert.assertEquals(p.getFirst("aa"), "$a");
    }
    
    @Test
    public void testGetSubMap() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty().addFromMap(ImmutableMap.of(
                "a.key", "aval", "a.key2", "aval2", "akey", "noval", "b.key", "bval"));
        BrooklynProperties p2 = props.submap(ConfigPredicates.matchingGlob("a.*"));
        assertEquals(p2.getAllConfig().keySet().size(), 2, "wrong size submap: "+p2);
        assertEquals(p2.getFirst("a.key"), "aval");
        assertEquals(p2.getFirst("b.key"), null);
        assertEquals(p2.getFirst("akey"), null);
        
        BrooklynProperties p3a = props.submap(ConfigPredicates.startingWith("a."));
        assertEquals(p3a, p2);
        BrooklynProperties p3b = props.submap(ConfigPredicates.matchingRegex("a\\..*"));
        assertEquals(p3b, p2);
        
        BrooklynProperties p4 = props.submap(ConfigPredicates.matchingRegex("a.*"));
        assertEquals(p4.getAllConfig().keySet().size(), 3, "wrong size submap: "+p4);
        assertEquals(p4.getFirst("a.key"), "aval");
        assertEquals(p4.getFirst("b.key"), null);
        assertEquals(p4.getFirst("akey"), "noval");
    }

    @Test
    public void testGetAndPutConfig() {
        BrooklynProperties props = BrooklynProperties.Factory.newEmpty().addFromMap(ImmutableMap.of(
                "a.key", "aval", "a.key2", "aval2", "akey", "noval", "n.key", "234"));
        
        BasicConfigKey<String> aString = new BasicConfigKey<String>(String.class, "a.key");
        BasicConfigKey<Integer> nNum = new BasicConfigKey<Integer>(Integer.class, "n.key");
        BasicConfigKey<Integer> aBsent = new BasicConfigKey<Integer>(Integer.class, "ab.sent");
        BasicConfigKey<Integer> aMisstyped = new BasicConfigKey<Integer>(Integer.class, "am.isstyped");
        BasicConfigKey<Integer> aDfault = new BasicConfigKey<Integer>(Integer.class, "a.default", "-", 123);
        
        assertEquals(props.getConfig(aString), "aval");
        assertEquals(props.getConfig(nNum), (Integer)234);
        
        props.put(aString, "aval2");
        assertEquals(props.getConfig(aString), "aval2");
        assertEquals(props.get("a.key"), "aval2");

        props.put(nNum, "345");
        assertEquals(props.getConfig(nNum), (Integer)345);
        assertEquals(props.get("n.key"), "345");

        assertEquals(props.getConfig(aBsent), null);
        assertEquals(props.getConfig(aBsent, 123), (Integer)123);
        assertEquals(props.getConfig(aDfault), (Integer)123);
        
        props.put(aMisstyped, "x1");
        assertEquals(props.get("am.isstyped"), "x1");
        boolean workedWhenShouldntHave = false;
        try { props.getConfig(aMisstyped); workedWhenShouldntHave = true; } catch (Exception e) {}
        if (workedWhenShouldntHave) fail("should have failed getting "+aMisstyped+" because can't coerce");
    }

}
