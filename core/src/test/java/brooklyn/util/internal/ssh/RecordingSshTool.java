/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package brooklyn.util.internal.ssh;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class RecordingSshTool implements SshTool {
    
    public static class ExecCmd {
        public final Map<String,?> props;
        public final String summaryForLogging;
        public final List<String> commands;
        public final Map<?,?> env;
        
        ExecCmd(Map<String,?> props, String summaryForLogging, List<String> commands, Map env) {
            this.props = props;
            this.summaryForLogging = summaryForLogging;
            this.commands = commands;
            this.env = env;
        }
        
        @Override
        public String toString() {
            return "ExecCmd["+summaryForLogging+": "+commands+"; "+props+"; "+env+"]";
        }
    }
    
    public static List<ExecCmd> execScriptCmds = Lists.newCopyOnWriteArrayList();
    
    private boolean connected;
    
    public RecordingSshTool(Map<?,?> props) {
    }
    @Override public void connect() {
        connected = true;
    }
    @Override public void connect(int maxAttempts) {
        connected = true;
    }
    @Override public void disconnect() {
        connected = false;
    }
    @Override public boolean isConnected() {
        return connected;
    }
    @Override public int execScript(Map<String, ?> props, List<String> commands, Map<String, ?> env) {
        execScriptCmds.add(new ExecCmd(props, "", commands, env));
        return 0;
    }
    @Override public int execScript(Map<String, ?> props, List<String> commands) {
        return execScript(props, commands, ImmutableMap.<String,Object>of());
    }
    @Override public int execCommands(Map<String, ?> props, List<String> commands, Map<String, ?> env) {
        execScriptCmds.add(new ExecCmd(props, "", commands, env));
        return 0;
    }
    @Override public int execCommands(Map<String, ?> props, List<String> commands) {
        return execCommands(props, commands, ImmutableMap.<String,Object>of());
    }
    @Override public int copyToServer(Map<String, ?> props, File localFile, String pathAndFileOnRemoteServer) {
        return 0;
    }
    @Override public int copyToServer(Map<String, ?> props, InputStream contents, String pathAndFileOnRemoteServer) {
        return 0;
    }
    @Override public int copyToServer(Map<String, ?> props, byte[] contents, String pathAndFileOnRemoteServer) {
        return 0;
    }
    @Override public int copyFromServer(Map<String, ?> props, String pathAndFileOnRemoteServer, File local) {
        return 0;
    }
}