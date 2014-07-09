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
package brooklyn.mementos;

import java.io.Serializable;
import java.util.Map;

import brooklyn.entity.rebind.RebindSupport;

/**
 * Represents the internal state of something in brooklyn, so that it can be reconstructed (e.g. after restarting brooklyn).
 * 
 * @see RebindSupport
 * 
 * @author aled
 */
public interface Memento extends Serializable {

    /**
     * The version of brooklyn used when this memento was generated.
     */
    String getBrooklynVersion();
    
    String getId();
    
    public String getType();
    
    public String getDisplayName();
    
    /**
     * A (weakly-typed) property set for this memento.
     * These can be used to avoid sub-classing the entity memento, but developers can sub-class to get strong typing if desired.
     * 
     * @deprecated since 0.7.0; use config/attributes so generic persistence will work, rather than requiring "custom fields"
     */
    @Deprecated
    public Object getCustomField(String name);

    /**
     * @deprecated since 0.7.0; use config/attributes so generic persistence will work, rather than requiring "custom fields"
     */
    @Deprecated
    public Map<String, ? extends Object> getCustomFields();
    
    public String toVerboseString();
    
    public void injectTypeClass(Class<?> clazz);
    
    /**
     * Returns the injected type class, or null if not injected.
     * <p>
     * This is useful for ensuring the correct classloader is used (e.g. for {@link EntityMemento} 
     * previously calling {@code EntityTypes.getDefinedSensors(getType())}. 
     */
    public Class<?> getTypeClass();
}
