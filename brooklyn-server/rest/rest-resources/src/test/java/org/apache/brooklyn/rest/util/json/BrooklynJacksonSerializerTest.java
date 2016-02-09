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
package org.apache.brooklyn.rest.util.json;

import java.io.NotSerializableException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

public class BrooklynJacksonSerializerTest {

    private static final Logger log = LoggerFactory.getLogger(BrooklynJacksonSerializerTest.class);
    
    public static class SillyClassWithManagementContext {
        @JsonProperty
        ManagementContext mgmt;
        @JsonProperty
        String id;
        
        public SillyClassWithManagementContext() { }
        
        public SillyClassWithManagementContext(String id, ManagementContext mgmt) {
            this.id = id;
            this.mgmt = mgmt;
        }

        @Override
        public String toString() {
            return super.toString()+"[id="+id+";mgmt="+mgmt+"]";
        }
    }

    @Test
    public void testCustomSerializerWithSerializableSillyManagementExample() throws Exception {
        ManagementContext mgmt = LocalManagementContextForTests.newInstance();
        try {

            ObjectMapper mapper = BrooklynJacksonJsonProvider.newPrivateObjectMapper(mgmt);

            SillyClassWithManagementContext silly = new SillyClassWithManagementContext("123", mgmt);
            log.info("silly is: "+silly);

            String sillyS = mapper.writeValueAsString(silly);

            log.info("silly json is: "+sillyS);

            SillyClassWithManagementContext silly2 = mapper.readValue(sillyS, SillyClassWithManagementContext.class);
            log.info("silly2 is: "+silly2);

            Assert.assertEquals(silly.id, silly2.id);
            
        } finally {
            Entities.destroyAll(mgmt);
        }
    }
    
    public static class SelfRefNonSerializableClass {
        @JsonProperty
        Object bogus = this;
    }

    @Test
    public void testSelfReferenceFailsWhenStrict() {
        checkNonSerializableWhenStrict(new SelfRefNonSerializableClass());
    }
    @Test
    public void testSelfReferenceGeneratesErrorMapObject() throws Exception {
        checkSerializesAsMapWithErrorAndToString(new SelfRefNonSerializableClass());
    }
    @Test
    public void testNonSerializableInListIsShownInList() throws Exception {
        List<?> result = checkSerializesAs(MutableList.of(1, new SelfRefNonSerializableClass()), List.class);
        Assert.assertEquals( result.get(0), 1 );
        Assert.assertEquals( ((Map<?,?>)result.get(1)).get("errorType"), NotSerializableException.class.getName() );
    }
    @Test
    public void testNonSerializableInMapIsShownInMap() throws Exception {
        Map<?,?> result = checkSerializesAs(MutableMap.of("x", new SelfRefNonSerializableClass()), Map.class);
        Assert.assertEquals( ((Map<?,?>)result.get("x")).get("errorType"), NotSerializableException.class.getName() );
    }
    static class TupleWithNonSerializable {
        String good = "bon";
        SelfRefNonSerializableClass bad = new SelfRefNonSerializableClass();
    }
    @Test
    public void testNonSerializableInObjectIsShownInMap() throws Exception {
        String resultS = checkSerializesAs(new TupleWithNonSerializable(), null);
        log.info("nested non-serializable json is "+resultS);
        Assert.assertTrue(resultS.startsWith("{\"good\":\"bon\",\"bad\":{"), "expected a nested map for the error field, not "+resultS);
        
        Map<?,?> result = checkSerializesAs(new TupleWithNonSerializable(), Map.class);
        Assert.assertEquals( result.get("good"), "bon" );
        Assert.assertTrue( result.containsKey("bad"), "Should have had a key for field 'bad'" );
        Assert.assertEquals( ((Map<?,?>)result.get("bad")).get("errorType"), NotSerializableException.class.getName() );
    }
    
    public static class EmptyClass {
    }

    @Test
    public void testEmptySerializesAsEmpty() throws Exception {
        // deliberately, a class with no fields and no annotations serializes as an error,
        // because the alternative, {}, is useless.  however if it *is* annotated, as below, then it will serialize fine.
        checkSerializesAsMapWithErrorAndToString(new SelfRefNonSerializableClass());
    }
    @Test
    public void testEmptyNonSerializableFailsWhenStrict() {
        checkNonSerializableWhenStrict(new EmptyClass());
    }

    @JsonSerialize
    public static class EmptyClassWithSerialize {
    }

    @Test
    public void testEmptyAnnotatedSerializesAsEmptyEvenWhenStrict() throws Exception {
        try {
            BidiSerialization.setStrictSerialization(true);
            testEmptyAnnotatedSerializesAsEmpty();
        } finally {
            BidiSerialization.clearStrictSerialization();
        }
    }
    
