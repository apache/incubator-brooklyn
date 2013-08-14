package brooklyn.event.feed.http;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class JsonFunctions {

    private JsonFunctions() {} // instead use static utility methods
    
    public static Function<String, JsonElement> asJson() {
        return new Function<String, JsonElement>() {
            @Override public JsonElement apply(String input) {
                return new JsonParser().parse(input);
            }
        };
    }

    public static <T> Function<JsonElement, List<T>> forEach(final Function<JsonElement, T> func) {
        return new Function<JsonElement, List<T>>() {
            @Override public List<T> apply(JsonElement input) {
                JsonArray array = (JsonArray) input;
                List<T> result = Lists.newArrayList();
                for (int i = 0; i < array.size(); i++) {
                    result.add(func.apply(array.get(i)));
                }
                return result;
            }
        };
    }

    public static Function<JsonElement, JsonElement> walk(String elements) {
        Iterable<String> iterable = Splitter.on('.').split(elements);
        return walk(iterable);
    }

    public static Function<JsonElement, JsonElement> walk(Iterable<String> elements) {
        String[] array = Lists.newArrayList(elements).toArray(new String[0]);
        return walk(array);
    }

    public static Function<JsonElement, JsonElement> walk(final String... elements) {
        return new Function<JsonElement, JsonElement>() {
            @Override public JsonElement apply(JsonElement input) {
                JsonElement curr = input;
                for (String element : elements) {
                    curr = curr.getAsJsonObject().get(element);
                }
                return curr;
            }
        };
    }
    
    @SuppressWarnings("unchecked")
    public static <T> Function<JsonElement, T> cast(final Class<T> expected) {
        return new Function<JsonElement, T>() {
            @Override public T apply(JsonElement input) {
                if (input == null) {
                    return (T) null;
                } else if (input.isJsonNull()) {
                    return (T) null;
                } else if (expected == boolean.class || expected == Boolean.class) {
                    return (T) (Boolean) input.getAsBoolean();
                } else if (expected == char.class || expected == Character.class) {
                    return (T) (Character) input.getAsCharacter();
                } else if (expected == byte.class || expected == Byte.class) {
                    return (T) (Byte) input.getAsByte();
                } else if (expected == short.class || expected == Short.class) {
                    return (T) (Short) input.getAsShort();
                } else if (expected == int.class || expected == Integer.class) {
                    return (T) (Integer) input.getAsInt();
                } else if (expected == long.class || expected == Long.class) {
                    return (T) (Long) input.getAsLong();
                } else if (expected == float.class || expected == Float.class) {
                    return (T) (Float) input.getAsFloat();
                } else if (expected == double.class || expected == Double.class) {
                    return (T) (Double) input.getAsDouble();
                } else if (expected == BigDecimal.class) {
                    return (T) input.getAsBigDecimal();
                } else if (expected == BigInteger.class) {
                    return (T) input.getAsBigInteger();
                } else if (Number.class.isAssignableFrom(expected)) {
                    // TODO Will result in a class-cast if it's an unexpected sub-type of Number not handled above
                    return (T) input.getAsNumber();
                } else if (expected == String.class) {
                    return (T) input.getAsString();
                } else if (expected.isArray()) {
                    JsonArray array = input.getAsJsonArray();
                    Class<?> componentType = expected.getComponentType();
                    if (JsonElement.class.isAssignableFrom(componentType)) {
                        JsonElement[] result = new JsonElement[array.size()];
                        for (int i = 0; i < array.size(); i++) {
                            result[i] = array.get(i);
                        }
                        return (T) result;
                    } else {
                        Object[] result = (Object[]) Array.newInstance(componentType, array.size());
                        for (int i = 0; i < array.size(); i++) {
                            result[i] = cast(componentType).apply(array.get(i));
                        }
                        return (T) result;
                    }
                } else {
                    throw new IllegalArgumentException("Cannot cast json element to type "+expected);
                }
            }
        };
    }
}
