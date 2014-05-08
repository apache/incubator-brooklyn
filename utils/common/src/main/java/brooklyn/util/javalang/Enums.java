package brooklyn.util.javalang;

import java.util.Arrays;

import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.text.StringFunctions;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class Enums {
    
    /** returns a function which given an enum, returns its <code>name()</code> function */
    public static Function<Enum<?>,String> enumValueNameFunction() {
        return new Function<Enum<?>,String>() {
            @Override
            public String apply(Enum<?> input) {
                return input.name();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T extends Enum<?>> T[] values(Class<T> type) {
        try {
            return (T[]) type.getMethod("values").invoke(null);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
    public static void checkAllEnumeratedIgnoreCase(Class<? extends Enum<?>> type, String ...explicitValues) {
        checkAllEnumeratedIgnoreCase(JavaClassNames.simpleClassName(type), values(type), explicitValues);
    }
    public static void checkAllEnumeratedIgnoreCase(String contextMessage, Enum<?>[] enumValues, String ...explicitValues) {
        MutableSet<String> enumValuesNames = MutableSet.copyOf(Iterables.transform(Iterables.transform(Arrays.asList(enumValues), enumValueNameFunction()), StringFunctions.toLowerCase()));
        MutableSet<String> explicitValuesSet = MutableSet.copyOf(Iterables.transform(Arrays.asList(explicitValues), StringFunctions.toLowerCase()));
        if (!enumValuesNames.equals(explicitValuesSet) ) {
            throw new IllegalStateException("Not all options for "+contextMessage+" are enumerated; "
                + "leftover enums = "+Sets.difference(enumValuesNames, explicitValuesSet)+"; "
                + "leftover values = "+Sets.difference(explicitValuesSet, enumValuesNames));
        }
    }
    
    public static <T extends Enum<?>> Maybe<T> valueOfIgnoreCase(Class<T> type, String givenValue) {
        return valueOfIgnoreCase(JavaClassNames.simpleClassName(type), values(type), givenValue);
    }
    public static <T extends Enum<?>> Maybe<T> valueOfIgnoreCase(String contextMessage, T[] enumValues, String givenValue) {
        if (Strings.isBlank(givenValue)) return Maybe.absent(new IllegalStateException("Value for "+contextMessage+" cannot be blank"));
        for (T v: enumValues)
            if (v.name().equalsIgnoreCase(givenValue)) return Maybe.of(v);
        return Maybe.absent(new IllegalStateException("Invalid value "+givenValue+" for "+contextMessage));
    }
    
}
