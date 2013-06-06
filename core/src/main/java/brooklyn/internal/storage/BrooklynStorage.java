package brooklyn.internal.storage;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public interface BrooklynStorage {

    // TODO Add createList, if it is required

    Object get(String id);

    Object put(String id, Object value);

    /**
     * Creates a reference to a long, backed by the storage-medium. 
     * If already exists with this id, returns it; otherwise creates a new atomic long.
     */
    public AtomicLong createAtomicLong(String id);

    /**
     * Creates a reference to a value, backed by the storage-medium. If a reference with this 
     * name has already been created, then that existing reference will be returned.
     * 
     * The returned reference is a live view: changes made to the reference will be persisted, 
     * and changes that others make will be reflected in the reference.
     * 
     * The reference is thread-safe. No additional synchronization is required when getting/setting
     * the reference.
     * 
     * @param id
     * @return
     */
    <T> Reference<T> createReference(String id);

    /**
     * Creates a set backed by the storage-medium. If a set with this name has already been
     * created, then that existing set will be returned.
     * 
     * The returned set is a live view: changes made to the set will be persisted, and changes 
     * that others make will be reflected in the set.
     * 
     * The set is thread-safe: {@link Set#iterator()} will iterate over a snapshot view of the
     * contents.
     * 
     * @param id
     * @return
     */
    <T> Set<T> createSet(String id);

    /**
     * Creates a map backed by the storage-medium. If a map with this name has already been
     * created, then that existing map will be returned.
     * 
     * The returned map is a live view: changes made to the map will be persisted, and changes 
     * that others make will be reflected in the map.
     * 
     * The map is thread-safe: {@link Map#keySet()} etc will iterate over a snapshot view of the
     * contents.
     * 
     * @param id
     * @return
     */
    <K,V> Map<K,V> createMap(String id);
}
