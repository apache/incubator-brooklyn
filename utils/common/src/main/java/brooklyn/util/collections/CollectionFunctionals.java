package brooklyn.util.collections;

import java.util.Arrays;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/** things which it seems should be in guava, but i can't find 
 * @author alex */
public class CollectionFunctionals {

    public static Supplier<Integer> sizeSupplier(final Iterable<?> collection) {
        return new Supplier<Integer>() {
            @Override
            public Integer get() {
                return Iterables.size(collection);
            }
        };
    }
    
    /** default guava Equals predicate will reflect order of target, and will fail when matching against a list;
     * this treats them both as sets */
    public static Predicate<Iterable<?>> equalsSetOf(Object... target) {
        return equalsSet(Arrays.asList(target));
    }
    public static Predicate<Iterable<?>> equalsSet(final Iterable<?> target) {
        return new Predicate<Iterable<?>>() {
            @Override
            public boolean apply(@Nullable Iterable<?> input) {
                if (input==null) return false;
                return Sets.newHashSet(target).equals(Sets.newHashSet(input));
            }
        };
    }
    
}
