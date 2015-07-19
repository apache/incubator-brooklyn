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

import javax.annotation.Nonnull;

import brooklyn.basic.BrooklynObject;
import brooklyn.util.text.NaturalOrderComparator;
import brooklyn.util.text.Strings;

/**
 * Useful comparators for domain objects
 */
public class SummaryComparators {
    
    private SummaryComparators() {}
    
    private static NaturalOrderComparator COMPARATOR = new NaturalOrderComparator();
    
    @Nonnull
    static String getDisplayNameOrName(HasName o1) {
        String n1 = null;
        if (o1 instanceof BrooklynObject)
            n1 = ((BrooklynObject)o1).getDisplayName();
        if (Strings.isEmpty(n1) && o1 instanceof HasConfig && ((HasConfig)o1).getConfig()!=null)
            n1 = Strings.toString(((HasConfig)o1).getConfig().get("displayName"));
        // prefer display name if set
        if (Strings.isEmpty(n1))
            n1 = o1.getName();
        if (n1==null) {
            // put last
            return "~~~";
        }
        return n1;
    }
    
    public static Comparator<HasName> displayNameComparator() {
        return new Comparator<HasName>() {
            @Override
            public int compare(HasName o1, HasName o2) {
                return COMPARATOR.compare(getDisplayNameOrName(o1).toLowerCase(), getDisplayNameOrName(o2).toLowerCase());
            }
        };
    }

    public static Comparator<HasName> nameComparator() {
        return new Comparator<HasName>() {
            @Override
            public int compare(HasName o1, HasName o2) {
                return COMPARATOR.compare(o1.getName(), o2.getName());
            }
        };
    }

    public static Comparator<HasId> idComparator() {
        return new Comparator<HasId>() {
            @Override
            public int compare(HasId o1, HasId o2) {
                return o1.getId().compareTo(o2.getId());
            }
        };
    }

}
