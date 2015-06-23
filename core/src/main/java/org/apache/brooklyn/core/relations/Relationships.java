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
package org.apache.brooklyn.core.relations;

import com.google.common.base.Objects;

import brooklyn.basic.relations.Relationship;

public class Relationships {

    private static abstract class AbstractBasicRelationship<SourceType,TargetType> implements Relationship<SourceType,TargetType> {
        private final String relationshipTypeName;
        private final String sourceName;
        private final String sourceNamePlural;
        private final Class<TargetType> targetType;
        
        private AbstractBasicRelationship(String relationshipTypeName, String sourceName, String sourceNamePlural, Class<TargetType> targetType) {
            this.relationshipTypeName = relationshipTypeName;
            this.sourceName = sourceName;
            this.sourceNamePlural = sourceNamePlural;
            this.targetType = targetType;
        }

        @Override
        public String getRelationshipTypeName() {
            return relationshipTypeName;
        }

        @Override
        public String getSourceName() {
            return sourceName;
        }
        
        @Override
        public String getSourceNamePlural() {
            return sourceNamePlural;
        }
        
        @Override
        public Class<TargetType> getTargetType() {
            return targetType;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (!(obj instanceof AbstractBasicRelationship)) return false;
            
            @SuppressWarnings("rawtypes")
            AbstractBasicRelationship other = (AbstractBasicRelationship) obj;

            // only look at type name and class; name of source and target is informational
            if (!Objects.equal(relationshipTypeName, other.relationshipTypeName)) return false;
            if (!Objects.equal(getSourceType(), other.getSourceType())) return false;
            if (!Objects.equal(targetType, other.targetType)) return false;

            if (getInverseRelationship() == null) {
                // require both null or...
                if (other.getInverseRelationship() != null)
                    return false;
            } else {
                // ... they have same type name
                // (don't recurse as that sets up infinite loop)
                if (other.getInverseRelationship() == null)
                    return false;
                if (!Objects.equal(getInverseRelationship().getRelationshipTypeName(), other.getInverseRelationship().getRelationshipTypeName())) return false;
            }

            return true;
        }
        
        @Override
        public int hashCode() {
            // comments as per equals
            return Objects.hashCode(relationshipTypeName, getSourceType(), targetType,
                getInverseRelationship()!=null ? getInverseRelationship().getRelationshipTypeName() : null);
        }
        
        @Override
        public String toString() {
            return relationshipTypeName;
        }
    }

    private static class BasicRelationshipWithInverse<SourceType,TargetType> extends AbstractBasicRelationship<SourceType,TargetType> {
        private BasicRelationshipWithInverse<TargetType,SourceType> inverseRelationship;
        
        private BasicRelationshipWithInverse(String relationshipTypeName, String sourceName, String sourceNamePlural, Class<TargetType> targetType) {
            super(relationshipTypeName, sourceName, sourceNamePlural, targetType);
        }

        @Override
        public Relationship<TargetType,SourceType> getInverseRelationship() {
            return inverseRelationship;
        }

        @Override
        public Class<SourceType> getSourceType() {
            if (getInverseRelationship()==null) return null;
            return getInverseRelationship().getTargetType();
        }

        @Override
        public String getTargetName() {
            if (getInverseRelationship()==null) return null;
            return getInverseRelationship().getSourceName();
        }

        @Override
        public String getTargetNamePlural() {
            if (getInverseRelationship()==null) return null;
            return getInverseRelationship().getSourceNamePlural();
        }
    }

    private static class BasicRelationshipOneWay<SourceType,TargetType> extends AbstractBasicRelationship<SourceType,TargetType> {
        
        private final String targetName;
        private final String targetNamePlural;
        private final Class<SourceType> sourceType;

        private BasicRelationshipOneWay(String sourceName, String sourceNamePlural, Class<SourceType> sourceType, String toTargetRelationshipTypeName, 
                String targetName, String targetNamePlural, Class<TargetType> targetType) {
            super(toTargetRelationshipTypeName, sourceName, sourceNamePlural, targetType);
            this.targetName = targetName;
            this.targetNamePlural = targetNamePlural;
            this.sourceType = sourceType;
        }

        @Override
        public Class<SourceType> getSourceType() {
            return sourceType;
        }

        @Override
        public String getTargetName() {
            return targetName;
        }

        @Override
        public String getTargetNamePlural() {
            return targetNamePlural;
        }

        @Override
        public Relationship<TargetType, SourceType> getInverseRelationship() {
            return null;
        }
    }

    public static <SourceType,TargetType> Relationship<SourceType,TargetType> newRelationshipPair(
            String sourceName, String sourceNamePlural, Class<SourceType> sourceType, String toTargetRelationshipTypeName,   
            String targetName, String targetNamePlural, Class<TargetType> targetType, String toSourceRelationshipTypeName) {
        BasicRelationshipWithInverse<SourceType, TargetType> r1 = new BasicRelationshipWithInverse<SourceType, TargetType>(
            toTargetRelationshipTypeName, sourceName, sourceNamePlural, targetType);
        BasicRelationshipWithInverse<TargetType, SourceType> r2 = new BasicRelationshipWithInverse<TargetType, SourceType>(
            toSourceRelationshipTypeName, targetName, targetNamePlural, sourceType);
        r1.inverseRelationship = r2;
        r2.inverseRelationship = r1;
        return r1;
    }

    public static <SourceType,TargetType> Relationship<SourceType,TargetType> newRelationshipOneway(
            String sourceName, String sourceNamePlural, Class<SourceType> sourceType, String toTargetRelationshipTypeName,   
            String targetName, String targetNamePlural, Class<TargetType> targetType) {
        return new BasicRelationshipOneWay<SourceType,TargetType>(
            sourceName, sourceNamePlural, sourceType, toTargetRelationshipTypeName,
            targetName, targetNamePlural, targetType);
    }
    
}
