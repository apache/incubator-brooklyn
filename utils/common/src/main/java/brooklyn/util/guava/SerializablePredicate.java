package brooklyn.util.guava;

import java.io.Serializable;

import com.google.common.base.Predicate;

public interface SerializablePredicate<T> extends Predicate<T>, Serializable {
}
