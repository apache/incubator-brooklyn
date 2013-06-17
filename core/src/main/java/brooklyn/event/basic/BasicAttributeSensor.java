package brooklyn.event.basic;

import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;

import com.google.common.reflect.TypeToken;

/**
 * A {@link Sensor} describing an attribute change.
 */
@SuppressWarnings("serial")
public class BasicAttributeSensor<T> extends BasicSensor<T> implements AttributeSensor<T> {
    private static final long serialVersionUID = -2493209215974820300L;

    public BasicAttributeSensor(Class<T> type, String name) {
        this(type, name, name);
    }
    
    public BasicAttributeSensor(Class<T> type, String name, String description) {
        super(type, name, description);
    }
    
    public BasicAttributeSensor(TypeToken<T> typeToken, String name) {
        this(typeToken, name, name);
    }
    
    public BasicAttributeSensor(TypeToken<T> typeToken, String name, String description) {
        super(typeToken, name, description);
    }
    
//    public static class StringAttributeSensor extends BasicAttributeSensor<String> {
//        public StringAttributeSensor(String name) {
//            super(String.class, name);
//        }
//        
//        public StringAttributeSensor(String name, String description) {
//            super(String.class, name, description);
//        }
//    }
//    
//    public static class DoubleAttributeSensor extends BasicAttributeSensor<Double> {
//        public DoubleAttributeSensor(String name) {
//            super(Double.class, name);
//        }
//        
//        public DoubleAttributeSensor(String name, String description) {
//            super(Double.class, name, description);
//        }
//    }
//
//    public static class IntegerAttributeSensor extends BasicAttributeSensor<Integer> {
//        public IntegerAttributeSensor(String name) {
//            super(Integer.class, name);
//        }
//        
//        public IntegerAttributeSensor(String name, String description) {
//            super(Integer.class, name, description);
//        }
//    }
//
//    public static class LongAttributeSensor extends BasicAttributeSensor<Long> {
//        public LongAttributeSensor(String name) {
//            super(Long.class, name);
//        }
//        
//        public LongAttributeSensor(String name, String description) {
//            super(Long.class, name, description);
//        }
//    }
//
//    public static class BooleanAttributeSensor extends BasicAttributeSensor<Boolean> {
//        public BooleanAttributeSensor(String name) {
//            super(Boolean.class, name);
//        }
//        
//        public BooleanAttributeSensor(String name, String description) {
//            super(Boolean.class, name, description);
//        }
//    }

}
