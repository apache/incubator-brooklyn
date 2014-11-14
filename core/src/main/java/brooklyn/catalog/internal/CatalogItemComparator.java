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

import java.util.Comparator;

import brooklyn.catalog.CatalogItem;

/**
 * When using the comparator to sort - first using symbolicName
 * and if equal puts larger versions first, snapshots at the back.
 */
public class CatalogItemComparator implements Comparator<CatalogItem<?, ?>> {
    private static final String SNAPSHOT = "SNAPSHOT";

    public static final CatalogItemComparator INSTANCE = new CatalogItemComparator();

    @Override
    public int compare(CatalogItem<?, ?> o1, CatalogItem<?, ?> o2) {
        int symbolicNameComparison = o1.getSymbolicName().compareTo(o2.getSymbolicName());
        if (symbolicNameComparison != 0) {
            return symbolicNameComparison;
        } else {
            String v1 = o1.getVersion();
            String v2 = o2.getVersion();

            boolean isV1Snapshot = v1.toUpperCase().contains(SNAPSHOT);
            boolean isV2Snapshot = v2.toUpperCase().contains(SNAPSHOT);
            if (isV1Snapshot == isV2Snapshot) {
                String[] v1Parts = v1.split("[^\\d]", 4);
                String[] v2Parts = v2.split("[^\\d]", 4);
                return -compare(v1Parts, v2Parts);
            } else if (isV1Snapshot) {
                return 1;
            } else {
                return -1;
            }
        }
    }

    private int compare(String[] v1Parts, String[] v2Parts) {
        int len = Math.max(v1Parts.length, v2Parts.length);
        for (int i = 0; i < len; i++) {
            if (i == v1Parts.length) {
                return -1;
            }
            if (i == v2Parts.length) {
                return 1;
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
                return 1;
            } else if (n1 != -1 && n2 == -1) {
                return -1;
            } else {
                int cmp = p1.compareTo(p2);
                if (cmp != 0) {
                    return cmp;
                }
            }
        }
        return 0;
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
