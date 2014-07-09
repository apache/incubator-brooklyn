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
package brooklyn.rest.resources;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Locale;

public abstract class DelegatingPrintStream extends PrintStream {
    
    public DelegatingPrintStream() {
        super(new IllegalOutputStream());
    }

    public static class IllegalOutputStream extends OutputStream {
        @Override public void write(int b) {
            throw new IllegalStateException("should not write to this output stream");
        }
        @Override public void write(byte[] b, int off, int len) {
            throw new IllegalStateException("should not write to this output stream");
        }
    }
    
    protected abstract PrintStream getDelegate();

    public int hashCode() {
        return getDelegate().hashCode();
    }

    public void write(byte[] b) throws IOException {
        getDelegate().write(b);
    }

    public boolean equals(Object obj) {
        return getDelegate().equals(obj);
    }

    public String toString() {
        return getDelegate().toString();
    }

    public void flush() {
        getDelegate().flush();
    }

    public void close() {
        getDelegate().close();
    }

    public boolean checkError() {
        return getDelegate().checkError();
    }

    public void write(int b) {
        getDelegate().write(b);
    }

    public void write(byte[] buf, int off, int len) {
        getDelegate().write(buf, off, len);
    }

    public void print(boolean b) {
        getDelegate().print(b);
    }

    public void print(char c) {
        getDelegate().print(c);
    }

    public void print(int i) {
        getDelegate().print(i);
    }

    public void print(long l) {
        getDelegate().print(l);
    }

    public void print(float f) {
        getDelegate().print(f);
    }

    public void print(double d) {
        getDelegate().print(d);
    }

    public void print(char[] s) {
        getDelegate().print(s);
    }

    public void print(String s) {
        getDelegate().print(s);
    }

    public void print(Object obj) {
        getDelegate().print(obj);
    }

    public void println() {
        getDelegate().println();
    }

    public void println(boolean x) {
        getDelegate().println(x);
    }

    public void println(char x) {
        getDelegate().println(x);
    }

    public void println(int x) {
        getDelegate().println(x);
    }

    public void println(long x) {
        getDelegate().println(x);
    }

    public void println(float x) {
        getDelegate().println(x);
    }

    public void println(double x) {
        getDelegate().println(x);
    }

    public void println(char[] x) {
        getDelegate().println(x);
    }

    public void println(String x) {
        getDelegate().println(x);
    }

    public void println(Object x) {
        getDelegate().println(x);
    }

    public PrintStream printf(String format, Object... args) {
        return getDelegate().printf(format, args);
    }

    public PrintStream printf(Locale l, String format, Object... args) {
        return getDelegate().printf(l, format, args);
    }

    public PrintStream format(String format, Object... args) {
        return getDelegate().format(format, args);
    }

    public PrintStream format(Locale l, String format, Object... args) {
        return getDelegate().format(l, format, args);
    }

    public PrintStream append(CharSequence csq) {
        return getDelegate().append(csq);
    }

    public PrintStream append(CharSequence csq, int start, int end) {
        return getDelegate().append(csq, start, end);
    }

    public PrintStream append(char c) {
        return getDelegate().append(c);
    }
    
}
