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
package io.brooklyn.camp.brooklyn.spi.dsl.parse;

import java.util.List;

import com.google.common.collect.ImmutableList;

public class FunctionWithArgs {
    private final String function;
    private final List<Object> args;
    
    public FunctionWithArgs(String function, List<Object> args) {
        this.function = function;
        this.args = args==null ? null : ImmutableList.copyOf(args);
    }
    
    public String getFunction() {
        return function;
    }
    
    /**
     * arguments (typically {@link QuotedString} or more {@link FunctionWithArgs}).
     * 
     * null means it is a function in a map key which expects map value to be the arguments -- specified without parentheses;
     * empty means parentheses already applied, with 0 args.
     */
    public List<Object> getArgs() {
        return args;
    }
    
    @Override
    public String toString() {
        return function+(args==null ? "" : args);
    }

    public Object arg(int i) {
        return args.get(i);
    }
    
}