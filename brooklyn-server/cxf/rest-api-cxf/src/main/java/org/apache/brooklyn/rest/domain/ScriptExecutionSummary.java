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
package org.apache.brooklyn.rest.domain;

import java.io.Serializable;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

public class ScriptExecutionSummary implements Serializable {

    private static final long serialVersionUID = -7707936602991185960L;
    
    @JsonSerialize(include = Inclusion.NON_NULL)
    private final Object result;
    @JsonSerialize(include = Inclusion.NON_EMPTY)
    private final String problem;
    @JsonSerialize(include = Inclusion.NON_EMPTY)
    private final String stdout;
    @JsonSerialize(include = Inclusion.NON_EMPTY)
    private final String stderr;

    public ScriptExecutionSummary(
            @JsonProperty("result") Object result, 
            @JsonProperty("problem") String problem, 
            @JsonProperty("stdout") String stdout, 
            @JsonProperty("stderr") String stderr) {
        super();
        this.result = result;
        this.problem = problem;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public Object getResult() {
        return result;
    }

    public String getProblem() {
        return problem;
    }

    public String getStderr() {
        return stderr;
    }

    public String getStdout() {
        return stdout;
    }
}
