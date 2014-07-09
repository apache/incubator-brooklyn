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
package brooklyn.entity.basic.lifecycle;

import java.util.List;
import java.util.Map;

import brooklyn.util.task.ssh.SshTasks;

/** Marks something which can run scripts. Called "Naive" because it hides too much of the complexity,
 * about script execution and other ssh-related tasks (put, etc). The {@link SshTasks} approach seems better.
 * <p> 
 * Not gone so far as deprecating (yet, in 0.6.0) although we might.  Feedback welcome. 
 * @since 0.6.0 */
public interface NaiveScriptRunner {

    /** Runs a script and returns the result code */
    int execute(List<String> script, String summaryForLogging);

    /** Runs a script and returns the result code, supporting flags including:
     *  out, err as output/error streams;
     *  logPrefix, prefix string to put in log output;
     *  env, map of environment vars to pass to shell environment */
    int execute(Map flags, List<String> script, String summaryForLogging);

}
