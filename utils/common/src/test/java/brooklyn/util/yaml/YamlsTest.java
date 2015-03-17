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
package brooklyn.util.yaml;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class YamlsTest {

    @Test
    public void testGetAt() throws Exception {
        // leaf of map
        assertEquals(Yamls.getAt("k1: v", ImmutableList.of("k1")), "v");
        assertEquals(Yamls.getAt("k1: {k2: v}", ImmutableList.of("k1", "k2")), "v");
        
        // get list
        assertEquals(Yamls.getAt("k1: [v1, v2]", ImmutableList.<String>of("k1")), ImmutableList.of("v1", "v2"));

        // get map
        assertEquals(Yamls.getAt("k1: v", ImmutableList.<String>of()), ImmutableMap.of("k1", "v"));
        assertEquals(Yamls.getAt("k1: {k2: v}", ImmutableList.of("k1")), ImmutableMap.of("k2", "v"));
        
        // get array index
        assertEquals(Yamls.getAt("k1: [v1, v2]", ImmutableList.<String>of("k1", "[0]")), "v1");
        assertEquals(Yamls.getAt("k1: [v1, v2]", ImmutableList.<String>of("k1", "[1]")), "v2");
    }
}
