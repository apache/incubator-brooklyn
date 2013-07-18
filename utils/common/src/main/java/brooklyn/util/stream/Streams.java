package brooklyn.util.stream;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;

public class Streams {

    private static final Logger log = LoggerFactory.getLogger(Streams.class);
    
    public static void closeQuietly(Closeable x) {
        try {
            x.close();
        } catch (Exception e) {
            if (log.isDebugEnabled())
                log.debug("Error closing (ignored) "+x+": "+e);
        }
    }

    public static InputStream fromString(String input) {
        try {
            byte[] bytes = checkNotNull(input, "input").getBytes(Charsets.UTF_8);
            InputSupplier<ByteArrayInputStream> supplier = ByteStreams.newInputStreamSupplier(bytes);
            InputStream stream = supplier.getInput();
            return stream;
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) log.debug("Error creating InputStream from String: " + ioe.getMessage());
            throw Exceptions.propagate(ioe);
        }
    }
}
