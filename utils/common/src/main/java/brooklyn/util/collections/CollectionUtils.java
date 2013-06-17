package brooklyn.util.collections;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class CollectionUtils {

    @SuppressWarnings("rawtypes")
    public static <T> T last(Iterable<T> x) {
        if (x instanceof Collection) {
            int size = ((Collection) x).size();
            if (size==0) throw new NoSuchElementException("Collection is empty");
            if (x instanceof List)
                return ((List<T>) x).get(size-1);
        }
        return last(x.iterator());
    }

    public static <T> T last(Iterator<T> x) {
        T last = null;
        if (!x.hasNext())
            throw new NoSuchElementException("Iterator is empty");
        while (x.hasNext()) last = x.next();
        return last;
    }
    
}
