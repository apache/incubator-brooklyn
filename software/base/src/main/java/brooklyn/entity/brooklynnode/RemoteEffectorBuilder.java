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
package brooklyn.entity.brooklynnode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import brooklyn.entity.Effector;
import brooklyn.entity.brooklynnode.BrooklynEntityMirrorImpl.RemoteEffector;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.effector.Effectors.EffectorBuilder;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.base.Function;

public class RemoteEffectorBuilder {
    private static class ResultParser implements Function<HttpToolResponse, String> {
        @Override
        public String apply(HttpToolResponse input) {
            return input.getContentAsString();
        }
    }

    public static Collection<Effector<String>> of(Collection<?> cfgEffectors) {
        Collection<Effector<String>> effectors = new ArrayList<Effector<String>>();
        for (Object objEff : cfgEffectors) {
            Map<?, ?> cfgEff = (Map<?, ?>)objEff;
//            String returnTypeName = (String)cfgEff.get("returnType");
            String effName = (String)cfgEff.get("name");
            String description = (String)cfgEff.get("description");

//            Class<?> returnType = getType(returnTypeName);
            EffectorBuilder<String> eff = Effectors.effector(String.class, effName);
            Collection<?> params = (Collection<?>)cfgEff.get("parameters");

            if (description != null) {
                eff.description(description);
            }

            for (Object objParam : params) {
                buildParam(eff, (Map<?, ?>)objParam);
            }

            eff.impl(new RemoteEffector<String>(effName, new ResultParser()));
            effectors.add(eff.build());
        }
        return effectors;
    }

    private static void buildParam(EffectorBuilder<String> eff, Map<?, ?> cfgParam) {
//        String type = (String)cfgParam.get("type");
        String name = (String)cfgParam.get("name");
        String description = (String)cfgParam.get("description");
        String defaultValue = (String)cfgParam.get("defaultValue");

//        Class<?> paramType = getType(type);
        eff.parameter(Object.class, name, description, defaultValue /*TypeCoercions.coerce(defaultValue, paramType)*/);
    }

//    private static Class<?> getType(String type) {
//        try {
//            return Class.forName(type);
//        } catch (ClassNotFoundException e) {
//            return Object.class;
//        }
//    }

}
