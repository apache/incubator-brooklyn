package brooklyn.internal.storage;

import java.util.List;
import java.util.Map;

import com.google.common.annotations.Beta;

public interface BrooklynStorage {

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
    <T> Reference<T> getReference(String id);

    /**
     * Creates a list backed by the storage-medium. If a list with this name has already been
     * created, then that existing list will be returned.
     * 
     * The returned list is not a live view. Changes are made by calling reference.set(), and
     * the view is refreshed by calling reference.get(). Changes are thread-safe, but callers
     * must be careful not to overwrite other's changes. For example, the code below could overwrite
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
     * TODO Aled says: Is getNonConcurrentList necessary?
     *   The purpose of this method, rather than just using
     *   {@code Reference ref = getReference(id); ref.set(ImmutableList.of())}
     *   is to allow control of the serialization of the things inside the list 
     *   (e.g. switching the Location object to serialize a proxy object of some sort). 
     *   I don't want us to have to do deep inspection of every object being added to any map/ref. 
     *   Feels like we can use normal serialization unless the top-level object matches an 
     *   instanceof for special things like Entity, Location, etc.
     * 
     * Peter responds:
     *   What I'm a bit scared of is that we need to write some kind of meta serialization mechanism 
     *   on top of the mechanisms provided by e.g. Hazelcast or Infinispan. Hazelcast has a very 
     *   extensive serialization library where you can plug in all kinds of serialization mechanisms.
     * 
     * @param id
     * @return
     */
    @Beta
    <T> Reference<List<T>> getNonConcurrentList(String id);
    
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
    <K,V> Map<K,V> getMap(String id);

    /**
     * Removes the data stored against this id, whether it is a map, ref or whatever.
     */
    void remove(String id);

    /**
     * Terminates the BrooklynStorage.
     */
    void terminate();
}
