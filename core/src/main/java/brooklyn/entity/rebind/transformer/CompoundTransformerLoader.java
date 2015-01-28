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
package brooklyn.entity.rebind.transformer;

import java.util.Map;

import brooklyn.entity.rebind.transformer.CompoundTransformer.Builder;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.TemplateProcessor;
import brooklyn.util.yaml.Yamls;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

@Beta
public class CompoundTransformerLoader {

    // TODO Improve error handing so get nicer errors.
    // TODO Improve names (e.g. always camel case?)
    // TODO Pass in classloader for reflectively loading rawDataTransformer?
    
    public static CompoundTransformer load(String contents) {
        CompoundTransformer.Builder builder = CompoundTransformer.builder();
        Iterable<Object> toplevel = Yamls.parseAll(contents);
        @SuppressWarnings("unchecked")
        Map<String, Map<?,?>> rules = (Map<String, Map<?,?>>) ((Map<?,?>)Iterables.getOnlyElement(toplevel));
        for (Map.Entry<String, Map<?,?>> entry : rules.entrySet()) {
            addRule(builder, entry.getKey(), entry.getValue());
        }
        return builder.build();
    }

    private static void addRule(Builder builder, String name, Map<?,?> args) {
        if (name.equals("renameClass")) {
            String oldVal = (String) args.get("old_val");
            String newVal = (String) args.get("new_val");
            builder.renameClass(oldVal, newVal);
        } else if (name.equals("renameType")) {
            String oldVal = (String) args.get("old_val");
            String newVal = (String) args.get("new_val");
            builder.renameType(oldVal, newVal);
        } else if (name.equals("renameField")) {
            String clazz = (String) args.get("class_name");
            String oldVal = (String) args.get("old_val");
            String newVal = (String) args.get("new_val");
            builder.renameField(clazz, oldVal, newVal);
        } else if (name.equals("catalogItemId")) {
            builder.changeCatalogItemId(
                (String) args.get("old_symbolic_name"), checkString(args.get("old_version"), "old_version"),
                (String) args.get("new_symbolic_name"), checkString(args.get("new_version"), "new_version"));
        } else if (name.equals("xslt")) {
            String url = (String) args.get("url");
            @SuppressWarnings("unchecked")
            Map<String,?> substitutions = (Map<String, ?>) args.get("substitutions");
            String xsltTemplate = ResourceUtils.create(CompoundTransformer.class).getResourceAsString(url);
            String xslt = TemplateProcessor.processTemplateContents(xsltTemplate, substitutions == null ? ImmutableMap.<String, String>of() : substitutions);
            // we could pass XSLT-style parameters instead, maybe?  that's more normal, 
            // but OTOH freemarker is maybe more powerful, given our other support there
            builder.xsltTransformer(xslt);
        } else if (name.equals("rawDataTransformer")) {
            String type = (String) args.get("type");
            try {
                Class<?> clazz = CompoundTransformerLoader.class.getClassLoader().loadClass(type);
                builder.rawDataTransformer((RawDataTransformer) clazz.newInstance());
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
        } else {
            throw new IllegalStateException("Unsupported transform '"+name+"' ("+args+")");
        }
    }

    private static String checkString(Object object, String name) {
        if (object!=null && !(object instanceof String)) {
            throw new IllegalArgumentException("Argument '"+name+"' must be a string; numbers may need explicit quoting in YAML.");
        }
        return (String) object;
    }
}
