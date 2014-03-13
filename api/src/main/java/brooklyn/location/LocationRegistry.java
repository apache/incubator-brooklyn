package brooklyn.location;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.google.common.annotations.Beta;

/**
 * The registry of the sorts of locations that brooklyn knows about. Given a
 * {@LocationDefinition} or a {@link String} representation of a spec, this can
 * be used to create a {@link Location} instance.
 */
@SuppressWarnings("rawtypes")
public interface LocationRegistry {

    /** map of ID (possibly randomly generated) to the definition (spec, name, id, and props; 
     * where spec is the spec as defined, for instance possibly another named:xxx location) */
    public Map<String,LocationDefinition> getDefinedLocations();
    
    /** returns a LocationDefinition given its ID (usually a random string), or null if none */
    public LocationDefinition getDefinedLocationById(String id);
    
    /** returns a LocationDefinition given its name (e.g. for named locations, supply the bit after the "named:" prefix), 
     * or null if none */
    public LocationDefinition getDefinedLocationByName(String name);

    /** adds or updates the given defined location */
    public void updateDefinedLocation(LocationDefinition l);

    /** removes the defined location from the registry (applications running there are unaffected) */
    public void removeDefinedLocation(String id);

    /** returns fully populated (config etc) location from the given definition, 
     * currently by creating it but TODO creation can be a leak so all current 'resolve' methods should be carefully used! */
    public Location resolve(LocationDefinition l);

    /** efficiently returns for inspection only a fully populated (config etc) location from the given definition; 
     * the value might be unmanaged so it is not meant for any use other than inspection,
     * but callers should prefer this when they don't wish to create a new location which will be managed in perpetuity!
     * 
     * @since 0.7.0, but beta and likely to change as the semantics of this class are tuned */
    @Beta
    public Location resolveForPeeking(LocationDefinition l);

    /** returns fully populated (config etc) location from the given definition */
    public Location resolve(LocationDefinition l, Map<?,?> locationFlags);
    
    /** See {@link #resolve(String, Map)} (with no options) */
    public Location resolve(String spec);
    
    /** Returns true/false depending whether spec seems like a valid location,
     * that is it has a chance of being resolved (depending on the spec) but NOT guaranteed;
     * see {@link #resolveIfPossible(String)} which has stronger guarantees */
    public boolean canMaybeResolve(String spec);
    
    /** Returns a location created from the given spec, which might correspond to a definition, or created on-the-fly.
     * Optional flags can be passed through to underlying the location. 
     * @throws NoSuchElementException if the spec cannot be resolved */
    public Location resolve(String spec, Map locationFlags);
    
    /** as {@link #resolve(String)} but returning null (never throwing) */
    public Location resolveIfPossible(String spec);

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
