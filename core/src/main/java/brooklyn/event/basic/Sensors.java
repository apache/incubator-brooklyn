package brooklyn.event.basic;

import brooklyn.event.AttributeSensor;

import com.google.common.reflect.TypeToken;

public class Sensors {

    public static <T> AttributeSensor<T> newSensor(Class<T> type, String name) {
        return new BasicAttributeSensor<T>(type, name);
    }

    public static <T> AttributeSensor<T> newSensor(Class<T> type, String name, String description) {
        return new BasicAttributeSensor<T>(type, name, description);
    }

    public static <T> AttributeSensor<T> newSensor(TypeToken<T> type, String name, String description) {
        return new BasicAttributeSensor<T>(type, name, description);
    }

    public static AttributeSensor<String> newStringSensor(String name) {
        return newSensor(String.class, name);
    }

    public static AttributeSensor<String> newStringSensor(String name, String description) {
        return newSensor(String.class, name, description);
    }

    public static AttributeSensor<Integer> newIntegerSensor(String name) {
        return newSensor(Integer.class, name);
    }

    public static AttributeSensor<Integer> newIntegerSensor(String name, String description) {
        return newSensor(Integer.class, name, description);
    }

    public static AttributeSensor<Long> newLongSensor(String name) {
        return newSensor(Long.class, name);
    }

    public static AttributeSensor<Long> newLongSensor(String name, String description) {
        return newSensor(Long.class, name, description);
    }

    public static AttributeSensor<Double> newDoubleSensor(String name) {
        return newSensor(Double.class, name);
    }

    public static AttributeSensor<Double> newDoubleSensor(String name, String description) {
        return newSensor(Double.class, name, description);
    }

    public static AttributeSensor<Boolean> newBooleanSensor(String name) {
        return newSensor(Boolean.class, name);
    }

    public static AttributeSensor<Boolean> newBooleanSensor(String name, String description) {
        return newSensor(Boolean.class, name, description);
    }

}
