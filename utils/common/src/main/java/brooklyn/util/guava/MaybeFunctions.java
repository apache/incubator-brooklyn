package brooklyn.util.guava;

import com.google.common.base.Function;

public class MaybeFunctions {

    public static <T> Function<T, Maybe<T>> wrap() {
        return new Function<T, Maybe<T>>() {
            @Override
            public Maybe<T> apply(T input) {
                return Maybe.fromNullable(input);
            }
        };
    }

    public static <T> Function<Maybe<T>, T> get() {
        return new Function<Maybe<T>, T>() {
            @Override
            public T apply(Maybe<T> input) {
                return input.get();
            }
        };
    }

    public static <T> Function<Maybe<T>, T> or(final T value) {
        return new Function<Maybe<T>, T>() {
            @Override
            public T apply(Maybe<T> input) {
                return input.or(value);
            }
        };
    }

}
