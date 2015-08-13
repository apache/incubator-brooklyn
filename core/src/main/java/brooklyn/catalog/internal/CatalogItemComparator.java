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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

import org.apache.brooklyn.api.catalog.CatalogItem;

import brooklyn.util.text.NaturalOrderComparator;

/**
 * Largest version first order.
 * 
 * When using the comparator to sort - first using symbolicName
 * and if equal puts larger versions first, snapshots at the back.
 */
public class CatalogItemComparator<T,SpecT> implements Comparator<CatalogItem<T, SpecT>> {
    private static final String SNAPSHOT = "SNAPSHOT";

    public static final CatalogItemComparator<?, ?> INSTANCE = new CatalogItemComparator<Object, Object>();

    @SuppressWarnings("unchecked")
    public static <T,SpecT> CatalogItemComparator<T,SpecT> getInstance() {
        return (CatalogItemComparator<T, SpecT>) INSTANCE;
    }

    @Override
    public int compare(CatalogItem<T, SpecT> o1, CatalogItem<T, SpecT> o2) {
        int symbolicNameComparison = o1.getSymbolicName().compareTo(o2.getSymbolicName());
        if (symbolicNameComparison != 0) {
            return symbolicNameComparison;
        } else {
            String v1 = o1.getVersion();
            String v2 = o2.getVersion();

            boolean isV1Snapshot = v1.toUpperCase().contains(SNAPSHOT);
            boolean isV2Snapshot = v2.toUpperCase().contains(SNAPSHOT);
            if (isV1Snapshot == isV2Snapshot) {
                String[] v1Parts = split(v1);
                String[] v2Parts = split(v2);
                return -compare(v1Parts, v2Parts);
            } else if (isV1Snapshot) {
                return 1;
            } else {
                return -1;
            }
        }
    }

    private String[] split(String v) {
        Collection<String> parts = new ArrayList<String>();
        int startPos = 0;
        int delimPos;
        while ((delimPos = v.indexOf('.', startPos)) != -1) {
            String part = v.substring(startPos, delimPos);
            if (parse(part) != -1) {
                parts.add(part);
            } else {
                break;
            }
            startPos = delimPos+1;
        }
        String remaining = v.substring(startPos);
        parts.addAll(Arrays.asList(remaining.split("[^\\d]", 2)));
        return parts.toArray(new String[parts.size()]);
    }

    private int compare(String[] v1Parts, String[] v2Parts) {
        int len = Math.max(v1Parts.length, v2Parts.length);
        for (int i = 0; i < len; i++) {
            if (i == v1Parts.length) {
                return isNumber(v2Parts[i]) ? -1 : 1;
            }
            if (i == v2Parts.length) {
                return isNumber(v1Parts[i]) ? 1 : -1;
            }

            String p1 = v1Parts[i];
            String p2 = v2Parts[i];
            int n1 = parse(p1);
            int n2 = parse(p2);
            if (n1 != -1 && n2 != -1) {
                if (n1 != n2) {
                    return compare(n1, n2);
                }
            } else if (n1 == -1 && n2 != -1) {
                return -1;
            } else if (n1 != -1 && n2 == -1) {
                return 1;
            } else {
                int cmp = NaturalOrderComparator.INSTANCE.compare(p1, p2);
                if (cmp < 0) {
                    return -1;
                } else if (cmp > 0) {
                    return 1;
                }
            }
        }
        return 0;
    }

    private boolean isNumber(String v) {
        return parse(v) != -1;
    }

    //Replace with Integer.compare in J7
    private int compare(int n1, int n2) {
        if (n1 == n2) {
            return 0;
        } else if (n1 < n2) {
            return -1;
        } else {
            return 1;
        }
    }

    private int parse(String p) {
        try {
            return Integer.parseInt(p);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
