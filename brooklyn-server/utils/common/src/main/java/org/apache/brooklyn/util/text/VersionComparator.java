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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

import org.apache.brooklyn.util.text.NaturalOrderComparator;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.annotations.VisibleForTesting;

/**
 * {@link Comparator} for version strings.
 * <p>
 * SNAPSHOT items always lowest rated, 
 * then splitting on dots,
 * using natural order comparator (so "9" < "10" and "4u8" < "4u20"),
 * and preferring segments without qualifiers ("4" > "4beta").
 * <p>
 * Impossible to follow semantics for all versioning schemes but 
 * does the obvious right thing for normal schemes
 * and pretty well in fringe cases.
 * <p>
 * See test case for lots of examples.
 */
public class VersionComparator implements Comparator<String> {
    
    private static final String SNAPSHOT = "SNAPSHOT";

    public static final VersionComparator INSTANCE = new VersionComparator();

    public static VersionComparator getInstance() {
        return INSTANCE;
    }

    @Override
    public int compare(String v1, String v2) {
        if (v1==null && v2==null) return 0;
        if (v1==null) return -1;
        if (v2==null) return 1;
        
        boolean isV1Snapshot = v1.toUpperCase().contains(SNAPSHOT);
        boolean isV2Snapshot = v2.toUpperCase().contains(SNAPSHOT);
        if (isV1Snapshot == isV2Snapshot) {
            // if snapshot status is the same, look at dot-split parts first
            return compareDotSplitParts(splitOnDot(v1), splitOnDot(v2));
        } else {
            // snapshot goes first
            return isV1Snapshot ? -1 : 1;
        }
    }

    @VisibleForTesting
    static String[] splitOnDot(String v) {
        return v.split("(?<=\\.)|(?=\\.)");
    }
    
    private int compareDotSplitParts(String[] v1Parts, String[] v2Parts) {
        for (int i = 0; ; i++) {
            if (i >= v1Parts.length && i >= v2Parts.length) {
                // end of both
                return 0;
            }
            if (i == v1Parts.length) {
                // sequence depends whether the extra part *starts with* a number
                // ie
                //                   2.0 < 2.0.0
                // and
                //   2.0.qualifier < 2.0 < 2.0.0qualifier < 2.0.0-qualifier < 2.0.0.qualifier < 2.0.0 < 2.0.9-qualifier
                return isNumberInFirstCharPossiblyAfterADot(v2Parts, i) ? -1 : 1;
            }
            if (i == v2Parts.length) {
                // as above but inverted
                return isNumberInFirstCharPossiblyAfterADot(v1Parts, i) ? 1 : -1;
            }
            // not at end; compare this dot split part
            
            int result = compareDotSplitPart(v1Parts[i], v2Parts[i]);
            if (result!=0) return result;
        }
    }
    
    private int compareDotSplitPart(String v1, String v2) {
        String[] v1Parts = splitOnNonWordChar(v1);
        String[] v2Parts = splitOnNonWordChar(v2);
        
        for (int i = 0; ; i++) {
            if (i >= v1Parts.length && i >= v2Parts.length) {
                // end of both
                return 0;
            }
            if (i == v1Parts.length) {
                // shorter set always wins here; i.e.
                // 1-qualifier < 1
                return 1;
            }
            if (i == v2Parts.length) {
                // as above but inverted
                return -1;
            }
            // not at end; compare this dot split part
            
            String v1p = v1Parts[i];
            String v2p = v2Parts[i];
            
            if (v1p.equals(v2p)) continue;
            
            if (isNumberInFirstChar(v1p) || isNumberInFirstChar(v2p)) {
                // something starting with a number is higher than something not
                if (!isNumberInFirstChar(v1p)) return -1;
                if (!isNumberInFirstChar(v2p)) return 1;
                
                // both start with numbers; can use natural order comparison *unless*
                // one is purely a number AND the other *begins* with that number,
                // followed by non-digit chars, in which case prefer the pure number
                // ie:
                //           1beta < 1
                // but note
                //            1 < 2beta < 11beta
                if (isNumber(v1p) || isNumber(v2p)) {
                    if (!isNumber(v1p)) {
                        if (v1p.startsWith(v2p)) {
                            if (!isNumberInFirstChar(Strings.removeFromStart(v1p, v2p))) {
                                // v2 is a number, and v1 is the same followed by non-numbers
                                return -1;
                            }
                        }
                    }
                    if (!isNumber(v2p)) {
                        // as above but inverted
                        if (v2p.startsWith(v1p)) {
                            if (!isNumberInFirstChar(Strings.removeFromStart(v2p, v1p))) {
                                return 1;
                            }
                        }
                    }
                    // both numbers, skip to natural order comparison
                }
            }
            
            // otherwise it is in-order
            int result = NaturalOrderComparator.INSTANCE.compare(v1p, v2p);
            if (result!=0) return result;
        }
    }

    @VisibleForTesting
    static String[] splitOnNonWordChar(String v) {
        Collection<String> parts = new ArrayList<String>();
        String remaining = v;
        
        // use lookahead to split on all non-letter non-numbers, putting them into their own buckets 
        parts.addAll(Arrays.asList(remaining.split("(?<=[^0-9\\p{L}])|(?=[^0-9\\p{L}])")));
        return parts.toArray(new String[parts.size()]);
    }

    @VisibleForTesting
    static boolean isNumberInFirstCharPossiblyAfterADot(String[] parts, int i) {
        if (parts==null || parts.length<=i) return false;
        if (isNumberInFirstChar(parts[i])) return true;
        if (".".equals(parts[i])) {
            if (parts.length>i+1)
                if (isNumberInFirstChar(parts[i+1])) 
                    return true;
        }
        return false;
    }

    @VisibleForTesting
    static boolean isNumberInFirstChar(String v) {
        if (v==null || v.length()==0) return false;
        return Character.isDigit(v.charAt(0));
    }
    
    @VisibleForTesting
    static boolean isNumber(String v) {
        if (v==null || v.length()==0) return false;
        return v.matches("[\\d]+");
    }
}
