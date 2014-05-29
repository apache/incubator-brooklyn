package brooklyn.util.text;

import java.io.Serializable;
import java.util.Set;

import javax.annotation.Nullable;

import brooklyn.util.collections.MutableSet;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class StringPredicates {

    /**
     * @since 0.7.0
     */
    public static Predicate<CharSequence> isBlank() {
        return new Predicate<CharSequence>() {
            @Override
            public boolean apply(@Nullable CharSequence input) {
                return Strings.isBlank(input);
            }
        };
    }

    public static Predicate<CharSequence> containsLiteralCaseInsensitive(final String fragment) {
        return new Predicate<CharSequence>() {
            @Override
            public boolean apply(@Nullable CharSequence input) {
                return Strings.containsLiteralIgnoreCase(input, fragment);
            }
        };
    }

    public static Predicate<CharSequence> containsLiteral(final String fragment) {
        return new Predicate<CharSequence>() {
            @Override
            public boolean apply(@Nullable CharSequence input) {
                return Strings.containsLiteral(input, fragment);
            }
        };
    }
    
    public static Predicate<CharSequence> containsRegex(final String regex) {
        // "Pattern" ... what a bad name :)
        return Predicates.containsPattern(regex);
    }

    public static Predicate<CharSequence> startsWith(final String prefix) {
        return new Predicate<CharSequence>() {
            @Override
            public boolean apply(CharSequence input) {
                return (input != null) && input.toString().startsWith(prefix);
            }
        };
    }

    /** true if the object *is* a string starting with the given prefix */
    public static Predicate<Object> isStringStartingWith(final String prefix) {
        return new Predicate<Object>() {
            @Override
            public boolean apply(Object input) {
                return (input instanceof CharSequence) && input.toString().startsWith(prefix);
            }
        };
    }

    public static Predicate<CharSequence> equalToAny(Iterable<String> vals) {
        return new EqualToAny<CharSequence>(vals);
    }

    public static class EqualToAny<T> implements Predicate<T>, Serializable {
        private static final long serialVersionUID = 6209304291945204422L;
        private final Set<T> vals;
        
        public EqualToAny(Iterable<? extends T> vals) {
            this.vals = MutableSet.copyOf(vals); // so allows nulls
        }
        @Override
        public boolean apply(T input) {
            return vals.contains(input);
        }
        @Override
        public String toString() {
            return "equalToAny("+vals+")";
        }
    }
    
    // TODO globs, matches regex, etc ... add as you need them!
    
}
