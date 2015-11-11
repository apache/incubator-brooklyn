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

import com.google.common.base.Objects;

/**
 * Strongly types tag used to store TOSCA metadata such as:
 * <ul>
 *     <li>TOSCA type</li>
 *     <li>Requirements</li>
 *     <li>relationships</li>
 * </ul>
 */
public class BrooklynToscaTags {

    private String derivedFrom = "brooklyn.nodes.SoftwareProcess";

    public String getDerivedFrom() {
        return derivedFrom;
    }

    public void setDerivedFrom(String derivedFrom) {
        this.derivedFrom = derivedFrom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BrooklynToscaTags)) return false;
        
        BrooklynToscaTags that = (BrooklynToscaTags) o;

        return Objects.equal(derivedFrom, that.derivedFrom);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(derivedFrom);
    }
}
