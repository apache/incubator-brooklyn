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
package org.apache.brooklyn.api.location;

import javax.annotation.Nullable;

public interface OsDetails {

    /** The name of the operating system, e.g. "Debian" or "Red Hat Enterprise Linux Server" */
    @Nullable
    String getName();

    /**
     * The version of the operating system. Generally numeric (e.g. "6.3") but occasionally
     * alphabetic (e.g. Debian's "Squeeze").
     */
    @Nullable
    String getVersion();

    /** The operating system's architecture, e.g. "x86" or "x86_64" */
    @Nullable
    String getArch();

    boolean is64bit();

    boolean isWindows();
    boolean isLinux();
    boolean isMac();

}
