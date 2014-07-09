package brooklyn.util.guava;

import com.google.common.base.Function;
import com.google.common.base.Functions;

public class Functionals {

    /** applies f1 to the input, then the result of that is passed to f2 (note opposite semantics to {@link Functions#compose(Function, Function)} */ 
    public static <A,B,C> Function<A,C> chain(final Function<A,? extends B> f1, final Function<B,C> f2) {
        return Functions.compose(f2, f1);
    }
    
    /** applies f1 to the input, then f2 to that result, then f3 to that result */
    public static <A,B,C,D> Function<A,D> chain(final Function<A,? extends B> f1, final Function<B,? extends C> f2, final Function<C,D> f3) {
        return chain(f1, chain(f2, f3));
    }
    
    /** applies f1 to the input, then f2 to that result, then f3 to that result, then f4 to that result */
    public static <A,B,C,D,E> Function<A,E> chain(final Function<A,? extends B> f1, final Function<B,? extends C> f2, final Function<C,? extends D> f3, final Function<D,E> f4) {
        return chain(f1, chain(f2, chain(f3, f4)));
    }

}
