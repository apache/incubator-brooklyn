package brooklyn.util.internal;

public class IntegerSystemProperty extends BasicDelegatingSystemProperty {
    public IntegerSystemProperty(String name) {
        super(name);
    }
    public int getValue() {
        return Integer.parseInt(delegate.getValue());
    }
}
