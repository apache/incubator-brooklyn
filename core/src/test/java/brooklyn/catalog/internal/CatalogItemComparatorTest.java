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
package brooklyn.catalog.internal;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import brooklyn.catalog.CatalogItem;

public class CatalogItemComparatorTest {

    @Test
    public void testComparison() {
        CatalogItemComparator cmp = CatalogItemComparator.INSTANCE;
        assertEquals(cmp.compare(v("0.0.1"), v("0.0.2")), 1);
        assertEquals(cmp.compare(v("0.0.2"), v("0.0.1")), -1);
        assertEquals(cmp.compare(v("0.0.1-qual"), v("0.0.2")), 1);
        assertEquals(cmp.compare(v("0.0.1-qual"), v("0.0.1_qual")), 0);
        assertEquals(cmp.compare(v("0.0.1"), v("0.0.2-SNAPSHOT")), -1);
        assertEquals(cmp.compare(v("0.0.0_SNAPSHOT"), v("0.0.1-SNAPSHOT-20141111114709760")), 1);
        assertEquals(cmp.compare(v("2.0"), v("2.0.1-BUILD")), 1);
        assertEquals(cmp.compare(v("2.0"), v("2.0-BUILD")), 1);
        assertEquals(cmp.compare(v("2.1"), v("2.0-BUILD")), -1);
    }
    
    private CatalogItem<?, ?> v(String version) {
        return CatalogItemBuilder.newEntity("xxx", version).build();
    }
}