    @Test
    public void testEmptyAnnotatedSerializesAsEmpty() throws Exception {
        Map<?, ?> map = checkSerializesAs( new EmptyClassWithSerialize(), Map.class );
        Assert.assertTrue(map.isEmpty(), "Expected an empty map; instead got: "+map);

        String result = checkSerializesAs( MutableList.of(new EmptyClassWithSerialize()), null );
        result = result.replaceAll(" ", "").trim();
        Assert.assertEquals(result, "[{}]");
    }

    @Test
    public void testSensorFailsWhenStrict() {
        checkNonSerializableWhenStrict(MutableList.of(Attributes.HTTP_PORT));
    }
    @Test
    public void testSensorSensible() throws Exception {
        Map<?,?> result = checkSerializesAs(Attributes.HTTP_PORT, Map.class);
        log.info("SENSOR json is: "+result);
        Assert.assertFalse(result.toString().contains("error"), "Shouldn't have had an error, instead got: "+result);
    }

    @Test
    public void testLinkedListSerialization() throws Exception {
        LinkedList<Object> ll = new LinkedList<Object>();
        ll.add(1); ll.add("two");
        String result = checkSerializesAs(ll, null);
        log.info("LLIST json is: "+result);
        Assert.assertFalse(result.contains("error"), "Shouldn't have had an error, instead got: "+result);
        Assert.assertEquals(Strings.collapseWhitespace(result, ""), "[1,\"two\"]");
    }

    @Test
    public void testMultiMapSerialization() throws Exception {
        Multimap<String, Integer> m = MultimapBuilder.hashKeys().arrayListValues().build();
        m.put("bob", 24);
        m.put("bob", 25);
        String result = checkSerializesAs(m, null);
        log.info("multimap serialized as: " + result);
        Assert.assertFalse(result.contains("error"), "Shouldn't have had an error, instead got: "+result);
        Assert.assertEquals(Strings.collapseWhitespace(result, ""), "{\"bob\":[24,25]}");
    }

    @Test
    public void testSupplierSerialization() throws Exception {
        String result = checkSerializesAs(Strings.toStringSupplier(Streams.byteArrayOfString("x")), null);
        log.info("SUPPLIER json is: "+result);
        Assert.assertFalse(result.contains("error"), "Shouldn't have had an error, instead got: "+result);
    }

    @Test
    public void testWrappedStreamSerialization() throws Exception {
        String result = checkSerializesAs(BrooklynTaskTags.tagForStream("TEST", Streams.byteArrayOfString("x")), null);
        log.info("WRAPPED STREAM json is: "+result);
        Assert.assertFalse(result.contains("error"), "Shouldn't have had an error, instead got: "+result);
    }

    @SuppressWarnings("unchecked")
    protected <T> T checkSerializesAs(Object x, Class<T> type) {
        ManagementContext mgmt = LocalManagementContextForTests.newInstance();
        try {
            ObjectMapper mapper = BrooklynJacksonJsonProvider.newPrivateObjectMapper(mgmt);
            String tS = mapper.writeValueAsString(x);
            log.debug("serialized "+x+" as "+tS);
            Assert.assertTrue(tS.length() < 1000, "Data too long, size "+tS.length()+" for "+x);
            if (type==null) return (T) tS;
            return mapper.readValue(tS, type);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        } finally {
            Entities.destroyAll(mgmt);
        }
    }
    protected Map<?,?> checkSerializesAsMapWithErrorAndToString(Object x) {
        Map<?,?> rt = checkSerializesAs(x, Map.class);
        Assert.assertEquals(rt.get("toString"), x.toString());
        Assert.assertEquals(rt.get("error"), Boolean.TRUE);
        return rt;
    }
    protected void checkNonSerializableWhenStrict(Object x) {
        checkNonSerializable(x, true);
    }
    protected void checkNonSerializable(Object x, boolean strict) {
        ManagementContext mgmt = LocalManagementContextForTests.newInstance();
        try {
            ObjectMapper mapper = BrooklynJacksonJsonProvider.newPrivateObjectMapper(mgmt);
            if (strict)
                BidiSerialization.setStrictSerialization(true);
            
            String tS = mapper.writeValueAsString(x);
            Assert.fail("Should not have serialized "+x+"; instead gave: "+tS);
            
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.info("Got expected error, when serializing "+x+": "+e);
            
        } finally {
            if (strict)
                BidiSerialization.clearStrictSerialization();
            Entities.destroyAll(mgmt);
        }
    }

}
