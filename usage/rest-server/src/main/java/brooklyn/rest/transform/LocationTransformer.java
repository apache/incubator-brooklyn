package brooklyn.rest.transform;

import java.net.URI;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.basic.BasicLocationDefinition;
import brooklyn.location.basic.LocationInternal;
import brooklyn.management.ManagementContext;
import brooklyn.rest.domain.LocationSpec;
import brooklyn.rest.domain.LocationSummary;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableMap;

public class LocationTransformer {

    private static final Logger log = LoggerFactory.getLogger(LocationTransformer.LocationDetailLevel.class);
    
    public static enum LocationDetailLevel { NONE, LOCAL_EXCLUDING_SECRET, FULL_EXCLUDING_SECRET, FULL_INCLUDING_SECRET }
    
    /** @deprecated since 0.7.0 use method taking management context and detail specifier */
    @Deprecated
    public static LocationSummary newInstance(String id, LocationSpec locationSpec) {
        return newInstance(null, id, locationSpec, LocationDetailLevel.LOCAL_EXCLUDING_SECRET);
    }
    public static LocationSummary newInstance(ManagementContext mgmt, String id, LocationSpec locationSpec, LocationDetailLevel level) {
        Set<?> config = locationSpec.getConfig().entrySet();
        if (level==LocationDetailLevel.FULL_EXCLUDING_SECRET || level==LocationDetailLevel.FULL_INCLUDING_SECRET) {
            LocationDefinition ld = new BasicLocationDefinition(id, locationSpec.getName(), locationSpec.getSpec(), locationSpec.getConfig());
            Location ll = mgmt.getLocationRegistry().resolve(ld);
            if (ll!=null) config = ll.getAllConfig(true).entrySet();
        }
        return new LocationSummary(
                id,
                locationSpec.getName(),
                locationSpec.getSpec(),
                null,
                copyConfig(config, level),
                ImmutableMap.of("self", URI.create("/v1/locations/" + id)));
    }

    /** @deprecated since 0.7.0 use method taking management context and detail specifier */
    public static LocationSummary newInstance(LocationDefinition l) {
        return newInstance(null, l, LocationDetailLevel.LOCAL_EXCLUDING_SECRET);
    }
    public static LocationSummary newInstance(ManagementContext mgmt, LocationDefinition l, LocationDetailLevel level) {
        Set<?> config = l.getConfig().entrySet();
        if (level==LocationDetailLevel.FULL_EXCLUDING_SECRET || level==LocationDetailLevel.FULL_INCLUDING_SECRET) {
            Location ll = mgmt.getLocationRegistry().resolve(l);
            if (ll!=null) config = ll.getAllConfig(true).entrySet();
        }

        return new LocationSummary(
                l.getId(),
                l.getName(),
                l.getSpec(),
                null,
                copyConfig(config, level),
                ImmutableMap.of("self", URI.create("/v1/locations/" + l.getId())));
    }

    private static Map<String, ?> copyConfig(@SuppressWarnings("rawtypes") Set entries, LocationDetailLevel level) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        if (level!=LocationDetailLevel.NONE) {
            for (Object entryO : entries) {
                @SuppressWarnings("unchecked")
                Map.Entry<String, ?> entry = (Map.Entry<String, ?>) entryO;
                if (level==LocationDetailLevel.FULL_INCLUDING_SECRET || !Entities.isSecret(entry.getKey())) {
                    builder.put(entry.getKey(), WebResourceUtils.getValueForDisplay(entry.getValue(), true, false));
                }
            }
        }
        return builder.build();
    }

    public static LocationSummary newInstance(ManagementContext mgmt, Location l, LocationDetailLevel level) {
        String spec = null;
        String specId = null;
        Location lp = l;
        while (lp!=null && (spec==null || specId==null)) {
            // walk parent locations
            // TODO not sure this is the best strategy, or if it's needed, as the spec config is inherited anyway... 
            if (spec==null)
                spec = Strings.toString( lp.getAllConfig(true).get(LocationInternal.ORIGINAL_SPEC.getName()) );
            if (specId==null) {
                LocationDefinition ld = null;
                // prefer looking it up by name as this loads the canonical definition
                if (spec!=null) ld = mgmt.getLocationRegistry().getDefinedLocationByName(spec);
                if (ld==null && spec!=null && spec.startsWith("named:")) 
                    ld = mgmt.getLocationRegistry().getDefinedLocationByName(Strings.removeFromStart(spec, "named:"));
                if (ld==null) ld = mgmt.getLocationRegistry().getDefinedLocationById(lp.getId());
                if (ld!=null) {
                    if (spec==null) spec = ld.getSpec();
                    specId = ld.getId();
                }
            }
            lp = lp.getParent();
        }
        if (specId==null && spec!=null) {
            // fall back to attempting to resolve it
            Location ll = mgmt.getLocationRegistry().resolveIfPossible(spec);
            if (ll!=null) specId = ll.getId();
        }
        
        Map<String, ?> config = level!=LocationDetailLevel.NONE ? null : copyConfig(l.getAllConfig(level!=LocationDetailLevel.LOCAL_EXCLUDING_SECRET).entrySet(), level);
        
        return new LocationSummary(
            l.getId(),
            l.getDisplayName(),
            spec,
            l.getClass().getName(),
            config,
            MutableMap.of("self", URI.create("/v1/locations/" + l.getId()))
                .addIfNotNull("parent", l.getParent()!=null ? URI.create("/v1/locations/"+l.getParent().getId()) : null)
                .addIfNotNull("spec", specId!=null ? URI.create("/v1/locations/"+specId) : null)
                .toImmutable() );
    }
    
}
