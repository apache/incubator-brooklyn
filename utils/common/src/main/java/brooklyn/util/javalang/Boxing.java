package brooklyn.util.javalang;

public class Boxing {

    public static boolean unboxSafely(Boolean ref, boolean valueIfNull) {
        if (ref==null) return valueIfNull;
        return ref.booleanValue();
    }
    
}
