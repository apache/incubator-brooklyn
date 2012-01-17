package brooklyn.util;

import java.util.ArrayList;
import java.util.List;

public class CollectionUtils {

    public static <T> List<T> asList(Iterable<T> i) {
        List<T> l = new ArrayList<T>();
        for (T ii: i) l.add(ii);
        return l;
    }
    
}
