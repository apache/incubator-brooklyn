package brooklyn.rest.transform;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.rest.domain.LocationSpec;
import brooklyn.rest.domain.LocationSummary;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableMap;

public class LocationTransformer {

    // LocationSummary
    public static LocationSummary newInstance(String id, LocationSpec locationSpec) {
        return new LocationSummary(
                id,
                locationSpec.getName(),
                locationSpec.getSpec(),
                copyConfigsForDisplayExcludingSensitiveKeys(locationSpec.getConfig().entrySet()),
                ImmutableMap.of("self", URI.create("/v1/locations/" + id)));
    }

    public static LocationSummary newInstance(LocationDefinition l) {
        return new LocationSummary(
                l.getId(),
                l.getName(),
                l.getSpec(),
                copyConfigsForDisplayExcludingSensitiveKeys(l.getConfig().entrySet()),
                ImmutableMap.of("self", URI.create("/v1/locations/" + l.getId())));
    }

    private static Map<String, ?> copyConfigsForDisplayExcludingSensitiveKeys(@SuppressWarnings("rawtypes") Set entries) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        for (Object entryO : entries) {
            @SuppressWarnings("unchecked")
            Map.Entry<String, ?> entry = (Map.Entry<String, ?>) entryO;
            if (!Entities.isSecret(entry.getKey())) {
                builder.put(entry.getKey(), WebResourceUtils.getValueForDisplay(entry.getValue(), true, false));
            }
        }
        return builder.build();
    }

    public static LocationSummary newInstance(Location l, boolean abbreviated) {
        String spec = Strings.toString( l.getAllConfig(true).get("spec") );
        return new LocationSummary(
            l.getId(),
            l.getDisplayName(),
            // TODO a link to the spec would be nice. for now, revert to class if no parent is set, so at least we have some info!
            // would be nice to have full type info, and in general for this to be aligned with the recent way of doing locations
            spec!=null ? spec : l.getParent()!=null ? null : l.getClass().getName(),
            abbreviated ? null : copyConfigsForDisplayExcludingSensitiveKeys(l.getAllConfig(true).entrySet()),
            MutableMap.of("self", URI.create("/v1/locations/" + l.getId()))
                .addIfNotNull("parent", l.getParent()!=null ? URI.create("/v1/locations/" + l.getParent().getId()) : null)
                .toImmutable() );
    }
    
}
