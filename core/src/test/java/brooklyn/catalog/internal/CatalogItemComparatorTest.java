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
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;
import org.apache.brooklyn.api.catalog.CatalogItem;

public class CatalogItemComparatorTest {
    private static final String RC2 = "10.5.8-rc2";
    private static final String STABLE = "10.5.8";

    @Test
    public void testComparison() {
        compare("0.0.1", "0.0.2", 1);
        compare("0.0.2", "0.0.1", -1);
        compare("0.0.1-qual", "0.0.2", 1);
        compare("0.0.1.qual", "0.0.2", 1);
        compare("0.0.1-qual", "0.0.1_qual", 0);
        compare("0.0.1.qual", "0.0.1.qual", 0);
        compare("0.0.1", "0.0.2-SNAPSHOT", -1);
        compare("0.0.1", "0.0.2.SNAPSHOT", -1);
        compare("0.0.0_SNAPSHOT", "0.0.1-SNAPSHOT-20141111114709760", 1);
        compare("0.0.0.SNAPSHOT", "0.0.1.SNAPSHOT-20141111114709760", 1);
        compare("2.0", "2.0.1-BUILD", 1);
        compare("2.0", "2.0.1.BUILD", 1);
        compare("2.0.1", "2.0-BUILD", -1);
        compare("2.0.1", "2.0.0.BUILD", -1);
        compare("2.0", "2.0-BUILD", -1);
        // Note not true for .qualifier: compare("2.0", "2.0.0.BUILD", -1);
        compare("2.1", "2.0-BUILD", -1);
        compare("2.1", "2.0.0.BUILD", -1);
        compare("1", "1.3", 1);
        compare("1-beta", "1-rc2", 1);
        // Note not true for .qualifier: compare("1.0.0.beta", "1.0.0.rc2", 1);
        compare("1-beta1", "1-beta10", 1);

        compare(STABLE, "10.5", -1);
        compare(STABLE, STABLE, 0);

        compare(STABLE, "10.6", 1);
        compare(STABLE, "10.5.8.1", 1);

        compare("10.5.8-rc2", "10.5.8-rc3", 1) ;
        compare("10.5.8-rc2", "10.5.8-rc1", -1);

        compare(STABLE, RC2, -1);

        CatalogItemComparator cmp = CatalogItemComparator.INSTANCE;
        assertTrue(cmp.compare(v(RC2), v("10.5.8-beta1")) == cmp.compare(v(RC2), v("10.5.8-beta3")));
    }
    
    private void compare(String v1, String v2, int expected) {
        CatalogItemComparator cmp = CatalogItemComparator.INSTANCE;
        assertEquals(cmp.compare(v(v1), v(v2)), expected);
        assertEquals(cmp.compare(v(v2), v(v1)), -expected);
    }
    
    private CatalogItem<?, ?> v(String version) {
        return CatalogItemBuilder.newEntity("xxx", version).build();
    }
}
