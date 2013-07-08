package brooklyn.rest.transform;

import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationDefinition;
import brooklyn.rest.domain.LocationSpec;
import brooklyn.rest.domain.LocationSummary;
import com.google.common.collect.ImmutableMap;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.Set;

public class LocationTransformer {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(LocationTransformer.class);

    
    // LocationSummary
    public static LocationSummary newInstance(String id, LocationSpec locationSpec) {
        return new LocationSummary(
                id,
                locationSpec.getName(),
                locationSpec.getSpec(),
                copyConfigsExceptSensitiveKeys(locationSpec.getConfig().entrySet()),
                ImmutableMap.of("self", URI.create("/v1/locations/" + id)));
    }

    public static LocationSummary newInstance(LocationDefinition l) {
        return new LocationSummary(
                l.getId(),
                l.getName(),
                l.getSpec(),
                copyConfigsExceptSensitiveKeys(l.getConfig().entrySet()),
                ImmutableMap.of("self", URI.create("/v1/locations/" + l.getId())));
    }

    private static Map<String, String> copyConfigsExceptSensitiveKeys(@SuppressWarnings("rawtypes") Set entries) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (Object entryO : entries) {
            @SuppressWarnings("unchecked")
            Map.Entry<String, ?> entry = (Map.Entry<String, ?>) entryO;
            if (!Entities.isSecret(entry.getKey())) {
                builder.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
            }
        }
        return builder.build();
    }
}
