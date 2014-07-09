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
package brooklyn.entity.chef;

import brooklyn.entity.basic.EffectorStartableImpl;
import brooklyn.util.text.Strings;

public class ChefEntityImpl extends EffectorStartableImpl implements ChefEntity {

    public void init() {
        String primaryName = getConfig(CHEF_COOKBOOK_PRIMARY_NAME);
        if (!Strings.isBlank(primaryName)) setDefaultDisplayName(primaryName+" (chef)");
        
        super.init();
        new ChefLifecycleEffectorTasks().attachLifecycleEffectors(this);
    }
    
    
    
}
