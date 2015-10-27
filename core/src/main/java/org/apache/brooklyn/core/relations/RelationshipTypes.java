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

import brooklyn.basic.relations.RelationshipType;

public class RelationshipTypes {

    private static abstract class AbstractBasicRelationship<SourceType,TargetType> implements RelationshipType<SourceType,TargetType> {
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

            if (getInverseRelationshipType() == null) {
                // require both null or...
                if (other.getInverseRelationshipType() != null)
                    return false;
            } else {
                // ... they have same type name
                // (don't recurse as that sets up infinite loop)
                if (other.getInverseRelationshipType() == null)
                    return false;
                if (!Objects.equal(getInverseRelationshipType().getRelationshipTypeName(), other.getInverseRelationshipType().getRelationshipTypeName())) return false;
            }

            return true;
        }
        
        @Override
        public int hashCode() {
            // comments as per equals
            return Objects.hashCode(relationshipTypeName, getSourceType(), targetType,
                getInverseRelationshipType()!=null ? getInverseRelationshipType().getRelationshipTypeName() : null);
        }
        
        @Override
        public String toString() {
            return relationshipTypeName;
        }
    }

    private static class BasicRelationshipWithInverse<SourceType,TargetType> extends AbstractBasicRelationship<SourceType,TargetType> {
        private BasicRelationshipWithInverse<TargetType,SourceType> inverseRelationshipType;
        
        private BasicRelationshipWithInverse(String relationshipTypeName, String sourceName, String sourceNamePlural, Class<TargetType> targetType) {
            super(relationshipTypeName, sourceName, sourceNamePlural, targetType);
        }

        @Override
        public RelationshipType<TargetType,SourceType> getInverseRelationshipType() {
            return inverseRelationshipType;
        }

        @Override
        public Class<SourceType> getSourceType() {
            if (getInverseRelationshipType()==null) return null;
            return getInverseRelationshipType().getTargetType();
        }

        @Override
        public String getTargetName() {
            if (getInverseRelationshipType()==null) return null;
            return getInverseRelationshipType().getSourceName();
        }

        @Override
        public String getTargetNamePlural() {
            if (getInverseRelationshipType()==null) return null;
            return getInverseRelationshipType().getSourceNamePlural();
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
        public RelationshipType<TargetType, SourceType> getInverseRelationshipType() {
            return null;
        }
    }

    public static <SourceType,TargetType> RelationshipType<SourceType,TargetType> newRelationshipPair(
            String sourceName, String sourceNamePlural, Class<SourceType> sourceType, String toTargetRelationshipTypeName,   
            String targetName, String targetNamePlural, Class<TargetType> targetType, String toSourceRelationshipTypeName) {
        BasicRelationshipWithInverse<SourceType, TargetType> r1 = new BasicRelationshipWithInverse<SourceType, TargetType>(
            toTargetRelationshipTypeName, sourceName, sourceNamePlural, targetType);
        BasicRelationshipWithInverse<TargetType, SourceType> r2 = new BasicRelationshipWithInverse<TargetType, SourceType>(
            toSourceRelationshipTypeName, targetName, targetNamePlural, sourceType);
        r1.inverseRelationshipType = r2;
        r2.inverseRelationshipType = r1;
        return r1;
    }

    public static <SourceType,TargetType> RelationshipType<SourceType,TargetType> newRelationshipOneway(
            String sourceName, String sourceNamePlural, Class<SourceType> sourceType, String toTargetRelationshipTypeName,   
            String targetName, String targetNamePlural, Class<TargetType> targetType) {
        return new BasicRelationshipOneWay<SourceType,TargetType>(
            sourceName, sourceNamePlural, sourceType, toTargetRelationshipTypeName,
            targetName, targetNamePlural, targetType);
    }
    
}
