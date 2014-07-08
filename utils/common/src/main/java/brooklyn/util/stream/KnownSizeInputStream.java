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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/** an input stream, whose size we know */
public class KnownSizeInputStream extends InputStream {

    public static KnownSizeInputStream of(String contents) {
        return of(contents.getBytes());
    }

    public static KnownSizeInputStream of(byte[] contents) {
        return new KnownSizeInputStream(new ByteArrayInputStream(contents), contents.length);
    }

    private final long length;
    private final InputStream target;
    
    public KnownSizeInputStream(InputStream target, long length) {
        this.target = checkNotNull(target, "target");
        this.length = length;
    }
    
    public long length() {
        return length;
    }
    
    public InputStream getTarget() {
        return target;
    }

    @Override
    public int read() throws IOException {
        return target.read();
    }

    @Override
    public int hashCode() {
        return target.hashCode();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return target.read(b);
    }

    @Override
    public boolean equals(Object obj) {
        return target.equals(obj);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return target.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
        return target.skip(n);
    }

    @Override
    public int available() throws IOException {
        return target.available();
    }

    @Override
    public String toString() {
        return target.toString();
    }

    @Override
    public void close() throws IOException {
        target.close();
    }

    @Override
    public void mark(int readlimit) {
        target.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
        target.reset();
    }

    @Override
    public boolean markSupported() {
        return target.markSupported();
    }
}
