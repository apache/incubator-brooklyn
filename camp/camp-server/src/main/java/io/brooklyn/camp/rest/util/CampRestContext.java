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
package io.brooklyn.camp.rest.util;

import javax.servlet.ServletContext;

import com.google.common.base.Preconditions;

import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.CampServer;

public class CampRestContext {

    private final ServletContext servletContext;
    private CampPlatform platform;
    private DtoFactory dto;
    
    public CampRestContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    public synchronized CampPlatform camp() {
        if (platform!=null) return platform;
        platform = (CampPlatform) servletContext.getAttribute(CampServer.CAMP_PLATFORM_ATTRIBUTE);
        return Preconditions.checkNotNull(platform, "CAMP platform instance not available from ServletContext");
    }

    public DtoFactory dto() {
        if (dto!=null) return dto;
        dto = (DtoFactory) servletContext.getAttribute(CampServer.DTO_FACTORY);
        return Preconditions.checkNotNull(dto, "CAMP DTO factory instance not available from ServletContext");
    }
    
}
