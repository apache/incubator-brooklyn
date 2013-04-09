package brooklyn.util;

import java.io.Serializable;

import javax.annotation.Nullable;

import com.google.common.base.Objects;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

public class Suppliers2 {

    /**
     * Returns a supplier that always supplies {@code instance}.
     * 
     * Use this instead of {@link Suppliers#ofInstance(Object)} if you need hashcode/equals
     * on the supplier such that they are equal if the instance being returned is always
     * equal.
     */
    public static <T> Supplier<T> ofInstance(@Nullable T instance) {
      return new SupplierOfInstance<T>(instance);
    }

    private static class SupplierOfInstance<T> implements Supplier<T>, Serializable {
        private static final long serialVersionUID = -3869109503516800339L;
        final T instance;

        SupplierOfInstance(@Nullable T instance) {
            this.instance = instance;
        }

        @Override public T get() {
            return instance;
        }

        @Override public String toString() {
            return "Suppliers.ofInstance(" + instance + ")";
        }
        
        @Override
        public int hashCode() {
            return Objects.hashCode(instance);
        }
        
        @Override
        public boolean equals(Object obj) {
            return (obj instanceof SupplierOfInstance) && Objects.equal(instance, ((SupplierOfInstance<?>)obj).instance);
        }
    }
}
