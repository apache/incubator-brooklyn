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
import brooklyn.util.text.NaturalOrderComparator;

public class CatalogItemComparator implements Comparator<CatalogItem<?, ?>> {
    private static final String SNAPSHOT = "SNAPSHOT";
    private static final Comparator<String> COMPARATOR = new NaturalOrderComparator();

    public static final CatalogItemComparator INSTANCE = new CatalogItemComparator();

    @Override
    public int compare(CatalogItem<?, ?> o1, CatalogItem<?, ?> o2) {
        int symbolicNameComparison = o1.getSymbolicName().compareTo(o2.getSymbolicName());
        if (symbolicNameComparison != 0) {
            return symbolicNameComparison;
        } else {
            String v1 = o1.getVersion().toUpperCase();
            String v2 = o2.getVersion().toUpperCase();
            boolean isV1Snapshot = v1.contains(SNAPSHOT);
            boolean isV2Snapshot = v2.contains(SNAPSHOT);
            if (isV1Snapshot == isV2Snapshot) {
                return -COMPARATOR.compare(v1, v2);
            } else if (isV1Snapshot) {
                return 1;
            } else {
                return -1;
            }
        }
    }
}
