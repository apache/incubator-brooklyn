package brooklyn.util.text;

import javax.annotation.Nullable;

import com.google.common.base.Function;

public class StringFunctions {

    public static Function<String,String> append(final String suffix) {
        return new Function<String, String>() {
            @Override
            @Nullable
            public String apply(@Nullable String input) {
                if (input==null) return null;
                return input + suffix;
            }
        };
    }

}
