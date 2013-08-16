package brooklyn.rest.domain;

import java.util.Map;

import javax.annotation.Nullable;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;

@Beta
public class LinkWithMetadata {

    // TODO remove 'metadata' and promote its contents to be top-level fields; then unmark as Beta
    
    private final String link;
    private final Map<String,Object> metadata;
    
    public LinkWithMetadata(
            @JsonProperty("link") String link, 
            @Nullable @JsonProperty("metadata") Map<String,?> metadata) {
        this.link = link;
        this.metadata = metadata==null ? ImmutableMap.<String,Object>of() : ImmutableMap.<String,Object>copyOf(metadata);
    }
    
    public String getLink() {
        return link;
    }
    
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((link == null) ? 0 : link.hashCode());
        result = prime * result + ((metadata == null) ? 0 : metadata.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LinkWithMetadata other = (LinkWithMetadata) obj;
        if (link == null) {
            if (other.link != null)
                return false;
        } else if (!link.equals(other.link))
            return false;
        if (metadata == null) {
            if (other.metadata != null)
                return false;
        } else if (!metadata.equals(other.metadata))
            return false;
        return true;
    }

    
}
