package brooklyn.rest.domain;

import java.net.URI;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonProperty;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

@Beta
public class AccessSummary {

    private final boolean locationProvisioningAllowed;
    private final Map<String, URI> links;

    public AccessSummary(
            @JsonProperty("locationProvisioningAllowed") boolean locationProvisioningAllowed,
            @JsonProperty("links") Map<String, URI> links
    ) {
        this.locationProvisioningAllowed = locationProvisioningAllowed;
        this.links = links == null ? ImmutableMap.<String, URI>of() : ImmutableMap.copyOf(links);
      }

    public boolean isLocationProvisioningAllowed() {
        return locationProvisioningAllowed;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AccessSummary)) return false;
        AccessSummary other = (AccessSummary) o;
        return locationProvisioningAllowed == other.isLocationProvisioningAllowed();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(locationProvisioningAllowed);
    }

    @Override
    public String toString() {
        return "AccessSummary{" +
                "locationProvisioningAllowed='" + locationProvisioningAllowed + '\'' +
                ", links=" + links +
                '}';
    }
}
