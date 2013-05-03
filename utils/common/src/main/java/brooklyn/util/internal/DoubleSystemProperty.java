package brooklyn.util.internal;

public class DoubleSystemProperty extends BasicDelegatingSystemProperty {
    public DoubleSystemProperty(String name) {
        super(name);
    }
    public double getValue() {
        return Double.parseDouble(delegate.getValue());
    }
}
