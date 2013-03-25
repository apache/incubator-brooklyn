package brooklyn.util.javalang;

import java.util.concurrent.atomic.AtomicReference;

import com.google.common.base.Function;
import com.google.common.base.Objects;

public class Providers {

    public static <T> Provider<T> constant(T value) {
        return new ConstantProvider<T>(value);
    }
    
    public static class ConstantProvider<T> implements Provider<T> {
        final T value;
        public ConstantProvider(T value) { this.value = value; }
        @Override public T get() { return value; }
        @Override public int hashCode() { return Objects.hashCode(value); }
        @Override public boolean equals(Object other) { 
            if (!(other instanceof ConstantProvider)) return false;
            return Objects.equal(value, ((ConstantProvider<?>)other).value); 
        }
    }
 
    public static <T> AtomicProvider<T> atomic(AtomicReference<T> ref) {
        return new AtomicProvider<T>(ref);
    }

    public static class AtomicProvider<T> implements Provider<T> {
        final AtomicReference<T> ref;
        public AtomicProvider(AtomicReference<T> ref) { this.ref = ref; }
        @Override public T get() { return ref.get(); }
        public AtomicReference<T> getReference() { return ref; }
        @Override public int hashCode() { return Objects.hashCode(ref); }
        @Override public boolean equals(Object other) { 
            if (!(other instanceof AtomicProvider)) return false;
            return Objects.equal(ref, ((AtomicProvider<?>)other).ref); 
        }
    }

    public static <A,B> Provider<B> transform(final Provider<A> val, final Function<A,B> f) {
        return new Provider<B>() {
            @Override
            public B get() {
                return f.apply(val.get());
            }
        };
    }

}
