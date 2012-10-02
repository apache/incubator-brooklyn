package brooklyn.util.internal;

public class StringSystemProperty {

    public StringSystemProperty(String name) {
        this.propertyName = name;
    }

    private final String propertyName;

    public String getPropertyName() {
        return propertyName;
    }

    public boolean isAvailable() {
        String property = System.getProperty(getPropertyName());
        return property!=null;
    }
    public boolean isNonEmpty() {
        String property = System.getProperty(getPropertyName());
        return property!=null && !property.equals("");
    }
    public String getValue() {
        return System.getProperty(getPropertyName());
    }
    @Override
    public String toString() {
        return getPropertyName()+(isAvailable()?"="+getValue():"(unset)");
    }
}
