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

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.MapConfigKey.MapModifications;
import brooklyn.event.basic.SetConfigKey.SetModifications;
import brooklyn.util.git.GithubUrls;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/** Conveniences for configuring brooklyn Chef entities 
 * @since 0.6.0 */
@Beta
public class ChefConfigs {

    public static void addToLaunchRunList(EntitySpec<?> entity, String ...recipes) {
        for (String recipe: recipes)
            entity.configure(ChefConfig.CHEF_LAUNCH_RUN_LIST, SetModifications.addItem(recipe));
    }

    public static void addToLaunchRunList(EntityInternal entity, String ...recipes) {
        for (String recipe: recipes)
            entity.setConfig(ChefConfig.CHEF_LAUNCH_RUN_LIST, SetModifications.addItem(recipe));
    }

    public static void addToCookbooksFromGithub(EntitySpec<?> entity, String ...cookbookNames) {
        for (String cookbookName: cookbookNames)
            addToCookbooksFromGithub(entity, cookbookName, getGithubOpscodeRepo(cookbookName)); 
    }
    
    public static void addToCookbooksFromGithub(EntityInternal entity, String ...cookbookNames) {
        for (String cookbookName: cookbookNames)
            addToCookbooksFromGithub(entity, cookbookName, getGithubOpscodeRepo(cookbookName)); 
    }

    public static String getGithubOpscodeRepo(String cookbookName) {
        return getGithubOpscodeRepo(cookbookName, "master");
    }
    public static String getGithubOpscodeRepo(String cookbookName, String tag) {
        return GithubUrls.tgz("opscode-cookbooks", cookbookName, tag);
    }
    
    public static void addToCookbooksFromGithub(EntitySpec<?> entity, String cookbookName, String cookbookUrl) {
        entity.configure(ChefConfig.CHEF_COOKBOOK_URLS.subKey(cookbookName), cookbookUrl);
    }

    public static void addToCookbooksFromGithub(EntityInternal entity, String cookbookName, String cookbookUrl) {
        entity.setConfig(ChefConfig.CHEF_COOKBOOK_URLS.subKey(cookbookName), cookbookUrl);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void addLaunchAttributes(EntitySpec<?> entity, Map<? extends Object,? extends Object> attributesMap) {
        entity.configure(ChefConfig.CHEF_LAUNCH_ATTRIBUTES, MapModifications.add((Map)attributesMap));
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void addLaunchAttributes(EntityInternal entity, Map<? extends Object,? extends Object> attributesMap) {
        entity.setConfig(ChefConfig.CHEF_LAUNCH_ATTRIBUTES, MapModifications.add((Map)attributesMap));
    }
    
    /** replaces the attributes underneath the rootAttribute parameter with the given value;
     * see {@link #addLaunchAttributesMap(EntitySpec, Map)} for richer functionality */
    public static void setLaunchAttribute(EntitySpec<?> entity, String rootAttribute, Object value) {
        entity.configure(ChefConfig.CHEF_LAUNCH_ATTRIBUTES.subKey(rootAttribute), value);
    }
    
    /** replaces the attributes underneath the rootAttribute parameter with the given value;
     * see {@link #addLaunchAttributesMap(EntitySpec, Map)} for richer functionality */
    public static void setLaunchAttribute(EntityInternal entity, String rootAttribute, Object value) {
        entity.setConfig(ChefConfig.CHEF_LAUNCH_ATTRIBUTES.subKey(rootAttribute), value);
    }

    public static <T> T getRequiredConfig(Entity entity, ConfigKey<T> key) {
        return Preconditions.checkNotNull(
                Preconditions.checkNotNull(entity, "Entity must be supplied").getConfig(key), 
                "Key "+key+" is required on "+entity);
    }

}
