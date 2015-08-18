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
package org.apache.brooklyn.location.core;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.location.MachineLocationCustomizer;

public class RecordingMachineLocationCustomizer implements MachineLocationCustomizer {
    public static class Call {
        public final String methodName;
        public final List<?> args;
        
        public Call(String methodName, List<?> args) {
            this.methodName = checkNotNull(methodName);
            this.args = checkNotNull(args);
        }
        
        @Override
        public String toString() {
            return methodName+args;
        }
        
        @Override
        public int hashCode() {
            return Objects.hashCode(methodName, args);
        }
        
        @Override
        public boolean equals(Object other) {
            return (other instanceof RecordingMachineLocationCustomizer.Call) && 
                    methodName.equals(((RecordingMachineLocationCustomizer.Call)other).methodName) && 
                    args.equals(((RecordingMachineLocationCustomizer.Call)other).args);
        }
    }
    
    public final List<RecordingMachineLocationCustomizer.Call> calls = Lists.newCopyOnWriteArrayList();
    
    @Override
    public void customize(MachineLocation machine) {
        calls.add(new Call("customize", ImmutableList.of(machine)));
    }

    @Override
    public void preRelease(MachineLocation machine) {
        calls.add(new Call("preRelease", ImmutableList.of(machine)));
    }
}