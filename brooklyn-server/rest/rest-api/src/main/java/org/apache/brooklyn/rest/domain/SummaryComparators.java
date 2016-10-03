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
package org.apache.brooklyn.rest.domain;

import java.util.Comparator;

import org.apache.brooklyn.util.text.NaturalOrderComparator;

/**
 * Useful comparators for domain objects
 */
public class SummaryComparators {

    private SummaryComparators() {
    }

    private static NaturalOrderComparator COMPARATOR = new NaturalOrderComparator();

    public static Comparator<HasName> nameComparator() {
        return new Comparator<HasName>() {
            @Override
            public int compare(HasName o1, HasName o2) {
                return COMPARATOR.compare(o1.getName(), o2.getName());
            }
        };
    }

}
