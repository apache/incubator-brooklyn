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
package brooklyn.event.feed.http;

import java.util.NoSuchElementException;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.collections.Jsonya;
import brooklyn.util.collections.Jsonya.Navigator;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Functionals;
import brooklyn.util.guava.Maybe;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.jayway.jsonpath.PathNotFoundException;

public class JsonFunctionsTest {

    public static JsonElement europeMap() {
        Navigator<MutableMap<Object, Object>> europe = Jsonya.newInstance().at("europe", "uk", "edinburgh")
                .put("population", 500*1000)
                .put("weather", "wet", "lighting", "dark")
                .root().at("europe").at("france").put("population", 80*1000*1000)
                .root();
        return new JsonParser().parse( europe.toString() );
    }

    @Test
    public void testWalk1() {
        JsonElement pop = JsonFunctions.walk("europe", "france", "population").apply(europeMap());
        Assert.assertEquals( (int)JsonFunctions.cast(Integer.class).apply(pop), 80*1000*1000 );
    }

    @Test
    public void testWalk2() {
        String weather = Functionals.chain(
            JsonFunctions.walk("europe.uk.edinburgh.weather"),
            JsonFunctions.cast(String.class) ).apply(europeMap());
        Assert.assertEquals(weather, "wet");
    }

    @Test(expectedExceptions=NoSuchElementException.class)
    public void testWalkWrong() {
        Functionals.chain(
            JsonFunctions.walk("europe", "spain", "barcelona"),
            JsonFunctions.cast(String.class) ).apply(europeMap());
    }


    @Test
    public void testWalkM() {
        Maybe<JsonElement> pop = JsonFunctions.walkM("europe", "france", "population").apply( Maybe.of(europeMap()) );
        Assert.assertEquals( (int)JsonFunctions.castM(Integer.class).apply(pop), 80*1000*1000 );
    }

    @Test
    public void testWalkMWrong1() {
        Maybe<JsonElement> m = JsonFunctions.walkM("europe", "spain", "barcelona").apply( Maybe.of( europeMap()) );
        Assert.assertTrue(m.isAbsent());
    }

    @Test(expectedExceptions=Exception.class)
    public void testWalkMWrong2() {
        Maybe<JsonElement> m = JsonFunctions.walkM("europe", "spain", "barcelona").apply( Maybe.of( europeMap()) );
        JsonFunctions.castM(String.class).apply(m);
    }

    
    @Test
    public void testWalkN() {
        JsonElement pop = JsonFunctions.walkN("europe", "france", "population").apply( europeMap() );
        Assert.assertEquals( (int)JsonFunctions.cast(Integer.class).apply(pop), 80*1000*1000 );
    }

    @Test
    public void testWalkNWrong1() {
        JsonElement m = JsonFunctions.walkN("europe", "spain", "barcelona").apply( europeMap() );
        Assert.assertNull(m);
    }

    public void testWalkNWrong2() {
        JsonElement m = JsonFunctions.walkN("europe", "spain", "barcelona").apply( europeMap() );
        String n = JsonFunctions.cast(String.class).apply(m);
        Assert.assertNull(n);
    }

    @Test
    public void testGetPath1(){
        Integer obj = (Integer) JsonFunctions.getPath("$.europe.uk.edinburgh.population").apply(europeMap());
        Assert.assertEquals((int) obj, 500*1000);
    }

    @Test
    public void testGetPath2(){
        String obj = (String) JsonFunctions.getPath("$.europe.uk.edinburgh.lighting").apply(europeMap());
        Assert.assertEquals(obj, "dark");
    }

    @Test
    public void testGetMissingPathIsNullOrThrows(){
        try {
            // TODO is there a way to force this to return null if not found?
            // for me (Alex) it throws but for others it seems to return null
            Object obj = JsonFunctions.getPath("$.europe.spain.malaga").apply(europeMap());
            Assert.assertNull(obj);
        } catch (PathNotFoundException e) {
            // not unexpected
        }
    }
    
}
