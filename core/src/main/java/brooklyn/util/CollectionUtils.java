package brooklyn.util;

import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated in 0.4; this unused code will be deleted; use guava where possible, e.g. ImmutableList.copyOf 
 */
@Deprecated
public class CollectionUtils {

    public static <T> List<T> asList(Iterable<T> i) {
        List<T> l = new ArrayList<T>();
        for (T ii: i) l.add(ii);
        return l;
    }
    
}
