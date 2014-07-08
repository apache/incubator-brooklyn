/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.util.stream;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
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
import com.google.common.io.CharStreams;

/**
 * Methods to manage byte and character streams.
 *
 * @see com.google.common.io.ByteStreams
 * @see com.google.common.io.CharStreams
 */
public class Streams {

    private static final Logger log = LoggerFactory.getLogger(Streams.class);

    /** drop-in non-deprecated replacement for {@link Closeable}'s deprecated closeQuiety;
     * we may wish to review usages, particularly as we drop support for java 1.6,
     * but until then use this instead of the deprecated method */
    @Beta
    public static void closeQuietly(Closeable x) {
        try {
            if (x!=null)
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
        byte[] bytes = checkNotNull(contents, "contents").getBytes(Charsets.UTF_8);
        return KnownSizeInputStream.of(bytes);
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
        try {
            return ByteStreams.toByteArray(is);
        } catch (IOException ioe) {
            throw Exceptions.propagate(ioe);
        }
    }

    public static String readFullyString(InputStream is) {
        return readFully(reader(is));
    }
    
    public static String readFully(Reader is) {
        try {
            return CharStreams.toString(is);
        } catch (IOException ioe) {
            throw Exceptions.propagate(ioe);
        }
    }

    public static void copy(InputStream input, OutputStream output) {
        try {
            ByteStreams.copy(input, output);
            output.flush();
        } catch (IOException ioe) {
            throw Exceptions.propagate(ioe);
        }
    }

    public static void copy(Reader input, Writer output) {
        try {
            CharStreams.copy(input, output);
            output.flush();
        } catch (IOException ioe) {
            throw Exceptions.propagate(ioe);
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
        return byteArray(in.getBytes(Charsets.UTF_8));
    }

    public static ByteArrayOutputStream byteArray(byte[] in) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            stream.write(in);
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
