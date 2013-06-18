package brooklyn.location;

import java.util.List;
import java.util.Map;

/**
 * The registry of the sorts of locations that brooklyn knows about. Given a
 * {@LocationDefinition} or a {@link String} representation of a spec, this can
 * be used to create a {@link Location} instance.
 */
@SuppressWarnings("rawtypes")
public interface LocationRegistry {

    public Map<String,LocationDefinition> getDefinedLocations();
    
    public LocationDefinition getDefinedLocation(String id);
    public LocationDefinition getDefinedLocationByName(String name);

    /** adds or updates the given defined location */
    public void updateDefinedLocation(LocationDefinition l);

    /** removes the defined location from the registry (applications running there are unaffected) */
    public void removeDefinedLocation(String id);

    /** returns fully populated (config etc) location from the given definition */
    public Location resolve(LocationDefinition l);
    
    /** See {@link #resolve(String, Map)} (with no options) */
    public Location resolve(String spec);
    
    /** Returns true/false depending whether spec seems like a valid location */
    public boolean canResolve(String spec);
    
    /** Returns a location created from the given spec, which might correspond to a definition, or created on-the-fly.
     * Optional flags can be passed through to underlying the location. */
    public Location resolve(String spec, Map locationFlags);
    
    /**
     * As {@link #resolve(String)} but works with a collection of location specs.
     * <p>
     * Usually given a collection of string specs.
     * Also supports comma-separated lists as a single spec.
     * <p>
     * For legacy compatibility this also accepts nested lists, but that is deprecated
     * (and triggers a warning).
     */
    public List<Location> resolve(Iterable<?> spec);
    
    public Map getProperties();
}
