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
package brooklyn.util.javalang;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Threads {

    private static final Logger log = LoggerFactory.getLogger(Threads.class);
    
    public static Thread addShutdownHook(final Runnable task) {
        Thread t = new Thread("shutdownHookThread") {
            public void run() {
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("Failed to execute shutdownhook", e);
                }
            }
        };
        Runtime.getRuntime().addShutdownHook(t);
        return t;
    }
    
    public static boolean removeShutdownHook(Thread hook) {
        try {
            return Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException e) {
            // probably shutdown in progress
            log.debug("cannot remove shutdown hook "+hook+": "+e);
            return false;
        }
    }

}
