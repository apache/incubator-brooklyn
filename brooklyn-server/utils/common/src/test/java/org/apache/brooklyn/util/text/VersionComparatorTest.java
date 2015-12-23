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
package org.apache.brooklyn.util.text;

import static org.testng.Assert.assertEquals;

import java.util.List;

import org.apache.brooklyn.util.collections.MutableList;
import org.testng.Assert;
import org.testng.annotations.Test;

public class VersionComparatorTest {

    @Test
    public void testStaticHelpers() {
        Assert.assertEquals(VersionComparator.splitOnDot("a.b.cc"), new String[] { "a", ".", "b", ".", "cc" });
        Assert.assertEquals(VersionComparator.splitOnDot("a..b-c"), new String[] { "a", ".", ".", "b-c" });

        Assert.assertEquals(VersionComparator.splitOnNonWordChar("a1-b__cc9c"), new String[] { 
            "a1", "-", "b", "_", "_", "cc9c" });

        Assert.assertEquals(VersionComparator.isNumberInFirstChar("1a"), true);
        Assert.assertEquals(VersionComparator.isNumberInFirstChar("a1"), false);
        Assert.assertEquals(VersionComparator.isNumberInFirstChar(""), false);
        Assert.assertEquals(VersionComparator.isNumberInFirstChar(null), false);
        
        Assert.assertEquals(VersionComparator.isNumber("1"), true);
        Assert.assertEquals(VersionComparator.isNumber("1111"), true);
        Assert.assertEquals(VersionComparator.isNumber("1a"), false);
        Assert.assertEquals(VersionComparator.isNumber("a1"), false);
        Assert.assertEquals(VersionComparator.isNumber(""), false);
        Assert.assertEquals(VersionComparator.isNumber(null), false);
    }
    
    @Test
    public void testComparison() {
        VersionComparator.INSTANCE.compare("B", "B-2");
        
        assertVersionOrder("0", "1");
        assertVersionOrder("0", "0.0", "0.9", "0.10", "0.10.0", "1");
        
        assertVersionOrder("a", "b");
        
        assertVersionOrder("1beta", "1", "2beta", "11beta");
        assertVersionOrder("beta", "0", "1beta", "1-alpha", "1", "11beta", "11-alpha", "11");
        assertVersionOrder("1.0-a", "1.0-b", "1.0");
        
        assertVersionOrder("qualifier", "0qualifier", "0-qualifier", "0", "1-qualifier", "1");

        assertVersionOrder("2.0.qualifier", "2.0", "2.0.0qualifier", "2.0.0-qualifier", "2.0.0.qualifier", "2.0.0");
        assertVersionOrder("2.0.qualifier.0", "2.0", "2.0.0qualifier.0", "2.0.0-qualifier.0", "2.0.0.qualifier.0", "2.0.0", "2.0.0.0");
        
        assertVersionOrder("0", "0.0", "0.1", "0.1.0", "0.1.1", "0.2", "0.2.1", "1", "1.0", "2");
        // case sensitive
        assertVersionOrder("AA", "Aa", "aa");
        // letters in order, ignoring case, and using natural order on numbers, splitting at word boundaries
        assertVersionOrder("A", "B-2", "B-10", "B", "B0", "C", "b", "b1", "b9", "b10", "c", "0");
        // and non-letter symbols are compared, in alpha order (e.g. - less than _) with dots even higher
        assertVersionOrder("0-qual", "0", "0.1", "1-qualC", "1_qualB", "1.qualA", "1", "1.0");
        
        // numeric comparison works with qualifiers, preferring unqualified
        assertVersionOrder("0--qual", "0-qual", "0-qualB", "0-qualB2", "0-qualB10", "0-qualC", "0.qualA", "0", "0.1.qual", "0.1", "1");
        
        // all snapshots rated lower
        assertVersionOrder(
            "0_SNAPSHOT", "0.1.SNAPSHOT", "1-SNAPSHOT-X-X", "1-SNAPSHOT-X", "1-SNAPSHOT-XX-X", "1-SNAPSHOT-XX", "1-SNAPSHOT", 
            "1.0-SNAPSHOT-B", "1.0.SNAPSHOT-A", 
            "1.2-SNAPSHOT", "1.10-SNAPSHOT",
            "qualifer",
            "0", "0.1", "1");
    }
    
    private static void assertVersionOrder(String v1, String v2, String ...otherVersions) {
        List<String> versions = MutableList.<String>of().append(v1, v2, otherVersions);
        
        for (int i=0; i<versions.size(); i++) {
            for (int j=0; j<versions.size(); j++) {
                assertEquals(VersionComparator.getInstance().compare(
                        versions.get(i), versions.get(j)),
                    new Integer(i).compareTo(j), "comparing "+versions.get(i)+" and "+versions.get(j));
            }
        }
    }

}
