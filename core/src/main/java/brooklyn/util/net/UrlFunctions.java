package brooklyn.util.net;

import java.net.URI;
import java.net.URISyntaxException;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Throwables;

public class UrlFunctions {

    public static final Function<String,URI> URI_FROM_STRING = new Function<String,URI>() {
        @Override @Nullable 
        public URI apply(@Nullable String input) { 
            try {
                if (input==null) return null;
                return new URI(input);
            } catch (URISyntaxException e) {
                throw Throwables.propagate(e);
            } 
        }
    };
}
