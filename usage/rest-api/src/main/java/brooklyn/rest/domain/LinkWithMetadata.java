package brooklyn.rest.domain;

import java.util.Map;

import com.google.common.collect.ImmutableMap;

public class LinkWithMetadata {

    String link;
    Map<String,Object> metadata;
    
    public LinkWithMetadata(String link, Map<String,Object> metadata) {
        this.link = link;
        this.metadata = metadata==null ? ImmutableMap.<String,Object>of() : ImmutableMap.<String,Object>copyOf(metadata);
    }
    
    public String getLink() {
        return link;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
}
