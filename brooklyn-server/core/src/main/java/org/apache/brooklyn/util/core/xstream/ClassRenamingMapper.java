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
package org.apache.brooklyn.util.core.xstream;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.apache.brooklyn.util.javalang.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.thoughtworks.xstream.mapper.Mapper;
import com.thoughtworks.xstream.mapper.MapperWrapper;

public class ClassRenamingMapper extends MapperWrapper {
    public static final Logger LOG = LoggerFactory.getLogger(ClassRenamingMapper.class);
    
    private final Map<String, String> nameToType;

    public ClassRenamingMapper(Mapper wrapped, Map<String, String> nameToType) {
        super(wrapped);
        this.nameToType = checkNotNull(nameToType, "nameToType");
    }
    
    @Override
    public Class<?> realClass(String elementName) {
        Optional<String> elementNameOpt = Reflections.tryFindMappedName(nameToType, elementName);
        if (elementNameOpt.isPresent()) {
            LOG.debug("Mapping class '"+elementName+"' to '"+elementNameOpt.get()+"'");
            elementName = elementNameOpt.get();
        }
        return super.realClass(elementName);
    }

}
