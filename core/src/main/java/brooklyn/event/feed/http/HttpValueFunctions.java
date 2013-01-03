package brooklyn.event.feed.http;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.gson.JsonElement;

public class HttpValueFunctions {

    private HttpValueFunctions() {} // instead use static utility methods
    
    public static Function<HttpPollValue, Integer> responseCode() {
        return new Function<HttpPollValue, Integer>() {
            @Override public Integer apply(HttpPollValue input) {
                return input.getResponseCode();
            }
        };
    }

    public static Function<HttpPollValue, Boolean> responseCodeEquals(final int expected) {
        return chain(HttpValueFunctions.responseCode(), Functions.forPredicate(Predicates.equalTo(expected)));
    }
    
    public static Function<HttpPollValue, String> stringContentsFunction() {
        return new Function<HttpPollValue, String>() {
            @Override public String apply(HttpPollValue input) {
                // TODO Charset?
                return new String(input.getContent());
            }
        };
    }
    
    public static Function<HttpPollValue, JsonElement> jsonContents() {
        return chain(stringContentsFunction(), JsonFunctions.asJson());
    }
    
    public static <T> Function<HttpPollValue, T> jsonContents(String element, Class<T> expected) {
        return jsonContents(new String[] {element}, expected);
    }
    
    public static <T> Function<HttpPollValue, T> jsonContents(String[] elements, Class<T> expected) {
        return chain(jsonContents(), JsonFunctions.walk(elements), JsonFunctions.cast(expected));
    }
    
    public static <A,B,C> Function<A,C> chain(final Function<A,? extends B> f1, final Function<B,C> f2) {
        return new Function<A,C>() {
            @Override public C apply(@Nullable A input) {
                return f2.apply(f1.apply(input));
            }
        };
    }
    
    public static <A,B,C,D> Function<A,D> chain(final Function<A,? extends B> f1, final Function<B,? extends C> f2, final Function<C,D> f3) {
        return new Function<A,D>() {
            @Override public D apply(@Nullable A input) {
                return f3.apply(f2.apply(f1.apply(input)));
            }
        };
    }
}
