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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.NoSuchElementException;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.util.http.HttpToolResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class HttpValueFunctionsTest {

    private int responseCode = 200;
    private long fullLatency = 1000;
    private String headerName = "my_header";
    private String headerVal = "my_header_val";
    private String bodyKey = "mykey";
    private String bodyVal = "myvalue";
    private String body = "{"+bodyKey+":"+bodyVal+"}";
    private long now;
    private HttpToolResponse response;
    
    @BeforeMethod
    public void setUp() throws Exception {
        now = System.currentTimeMillis();
        response = new HttpToolResponse(responseCode, ImmutableMap.of(headerName, ImmutableList.of(headerVal)), 
                body.getBytes(), now-fullLatency, fullLatency / 2, fullLatency);
    }
    
    @Test
    public void testResponseCode() throws Exception {
        assertEquals(HttpValueFunctions.responseCode().apply(response), Integer.valueOf(responseCode));
    }

    @Test
    public void testContainsHeader() throws Exception {
        assertTrue(HttpValueFunctions.containsHeader(headerName).apply(response));
        assertFalse(HttpValueFunctions.containsHeader("wrong_header").apply(response));
    }
    
    @Test
    public void testStringContents() throws Exception {
        assertEquals(HttpValueFunctions.stringContentsFunction().apply(response), body);
    }

    @Test
    public void testJsonContents() throws Exception {
        JsonElement json = HttpValueFunctions.jsonContents().apply(response);
        assertTrue(json.isJsonObject());
        assertEquals(json.getAsJsonObject().entrySet(), ImmutableMap.of(bodyKey, new JsonPrimitive(bodyVal)).entrySet());
    }

    @Test
    public void testJsonContentsGettingElement() throws Exception {
        assertEquals(HttpValueFunctions.jsonContents(bodyKey, String.class).apply(response), bodyVal);
    }

    @Test(expectedExceptions=NoSuchElementException.class)
    public void testJsonContentsGettingMissingElement() throws Exception {
        assertNull(HttpValueFunctions.jsonContents("wrongkey", String.class).apply(response));
    }

    @Test
    public void testLatency() throws Exception {
        assertEquals(HttpValueFunctions.latency().apply(response), Long.valueOf(fullLatency));
    }
}
