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
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ScriptExecutionSummary implements Serializable {

    private static final long serialVersionUID = -7707936602991185960L;

    private final Object result;
    private final String problem;
    private final String stdout;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScriptExecutionSummary)) return false;
        ScriptExecutionSummary that = (ScriptExecutionSummary) o;
        return Objects.equals(result, that.result) &&
                Objects.equals(problem, that.problem) &&
                Objects.equals(stdout, that.stdout) &&
                Objects.equals(stderr, that.stderr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(result, problem, stdout, stderr);
    }

    @Override
    public String toString() {
        return "ScriptExecutionSummary{" +
                "result=" + result +
                ", problem='" + problem + '\'' +
                ", stdout='" + stdout + '\'' +
                ", stderr='" + stderr + '\'' +
                '}';
    }
}
