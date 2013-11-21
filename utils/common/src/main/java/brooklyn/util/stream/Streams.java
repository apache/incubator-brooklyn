package brooklyn.util.stream;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;

import com.google.common.annotations.Beta;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;

public class Streams {

    private static final Logger log = LoggerFactory.getLogger(Streams.class);
    
    /** drop-in non-deprecated replacement for {@link Closeable}'s deprecated closeQuiety;
     * we may wish to review usages, particularly as we drop support for java 1.6,
     * but until then use this instead of the deprecated method */
    @Beta
    public static void closeQuietly(Closeable x) {
        try {
            x.close();
        } catch (Exception e) {
            if (log.isDebugEnabled())
                log.debug("Error closing (ignored) "+x+": "+e);
        }
    }

    /** @deprecated since 0.7.0 use {@link #newInputStreamWithContents(String)} */ @Deprecated
    public static InputStream fromString(String contents) {
        return newInputStreamWithContents(contents);
    }
    
    public static InputStream newInputStreamWithContents(String contents) {
        try {
            byte[] bytes = checkNotNull(contents, "contents").getBytes(Charsets.UTF_8);
            InputSupplier<ByteArrayInputStream> supplier = ByteStreams.newInputStreamSupplier(bytes);
            InputStream stream = supplier.getInput();
            return stream;
        } catch (IOException ioe) {
            if (log.isDebugEnabled()) log.debug("Error creating InputStream from String: " + ioe.getMessage());
            throw Exceptions.propagate(ioe);
        }
    }

    public static Reader newReaderWithContents(String contents) {
        return new StringReader(contents);
    }
    
    public static Reader reader(InputStream stream) {
        return new InputStreamReader(stream);
    }
    
    public static Reader reader(InputStream stream, Charset charset) {
        return new InputStreamReader(stream, charset);
    }

    /** reads the input stream fully, returning a byte array; throws unchecked exception on failure;
     *  to get a string, use <code>readFully(reader(is))</code> or <code>readFullyString(is)</code> */
    public static byte[] readFully(InputStream is) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        copy(is, out);
        return out.toByteArray();
    }

    public static String readFullyString(InputStream is) {
        return readFully(reader(is));
    }
    
    public static String readFully(Reader is) {
        StringWriter out = new StringWriter();
        copy(is, out);
        return out.toString();
    }

    public static void copy(InputStream input, OutputStream output) {
        try {
            byte[] buf = new byte[1024];
            int bytesRead = input.read(buf);
            while (bytesRead != -1) {
                output.write(buf, 0, bytesRead);
                bytesRead = input.read(buf);
            }
            output.flush();
        } catch (Exception e) {
            Exceptions.propagate(e);
        }
    }

    public static void copy(Reader input, Writer output) {
        try {
            char[] buf = new char[1024];
            int bytesRead = input.read(buf);
            while (bytesRead != -1) {
                output.write(buf, 0, bytesRead);
                bytesRead = input.read(buf);
            }
            output.flush();
        } catch (Exception e) {
            Exceptions.propagate(e);
        }
    }

    public static Supplier<Integer> sizeSupplier(final ByteArrayOutputStream src) {
        Preconditions.checkNotNull(src);
        return new Supplier<Integer>() {
            @Override
            public Integer get() {
                return src.size();
            }
        };
    }

    public static ByteArrayOutputStream byteArrayOfString(String in) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            stream.write(in.getBytes());
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
        return stream;
    }

    public static boolean logStreamTail(Logger log, String message, ByteArrayOutputStream stream, int max) {
        if (stream!=null && stream.size()>0) {
            String streamS = stream.toString();
            if (max>=0 && streamS.length()>max)
                streamS = "... "+streamS.substring(streamS.length()-max);
            log.info(message+":\n"+streamS);
            return true;
        }
        return false;
    }

}
