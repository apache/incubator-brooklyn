package brooklyn.util.text;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

/** wraps a call to {@link String#format(String, Object...)} in a toString, i.e. using %s syntax,
 * useful for places where we want deferred evaluation 
 * (e.g. as message to {@link Preconditions} to skip concatenation when not needed) */
public class FormattedString {
    private final String pattern;
    private final Object[] args;
    public FormattedString(String pattern, Object[] args) {
        this.pattern = pattern;
        this.args = args;
    }
    @Override
    public String toString() {
        return String.format(pattern, args);
    }
    public String getPattern() {
        return pattern;
    }
    public Object[] getArgs() {
        return args;
    }
    public Supplier<String> supplier() {
        return Strings.toStringSupplier(this);
    }
}
