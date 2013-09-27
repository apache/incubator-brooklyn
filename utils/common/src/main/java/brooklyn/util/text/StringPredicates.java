package brooklyn.util.text;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class StringPredicates {

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
                if (input==null) return false;
                return input.toString().startsWith(prefix);
            }
        };
    }

    /** true if the object *is* a string starting with the given prefix */
    public static Predicate<Object> isStringStartingWith(final String prefix) {
        return new Predicate<Object>() {
            @Override
            public boolean apply(Object input) {
                if (input==null) return false;
                if (!(input instanceof CharSequence)) return false;
                return input.toString().startsWith(prefix);
            }
        };
    }

    // TODO globs, matches regex, etc ... add as you need them!
    
}
