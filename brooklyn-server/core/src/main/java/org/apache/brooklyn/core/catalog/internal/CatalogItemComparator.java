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
package org.apache.brooklyn.core.catalog.internal;

import java.util.Collections;
import java.util.Comparator;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.util.text.VersionComparator;

/**
 * Largest version first order.
 * 
 * When using the comparator to sort - first using symbolicName
 * and if equal puts larger versions first, snapshots at the back.
 */
public class CatalogItemComparator<T,SpecT> implements Comparator<CatalogItem<T, SpecT>> {

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
            return Collections.reverseOrder(VersionComparator.INSTANCE).compare(o1.getVersion(), o2.getVersion());
        }
    }

}
