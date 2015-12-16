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
package org.apache.brooklyn.entity.chef;

import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.MapConfigKey.MapModifications;
import org.apache.brooklyn.core.config.SetConfigKey.SetModifications;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.util.git.GithubUrls;

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
            entity.config().set(ChefConfig.CHEF_LAUNCH_RUN_LIST, SetModifications.addItem(recipe));
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
        entity.config().set(ChefConfig.CHEF_COOKBOOK_URLS.subKey(cookbookName), cookbookUrl);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void addLaunchAttributes(EntitySpec<?> entity, Map<? extends Object,? extends Object> attributesMap) {
        entity.configure(ChefConfig.CHEF_LAUNCH_ATTRIBUTES, MapModifications.add((Map)attributesMap));
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void addLaunchAttributes(EntityInternal entity, Map<? extends Object,? extends Object> attributesMap) {
        entity.config().set(ChefConfig.CHEF_LAUNCH_ATTRIBUTES, MapModifications.add((Map)attributesMap));
    }
    
    /** replaces the attributes underneath the rootAttribute parameter with the given value;
     * see {@link #addLaunchAttributesMap(EntitySpec, Map)} for richer functionality */
    public static void setLaunchAttribute(EntitySpec<?> entity, String rootAttribute, Object value) {
        entity.configure(ChefConfig.CHEF_LAUNCH_ATTRIBUTES.subKey(rootAttribute), value);
    }
    
    /** replaces the attributes underneath the rootAttribute parameter with the given value;
     * see {@link #addLaunchAttributesMap(EntitySpec, Map)} for richer functionality */
    public static void setLaunchAttribute(EntityInternal entity, String rootAttribute, Object value) {
        entity.config().set(ChefConfig.CHEF_LAUNCH_ATTRIBUTES.subKey(rootAttribute), value);
    }

    public static <T> T getRequiredConfig(Entity entity, ConfigKey<T> key) {
        return Preconditions.checkNotNull(
                Preconditions.checkNotNull(entity, "Entity must be supplied").getConfig(key), 
                "Key "+key+" is required on "+entity);
    }

}
