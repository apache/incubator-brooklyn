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
package brooklyn.demo.webapp.hello;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The remote hadoop needs access to a jar file containing the code.
 * We jump through some maven hoops to have that JAR in our classpath,
 * then on the web server we serialize that JAR then pass the filename
 * to the JobConf which gets sent to hadoop.
 */
public class SerializeHelloWorldHadoopJar {
    
    public static final Logger LOG = LoggerFactory.getLogger(SerializeHelloWorldHadoopJar.class);
    
    private static final String JAR_RESOURCE_NAME = "brooklyn-example-hello-world-hadoop-jar.jar";
    private static AtomicBoolean initialized = new AtomicBoolean(false);
    private static String jarFileName;
    
    public static void init() {
        if (initialized.get()) return;
        synchronized (initialized) {
            if (initialized.get()) return;
            try {
                URL u = SerializeHelloWorldHadoopJar.class.getClassLoader().getResource(JAR_RESOURCE_NAME);
                if (u==null) {
                    throw new FileNotFoundException("jar "+JAR_RESOURCE_NAME+" not on classpath");
                } else {
                    OutputStream out = new FileOutputStream("/tmp/brooklyn-example-hello-world-hadoop-jar.jar");
                    copy(u.openStream(), out);
                    out.close();
                    jarFileName = "/tmp/brooklyn-example-hello-world-hadoop-jar.jar";
                }
            } catch (Exception e) {
                LOG.warn("Cannot copy "+JAR_RESOURCE_NAME+"; hadoop will likely fail: "+e);
            }
            initialized.set(true);
        }
    }

    public static String getJarName() {
        init();
        return jarFileName;
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buf = new byte[1024];
        int bytesRead = input.read(buf);
        while (bytesRead != -1) {
            output.write(buf, 0, bytesRead);
            bytesRead = input.read(buf);
        }
        output.flush();
    }
    
}
