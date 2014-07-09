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
import java.util.List;

import javax.management.MBeanOperationInfo;

/**
 * An operation of some kind, carried out by an {@link Entity}.
 *
 * Similar to the concepts in the JMX {@link MBeanOperationInfo} class.
 */
public interface Effector<T> extends Serializable {
    /**
     * human-friendly name of the effector (although frequently this uses java method naming convention)
     */
    String getName();

    Class<T> getReturnType();

    /**
     * canonical name of return type (in case return type does not resolve after serialization)
     */
    String getReturnTypeName();

    /**
     * parameters expected by method, including name and type, optional description and default value
     */
    List<ParameterType<?>> getParameters();

    /**
     * optional description for the effector
     */
    String getDescription();

}
