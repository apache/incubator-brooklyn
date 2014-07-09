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
package brooklyn.entity;

import java.io.Serializable;

import javax.management.MBeanParameterInfo;

/**
 * Similar to the concepts in the JMX {@link MBeanParameterInfo} class.
 *
 * @see Effector
 */
public interface ParameterType<T> extends Serializable {
    
    public String getName();

    public Class<T> getParameterClass();

    /**
     * The canonical name of the parameter class; especially useful if the class 
     * cannot be resolved after deserialization. 
     */
    public String getParameterClassName();

    public String getDescription();

    /**
     * @return The default value for this parameter, if not supplied during an effector call.
     */
    public T getDefaultValue();
}
