package brooklyn.util.text;

import javax.annotation.Nullable;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class StringPredicates {

    public static Predicate<CharSequence> containsLiteralCaseInsensitive(final String fragment) {
        return new Predicate<CharSequence>() {
            @Override
            public boolean apply(@Nullable CharSequence input) {
                return Strings.containsLiteralCaseInsensitive(input, fragment);
            }
        };
    }

    public static Predicate<CharSequence> containsRegex(final String regex) {
        // "Pattern" ... what a bad name :)
        return Predicates.containsPattern(regex);
    }
    
    // TODO globs, matches regex, etc ... add as you need them!
    
}
