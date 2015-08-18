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
package org.apache.brooklyn.util.stream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.stream.StreamGobbler;
import org.testng.annotations.Test;

public class StreamGobblerTest {
    private String NL = Os.LINE_SEPARATOR;

    @Test
    public void testGobbleStream() throws Exception {
        byte[] bytes = new byte[] {'a','b','c'};
        InputStream stream = new ByteArrayInputStream(bytes);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StreamGobbler gobbler = new StreamGobbler(stream, out, null);
        gobbler.start();
        try {
            gobbler.join(10*1000);
            assertFalse(gobbler.isAlive());
            assertEquals(new String(out.toByteArray()), "abc" + NL);
        } finally {
            gobbler.close();
            gobbler.interrupt();
        }
    }
    
    @Test
    public void testGobbleMultiLineBlockingStream() throws Exception {
        PipedOutputStream pipedOutputStream = new PipedOutputStream();
        PipedInputStream stream = new PipedInputStream(pipedOutputStream);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StreamGobbler gobbler = new StreamGobbler(stream, out, null);
        gobbler.start();
        try {
            pipedOutputStream.write("line1\n".getBytes());
            assertEqualsEventually(out, "line1" + NL);

            pipedOutputStream.write("line2\n".getBytes());
            assertEqualsEventually(out, "line1" + NL + "line2" + NL);

            pipedOutputStream.write("line".getBytes());
            pipedOutputStream.write("3\n".getBytes());
            assertEqualsEventually(out, "line1" + NL + "line2" + NL + "line3" + NL);

            pipedOutputStream.close();
            
            gobbler.join(10*1000);
            assertFalse(gobbler.isAlive());
            assertEquals(new String(out.toByteArray()), "line1" + NL + "line2" + NL + "line3" + NL);
        } finally {
            gobbler.close();
            gobbler.interrupt();
        }
    }
    
    private void assertEqualsEventually(final ByteArrayOutputStream out, final String expected) {
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertEquals(new String(out.toByteArray()), expected);
            }});
    }
}
