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
package org.apache.brooklyn.tosca;

import java.util.Collection;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Strongly types tag used to store TOSCA metadata such as:
 * <ul>
 *     <li>TOSCA type</li>
 *     <li>Capabilities</li>
 *     <li>Requirements</li>
 * </ul>
 */
public class BrooklynToscaTags {

    @JsonProperty("tosca:derivedFrom")
    private String derivedFrom = "brooklyn.nodes.SoftwareProcess";
    @JsonProperty("tosca:capabilities")
    private Collection<Capability> capabilities = Lists.newArrayList();
    @JsonProperty("tosca:requirements")
    private Collection<Requirement> requirements = Lists.newArrayList();

    public String getDerivedFrom() {
        return derivedFrom;
    }

    public void setDerivedFrom(String derivedFrom) {
        this.derivedFrom = derivedFrom;
    }

    public Collection<Capability> getCapabilities() {
        return capabilities;
    }

    public void addCapability(String id, String type, int upperbound) {
        capabilities.add(new Capability(id, type, upperbound));
    }

    public Collection<Requirement> getRequirements() {
        return requirements;
    }

    public void addRequirement(String id, String capabilityType, String relationshipType, int lowerBound, int upperbound) {
        requirements.add(new Requirement(id, capabilityType, relationshipType, lowerBound, upperbound));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BrooklynToscaTags)) return false;

        BrooklynToscaTags that = (BrooklynToscaTags) o;
        return Objects.equal(derivedFrom, that.derivedFrom) && Iterables.elementsEqual(capabilities, that.capabilities) && Iterables.elementsEqual(requirements, that.requirements);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(derivedFrom, requirements);
    }

    public class Capability {
        private final String id;
        private final String type;
        private final int upperBound;

        public Capability(String id, String type, int upperBound) {
            this.id = id;
            this.type = type;
            this.upperBound = upperBound;
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        public int getUpperBound() {
            return upperBound;
        }
    }

    public class Requirement {
        private final String id;
        private final String capabilityType;
        private final String relationshipType;
        private final int lowerBound;
        private final int upperBound;

        public Requirement(String id, String capabilityType, String relationshipType, int lowerBound, int upperBound) {
            this.id = id;
            this.capabilityType = capabilityType;
            this.relationshipType = relationshipType;
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
        }

        public String getId() {
            return id;
        }

        public String getCapabilityType() {
            return capabilityType;
        }

        public String getRelationshipType() {
            return relationshipType;
        }

        public int getLowerBound() {
            return lowerBound;
        }

        public int getUpperBound() {
            return upperBound;
        }
    }
}
