package brooklyn.util.stream;

import java.io.Closeable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    
}
