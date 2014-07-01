package io.brooklyn.camp.spi.pdp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;

import brooklyn.util.collections.MutableMap;

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
    
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
