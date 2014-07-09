package brooklyn.util.internal;

public class BooleanSystemProperty extends BasicDelegatingSystemProperty {
    public BooleanSystemProperty(String name) {
        super(name);
    }
    public boolean isEnabled() {
        // actually access system property!
        return Boolean.getBoolean(getPropertyName());
    }
}
