package brooklyn.util.guava;

import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import javax.annotation.Nullable;

import brooklyn.util.javalang.JavaClassNames;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;

/** Like Guava Optional but permitting null and permitting errors to be thrown. */
public abstract class Maybe<T> implements Serializable {

    private static final long serialVersionUID = -6372099069863179019L;

    public static <T> Maybe<T> absent() {
        return new Absent<T>();
    }

    /** Creates an absent whose get throws an {@link IllegalStateException} with the indicated cause.
     * Both stack traces (the cause and the callers) are provided, which can be quite handy. */
    public static <T> Maybe<T> absent(final Throwable cause) {
        return new Absent<T>() {
            private static final long serialVersionUID = 1L;
            @Override
            public T get() {
                throw new IllegalStateException(cause);
            }
        };
    }
    
    public static <T> Maybe<T> of(@Nullable T value) {
        return new Present<T>(value);
    }

    /** like {@link Optional#fromNullable(Object)}, returns absent if the argument is null */
    public static <T> Maybe<T> fromNullable(@Nullable T value) {
        if (value==null) return absent();
        return new Present<T>(value);
    }

    public static <T> Maybe<T> of(final Optional<T> value) {
        if (value.isPresent()) return new AbstractPresent<T>() {
            private static final long serialVersionUID = -5735268814211401356L;
            @Override
            public T get() {
                return value.get();
            }
        };
        return absent();
    }
    
    public static <T> Maybe<T> of(final Supplier<T> value) {
        return new AbstractPresent<T>() {
            private static final long serialVersionUID = -5735268814211401356L;
            @Override
            public T get() {
                return value.get();
            }
        };
    }
    
    public static <T> Maybe<T> next(Iterator<T> iterator) {
        return iterator.hasNext() ? Maybe.of(iterator.next()) : Maybe.<T>absent();
    }
    
    public abstract boolean isPresent();
    public abstract T get();
    
    public boolean isAbsent() {
        return !isPresent(); 
    }
    public boolean isAbsentOrNull() {
        return !isPresentAndNonNull();
    }
    public boolean isPresentAndNonNull() {
        return isPresent() && get()!=null;
    }
    
    public T or(T nextValue) {
        if (isPresent()) return get();
        return nextValue;
    }

    public Maybe<T> or(Maybe<T> nextValue) {
        if (isPresent()) return this;
        return nextValue;
    }

    public T or(Supplier<T> nextValue) {
        if (isPresent()) return get();
        return nextValue.get();
    }

    public T orNull() {
        if (isPresent()) return get();
        return null;
    }
    
    public Set<T> asSet() {
        if (isPresent()) return ImmutableSet.of(get());
        return Collections.emptySet();
    }
    
    public <V> Maybe<V> transform(final Function<? super T, V> f) {
        if (isPresent()) return new AbstractPresent<V>() {
            private static final long serialVersionUID = 325089324325L;
            public V get() {
                return f.apply(Maybe.this.get());
            }
        };
        return absent();
    }
    
    public static class Absent<T> extends Maybe<T> {
        private static final long serialVersionUID = -757170462010887057L;
        @Override
        public boolean isPresent() {
            return false;
        }
        @Override
        public T get() {
            throw new IllegalStateException();
        }
    }

    public static abstract class AbstractPresent<T> extends Maybe<T> {
        private static final long serialVersionUID = -2266743425340870492L;
        protected AbstractPresent() {
        }
        @Override
        public boolean isPresent() {
            return true;
        }
    }

    public static class Present<T> extends AbstractPresent<T> {
        private static final long serialVersionUID = 436799990500336015L;
        private final T value;
        protected Present(T value) {
            this.value = value;
        }
        @Override
        public T get() {
            return value;
        }
    }

    @Override
    public String toString() {
        return JavaClassNames.simpleClassName(this)+"["+(isPresent()?"value="+get():"")+"]";
    }

}
