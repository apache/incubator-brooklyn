package brooklyn.util.internal;

public class BasicDelegatingSystemProperty {
    protected final StringSystemProperty delegate;

    public BasicDelegatingSystemProperty(String name) {
        delegate = new StringSystemProperty(name);
    }
    public String getPropertyName() {
        return delegate.getPropertyName();
    }
    public boolean isAvailable() {
        return delegate.isAvailable();
    }
    public String toString() {
        return delegate.toString();
    }
}
