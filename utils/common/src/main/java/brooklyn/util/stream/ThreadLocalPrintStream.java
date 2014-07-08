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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import org.apache.commons.io.output.TeeOutputStream;

public class ThreadLocalPrintStream extends DelegatingPrintStream {

    protected PrintStream defaultPrintStream;
    protected final ThreadLocal<PrintStream> customStream = new ThreadLocal<PrintStream>();
    
    public ThreadLocalPrintStream(PrintStream defaultPrintStream) {
        this.defaultPrintStream = defaultPrintStream;
    }
    
    @Override
    public PrintStream getDelegate() {
        PrintStream delegate = customStream.get();
        if (delegate!=null) return delegate;
        return defaultPrintStream;
    }

    /** sets the PrintStream that callers from this thread should see;
     * returns any previously custom PrintStream for this thread */
    public PrintStream setThreadLocalPrintStream(OutputStream stream) {
        PrintStream old = customStream.get();
        if (!(stream instanceof PrintStream))
            stream = new PrintStream(stream);
        customStream.set((PrintStream)stream);
        return old;
    }

    public PrintStream clearThreadLocalPrintStream() {
        PrintStream old = customStream.get();
        customStream.remove();
        if (old!=null) old.flush();
        return old;
    }
    
    /** creates a capturing context which eats the output to this stream, blocking the original target */
    public OutputCapturingContext capture() {
        return new OutputCapturingContext(this);
    }
    
    /** creates a capturing context which sees the output to this stream, without interrupting the original target */
    public OutputCapturingContext captureTee() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream toRestore = setThreadLocalPrintStream(new TeeOutputStream(getDelegate(), out));
        return new OutputCapturingContext(this, out, toRestore);
    }
    
    public static class OutputCapturingContext {
        protected final ThreadLocalPrintStream stream;
        protected final ByteArrayOutputStream out;
        protected final OutputStream streamToRestore;
        protected boolean finished = false;
        /** constructor which installs a ByteArrayOutputStream to this stream */
        public OutputCapturingContext(ThreadLocalPrintStream stream) {
            this.stream = stream;
            this.out = new ByteArrayOutputStream();
            this.streamToRestore = stream.setThreadLocalPrintStream(out);
        }
        /** constructor for a capturing context which is already installed */
        public OutputCapturingContext(ThreadLocalPrintStream stream, ByteArrayOutputStream capturingStream, OutputStream optionalStreamToRestore) {
            this.stream = stream;
            this.out = capturingStream;
            this.streamToRestore = optionalStreamToRestore;
        }
        public String getOutputSoFar() {
            return out.toString();
        }
        public String end() {
            if (streamToRestore!=null)
                stream.setThreadLocalPrintStream(streamToRestore);
            else
                stream.clearThreadLocalPrintStream();
            finished = true;
            return out.toString();
        }
        public boolean isActive() {
            return !finished;
        }
        public ByteArrayOutputStream getOutputStream() {
            return out;
        }
        @Override
        public String toString() {
            return getOutputSoFar();
        }
        public boolean isEmpty() {
            return out.size()==0;
        }
    }
    
    /** installs a thread local print stream to System.out if one is not already set;
     * caller may then #capture and #captureTee on it.
     * @return the ThreadLocalPrintStream which System.out is using */
    public synchronized static ThreadLocalPrintStream stdout() {
        PrintStream oldOut = System.out;
        if (oldOut instanceof ThreadLocalPrintStream) return (ThreadLocalPrintStream)oldOut;
        ThreadLocalPrintStream newOut = new ThreadLocalPrintStream(System.out);
        System.setOut(newOut);
        return newOut;
    }

    /** installs a thread local print stream to System.err if one is not already set;
     * caller may then #capture and #captureTee on it.
     * @return the ThreadLocalPrintStream which System.err is using */
    public synchronized static ThreadLocalPrintStream stderr() {
        PrintStream oldErr = System.err;
        if (oldErr instanceof ThreadLocalPrintStream) return (ThreadLocalPrintStream)oldErr;
        ThreadLocalPrintStream newErr = new ThreadLocalPrintStream(System.err);
        System.setErr(newErr);
        return newErr;
    }

}
