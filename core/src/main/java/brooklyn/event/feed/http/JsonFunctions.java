package brooklyn.event.feed.http;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import javax.annotation.Nullable;

import brooklyn.util.guava.Functionals;
import brooklyn.util.guava.Maybe;
import brooklyn.util.guava.MaybeFunctions;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

    
    /** as {@link #walkM(Iterable)} taking a single string consisting of a dot separated path */
    public static Function<JsonElement, JsonElement> walk(String elementOrDotSeparatedElements) {
        return walk( Splitter.on('.').split(elementOrDotSeparatedElements) );
    }

    /** as {@link #walkM(Iterable)} taking a series of strings (dot separators not respected here) */
    public static Function<JsonElement, JsonElement> walk(final String... elements) {
        return walk(Arrays.asList(elements));
    }

    /** returns a function which traverses the supplied path of entries in a json object (maps of maps of maps...), 
     * @throws NoSuchElementException if any path is not present as a key in that map */
    public static Function<JsonElement, JsonElement> walk(final Iterable<String> elements) {
        // could do this instead, pointing at Maybe for this, and for walkN, but it's slightly less efficient
//      return Functionals.chain(MaybeFunctions.<JsonElement>wrap(), walkM(elements), MaybeFunctions.<JsonElement>get());
        
        return new Function<JsonElement, JsonElement>() {
            @Override public JsonElement apply(JsonElement input) {
                JsonElement curr = input;
                for (String element : elements) {
                    JsonObject jo = curr.getAsJsonObject();
                    curr = jo.get(element);
                    if (curr==null) 
                        throw new NoSuchElementException("No element '"+element+" in JSON, when walking "+elements);
                }
                return curr;
            }
        };
    }

    
    /** as {@link #walk(String)} but if any element is not found it simply returns null */
    public static Function<JsonElement, JsonElement> walkN(@Nullable String elements) {
        return walkN( Splitter.on('.').split(elements) );
    }

    /** as {@link #walk(String...))} but if any element is not found it simply returns null */
    public static Function<JsonElement, JsonElement> walkN(final String... elements) {
        return walkN(Arrays.asList(elements));
    }

    /** as {@link #walk(Iterable))} but if any element is not found it simply returns null */
    public static Function<JsonElement, JsonElement> walkN(final Iterable<String> elements) {
        return new Function<JsonElement, JsonElement>() {
            @Override public JsonElement apply(JsonElement input) {
                JsonElement curr = input;
                for (String element : elements) {
                    if (curr==null) return null;
                    JsonObject jo = curr.getAsJsonObject();
                    curr = jo.get(element);
                }
                return curr;
            }
        };
    }

    /** as {@link #walk(String))} and {@link #walk(Iterable)} */
    public static Function<Maybe<JsonElement>, Maybe<JsonElement>> walkM(@Nullable String elements) {
        return walkM( Splitter.on('.').split(elements) );
    }

    /** as {@link #walk(String...))} and {@link #walk(Iterable)} */
    public static Function<Maybe<JsonElement>, Maybe<JsonElement>> walkM(final String... elements) {
        return walkM(Arrays.asList(elements));
    }

    /** as {@link #walk(Iterable))} but working with objects which {@link Maybe} contain {@link JsonElement},
     * simply preserving a {@link Maybe#absent()} object if additional walks are requested upon it
     * (cf jquery) */
    public static Function<Maybe<JsonElement>, Maybe<JsonElement>> walkM(final Iterable<String> elements) {
        return new Function<Maybe<JsonElement>, Maybe<JsonElement>>() {
            @Override public Maybe<JsonElement> apply(Maybe<JsonElement> input) {
                Maybe<JsonElement> curr = input;
                for (String element : elements) {
                    if (curr.isAbsent()) return curr;
                    JsonObject jo = curr.get().getAsJsonObject();
                    JsonElement currO = jo.get(element);
                    if (currO==null) return Maybe.absent("No element '"+element+" in JSON, when walking "+elements);
                    curr = Maybe.of(currO);
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
    
    public static <T> Function<Maybe<JsonElement>, T> castM(final Class<T> expected) {
        return Functionals.chain(MaybeFunctions.<JsonElement>get(), cast(expected));
    }
    
    public static <T> Function<Maybe<JsonElement>, T> castM(final Class<T> expected, final T defaultValue) {
        return new Function<Maybe<JsonElement>, T>() {
            @Override
            public T apply(Maybe<JsonElement> input) {
                if (input.isAbsent()) return defaultValue;
                return cast(expected).apply(input.get());
            }
        };
    }

}
