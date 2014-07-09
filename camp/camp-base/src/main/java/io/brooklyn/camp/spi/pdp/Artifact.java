package io.brooklyn.camp.spi.pdp;

import io.brooklyn.util.yaml.Yamls;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.ToStringBuilder;

import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class Artifact {

    String name;
    String description;
    String artifactType;
    
    ArtifactContent content;
    List<ArtifactRequirement> requirements;
    
    Map<String,Object> customAttributes;
    
    @SuppressWarnings("unchecked")
    public static Artifact of(Map<String, Object> artifact) {
        Map<String,Object> fields = MutableMap.copyOf(artifact);
        
        Artifact result = new Artifact();
        result.name = (String) fields.remove("name");
        result.description = (String) fields.remove("description");
        result.artifactType = (String) (String) Yamls.removeMultinameAttribute(fields, "artifactType", "type");
        
        result.content = ArtifactContent.of( fields.remove("content") );
        
        result.requirements = new ArrayList<ArtifactRequirement>();
        Object reqs = fields.remove("requirements");
        if (reqs instanceof Iterable) {
            for (Object req: (Iterable<Object>)reqs) {
                if (req instanceof Map) {
                    result.requirements.add(ArtifactRequirement.of((Map<String,Object>) req));
                } else {
                    throw new IllegalArgumentException("requirement should be a map, not "+req.getClass());
                }
            }
        } else if (reqs!=null) {
            // TODO "map" short form
            throw new IllegalArgumentException("artifacts body should be iterable, not "+reqs.getClass());
        }
        
        result.customAttributes = fields;
        
        return result;
    }
    
    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }
    public String getArtifactType() {
        return artifactType;
    }
    public ArtifactContent getContent() {
        return content;
    }
    public List<ArtifactRequirement> getRequirements() {
        return ImmutableList.copyOf(requirements);
    }
    public Map<String, Object> getCustomAttributes() {
        return ImmutableMap.copyOf(customAttributes);
    }
    
    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

}
