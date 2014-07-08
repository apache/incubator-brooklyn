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
package io.brooklyn.camp.dto;

import io.brooklyn.camp.rest.util.DtoFactory;
import io.brooklyn.camp.spi.ApplicationComponentTemplate;

public class ApplicationComponentTemplateDto extends ResourceDto {

    // defined as a constant so can be used in Swagger REST API annotations
    public static final String CLASS_NAME = "io.brooklyn.camp.dto.ApplicationComponentTemplateDto";
    static { assert CLASS_NAME.equals(ApplicationComponentTemplateDto.class.getCanonicalName()); }

    protected ApplicationComponentTemplateDto() {}
    protected ApplicationComponentTemplateDto(DtoFactory dtoFactory, ApplicationComponentTemplate x) {
        super(dtoFactory, x);
        // TODO set addl ACT fields
    }
 
    // TODO add addl ACT fields
    
    // --- building ---

    public static ApplicationComponentTemplateDto newInstance(DtoFactory dtoFactory, ApplicationComponentTemplate x) {
        return new ApplicationComponentTemplateDto(dtoFactory, x);
    }
    
}
