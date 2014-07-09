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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

public class ReaderInputStream extends InputStream {

    /** Source Reader */
    private Reader in;

    private String encoding = System.getProperty("file.encoding");

    private byte[] slack;

    private int begin;

    /**
     * Construct a <{@link ReaderInputStream}
     * for the specified {@link Reader}.
     *
     * @param reader   {@link Reader}; must not be {@code null}.
     */
    public ReaderInputStream(Reader reader) {
        in = reader;
    }

    /**
     * Construct a {@link ReaderInputStream}
     * for the specified {@link Reader},
     * with the specified encoding.
     *
     * @param reader     non-null {@link Reader}.
     * @param encoding   non-null {@link String} encoding.
     */
    public ReaderInputStream(Reader reader, String encoding) {
        this(reader);
        if (encoding == null) {
            throw new IllegalArgumentException("encoding must not be null");
        } else {
            this.encoding = encoding;
        }
    }

    /**
     * Reads from the {@link Reader}, returning the same value.
     *
     * @return the value of the next character in the {@link Reader}.
     *
     * @exception IOException if the original {@link Reader} fails to be read
     */
    public synchronized int read() throws IOException {
        if (in == null) {
            throw new IOException("Stream Closed");
        }

        byte result;
        if (slack != null && begin < slack.length) {
            result = slack[begin];
            if (++begin == slack.length) {
                slack = null;
            }
        } else {
            byte[] buf = new byte[1];
            if (read(buf, 0, 1) <= 0) {
                result = -1;
            }
            result = buf[0];
        }

        if (result < -1) {
            result += 256;
        }

        return result;
    }

    /**
     * Reads from the {@link Reader} into a byte array
     *
     * @param b  the byte array to read into
     * @param off the offset in the byte array
     * @param len the length in the byte array to fill
     * @return the actual number read into the byte array, -1 at
     *         the end of the stream
     * @exception IOException if an error occurs
     */
    public synchronized int read(byte[] b, int off, int len)
        throws IOException {
        if (in == null) {
            throw new IOException("Stream Closed");
        }

        while (slack == null) {
            char[] buf = new char[len]; // might read too much
            int n = in.read(buf);
            if (n == -1) {
                return -1;
            }
            if (n > 0) {
                slack = new String(buf, 0, n).getBytes(encoding);
                begin = 0;
            }
        }

        if (len > slack.length - begin) {
            len = slack.length - begin;
        }

        System.arraycopy(slack, begin, b, off, len);

        if ((begin += len) >= slack.length) {
            slack = null;
        }

        return len;
    }

    /**
     * Marks the read limit of the StringReader.
     *
     * @param limit the maximum limit of bytes that can be read before the
     *              mark position becomes invalid
     */
    public synchronized void mark(final int limit) {
        try {
            in.mark(limit);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe.getMessage());
        }
    }


    /**
     * @return   the current number of bytes ready for reading
     * @exception IOException if an error occurs
     */
    public synchronized int available() throws IOException {
        if (in == null) {
            throw new IOException("Stream Closed");
        }
        if (slack != null) {
            return slack.length - begin;
        }
        if (in.ready()) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * @return false - mark is not supported
     */
    public boolean markSupported () {
        return false;   // would be imprecise
    }

    /**
     * Resets the StringReader.
     *
     * @exception IOException if the StringReader fails to be reset
     */
    public synchronized void reset() throws IOException {
        if (in == null) {
            throw new IOException("Stream Closed");
        }
        slack = null;
        in.reset();
    }

    /**
     * Closes the Stringreader.
     *
     * @exception IOException if the original StringReader fails to be closed
     */
    public synchronized void close() throws IOException {
        if (in != null) {
            in.close();
            slack = null;
            in = null;
        }
    }
}