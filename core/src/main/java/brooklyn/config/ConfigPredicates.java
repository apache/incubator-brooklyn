package brooklyn.config;

import java.util.regex.Pattern;

import javax.annotation.Nullable;

import brooklyn.util.text.WildcardGlobs;

import com.google.common.base.Predicate;

public class ConfigPredicates {

    public static Predicate<ConfigKey<?>> startingWith(final String prefix) {
        return new Predicate<ConfigKey<?>>() {
            @Override
            public boolean apply(@Nullable ConfigKey<?> input) {
                return input.getName().startsWith(prefix);
            }
        };
    }

    public static Predicate<ConfigKey<?>> matchingGlob(final String glob) {
        return new Predicate<ConfigKey<?>>() {
            @Override
            public boolean apply(@Nullable ConfigKey<?> input) {
                return WildcardGlobs.isGlobMatched(glob, input.getName());
            }
        };
    }

    public static Predicate<ConfigKey<?>> matchingRegex(final String regex) {
        final Pattern p = Pattern.compile(regex);
        return new Predicate<ConfigKey<?>>() {
            @Override
            public boolean apply(@Nullable ConfigKey<?> input) {
                return p.matcher(input.getName()).matches();
            }
        };
    }

    public static Predicate<ConfigKey<?>> nameMatching(final Predicate<String> filter) {
        return new Predicate<ConfigKey<?>>() {
            @Override
            public boolean apply(@Nullable ConfigKey<?> input) {
                return filter.apply(input.getName());
            }
        };
    }
    
}
