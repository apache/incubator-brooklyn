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
package org.apache.brooklyn.location.winrm;

import java.io.InputStream;
import java.util.Map;

/** Marks something which can run scripts. Called "Naive" because it hides too much of the complexity,
 * about script execution and other command-related tasks (put, etc).
 */
public interface NaiveWindowsScriptRunner {

    /** Runs a command and returns the result code */
    int executeNativeCommand(Map flags, String windowsCommand, String summaryForLogging);
    int executePsCommand(Map flags, String powerShellCommand, String summaryForLogging);
    Integer executeNativeOrPsCommand(Map flags, String regularCommand, String powershellCommand, String summaryForLogging, Boolean allowNoOp);

    int copyTo(InputStream source, String destination);
}
