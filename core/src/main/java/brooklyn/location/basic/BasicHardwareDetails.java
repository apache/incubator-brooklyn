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
package brooklyn.location.basic;

import javax.annotation.concurrent.Immutable;

import com.google.common.base.Objects;

import brooklyn.location.HardwareDetails;

@Immutable
public class BasicHardwareDetails implements HardwareDetails {

    private final Integer cpuCount;
    private final Integer ram;

    public BasicHardwareDetails(Integer cpuCount, Integer ram) {
        this.cpuCount = cpuCount;
        this.ram = ram;
    }

    @Override
    public Integer getCpuCount() {
        return cpuCount;
    }

    @Override
    public Integer getRam() {
        return ram;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(HardwareDetails.class)
                .omitNullValues()
                .add("cpuCount", cpuCount)
                .add("ram", ram)
                .toString();
    }
}
