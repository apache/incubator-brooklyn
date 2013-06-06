package brooklyn.internal.storage;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import com.google.common.annotations.Beta;

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
     * Creates a list backed by the storage-medium. If a list with this name has already been
     * created, then that existing list will be returned.
     * 
     * The returned list is not a live view. Changes are made by calling reference.set(), and
     * the view is refreshed by calling reference.get(). Changes are thread-safe, but callers
     * must be cafeful not to overwrite other's changes. For example, the code below could overwrite
     * another threads changes that are made to the map between the call to get() and the subsequent
     * call to set().
     * 
     * <pre>
     * {@code
     * Reference<List<String>> ref = storage.<String>createNonConcurrentList("myid");
     * List<String> newval = ImmutableList.<String>builder().addAll(ref.get()).add("another").builder();
     * ref.set(newval);
     * }
     * </pre>
     * 
     * @param id
     * @return
     */
    @Beta
    <T> Reference<List<T>> createNonConcurrentList(String id);
    
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
