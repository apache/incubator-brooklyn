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
package brooklyn.event.feed.shell;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.PollConfig;
import brooklyn.event.feed.ssh.SshPollValue;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;

public class ShellPollConfig<T> extends PollConfig<SshPollValue, T, ShellPollConfig<T>> {

    private String command;
    private Map<String,String> env = Maps.newLinkedHashMap();
    private long timeout = -1;
    private File dir;
    private String input;

    public static final Predicate<SshPollValue> DEFAULT_SUCCESS = new Predicate<SshPollValue>() {
        @Override
        public boolean apply(@Nullable SshPollValue input) {
            return input != null && input.getExitStatus() == 0;
        }};

    public ShellPollConfig(AttributeSensor<T> sensor) {
        super(sensor);
        super.checkSuccess(DEFAULT_SUCCESS);
    }

    public ShellPollConfig(ShellPollConfig<T> other) {
        super(other);
        command = other.command;
        env = other.env;
        timeout = other.timeout;
        dir = other.dir;
        input = other.input;
    }
    
    public String getCommand() {
        return command;
    }
    
    public Map<String, String> getEnv() {
        return env;
    }

    public File getDir() {
        return dir;
    }

    public String getInput() {
        return input;
    }
    
    public long getTimeout() {
        return timeout;
    }
    
    public ShellPollConfig<T> command(String val) {
        this.command = val;
        return this;
    }

    public ShellPollConfig<T> env(String key, String val) {
        env.put(checkNotNull(key, "key"), checkNotNull(val, "val"));
        return this;
    }
    
    public ShellPollConfig<T> env(Map<String,String> val) {
        for (Map.Entry<String, String> entry : checkNotNull(val, "map").entrySet()) {
            env(entry.getKey(), entry.getValue());
        }
        return this;
    }
    
    public ShellPollConfig<T> dir(File val) {
        this.dir = val;
        return this;
    }
    
    public ShellPollConfig<T> input(String val) {
        this.input = val;
        return this;
    }
    
    public ShellPollConfig<T> timeout(long timeout) {
        return timeout(timeout, TimeUnit.MILLISECONDS);
    }
    
    public ShellPollConfig<T> timeout(long timeout, TimeUnit units) {
        this.timeout = units.toMillis(timeout);
        return this;
    }
}
