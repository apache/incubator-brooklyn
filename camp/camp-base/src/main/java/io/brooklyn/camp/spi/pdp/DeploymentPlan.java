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
package io.brooklyn.camp.spi.pdp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Maybe;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class DeploymentPlan {

    String name;
    String origin;
    String description;
    
    List<Artifact> artifacts;
    List<Service> services;
    Map<String,Object> customAttributes;

    @SuppressWarnings("unchecked")
    public static DeploymentPlan of(Map<String,Object> root) {
        Map<String,Object> attrs = MutableMap.copyOf(root);
        
        DeploymentPlan result = new DeploymentPlan();
        result.name = (String) attrs.remove("name");
        result.description = (String) attrs.remove("description");
        result.origin = (String) attrs.remove("origin");
        // TODO version
        
        result.services = new ArrayList<Service>();
        Object services = attrs.remove("services");
        if (services instanceof Iterable) {
            for (Object service: (Iterable<Object>)services) {
                if (service instanceof Map) {
                    result.services.add(Service.of((Map<String,Object>) service));
                } else {
                    throw new IllegalArgumentException("service should be map, not "+service.getClass());
                }
            }
        } else if (services!=null) {
            // TODO "map" short form
            throw new IllegalArgumentException("artifacts body should be iterable, not "+services.getClass());
        }
        
        result.artifacts = new ArrayList<Artifact>();
        Object artifacts = attrs.remove("artifacts");
        if (artifacts instanceof Iterable) {
            for (Object artifact: (Iterable<Object>)artifacts) {
                if (artifact instanceof Map) {
                    result.artifacts.add(Artifact.of((Map<String,Object>) artifact));
                } else {
                    throw new IllegalArgumentException("artifact should be map, not "+artifact.getClass());
                }
            }
        } else if (artifacts!=null) {
            // TODO "map" short form
            throw new IllegalArgumentException("artifacts body should be iterable, not "+artifacts.getClass());
        }
        
        result.customAttributes = attrs;
        
        return result;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getOrigin() {
        return origin;
    }

    public List<Artifact> getArtifacts() {
        return ImmutableList.copyOf(artifacts);
    }

    public List<Service> getServices() {
        return ImmutableList.copyOf(services);
    }

    public Map<String, Object> getCustomAttributes() {
        return ImmutableMap.copyOf(customAttributes);
    }

    /**
     * Returns a present {@link Maybe} of the custom attribute with the given name if the attribute is
     * non-null and is an instance of the given type. Otherwise returns absent.
     * <p/>
     * Does not remove the attribute from the custom attribute map.
     */
    @SuppressWarnings("unchecked")
    public <T> Maybe<T> getCustomAttribute(String attributeName, Class<T> type, boolean throwIfTypeMismatch) {
        Object attribute = customAttributes.get(attributeName);
        if (attribute == null) {
            return Maybe.absent("Custom attributes does not contain " + attributeName);
        } else if (!type.isAssignableFrom(attribute.getClass())) {
            String message = "Custom attribute " + attributeName + " is not of expected type: " +
                    "expected=" + type.getName() + " actual=" + attribute.getClass().getName();
            if (throwIfTypeMismatch) {
                throw new IllegalArgumentException(message);
            }
            return Maybe.absent(message);
        } else {
            return Maybe.of((T) attribute);
        }
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
