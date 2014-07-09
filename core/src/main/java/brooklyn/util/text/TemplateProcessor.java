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
package brooklyn.util.text;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.drivers.EntityDriver;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;

public class TemplateProcessor {

    private static final Logger log = LoggerFactory.getLogger(TemplateProcessor.class);

    public static String processTemplate(String templateFileName, Map<String, ? extends Object> substitutions) {
        String templateContents;
        try {
            templateContents = Files.toString(new File(templateFileName), Charsets.UTF_8);
        } catch (IOException e) {
            log.warn("Error loading file " + templateFileName, e);
            throw Exceptions.propagate(e);
        }
        return processTemplateContents(templateContents, substitutions);
    }

    public static String processTemplate(String templateFileName, EntityDriver driver, Map<String, ? extends Object> extraSubstitutions) {
        String templateContents;
        try {
            templateContents = Files.toString(new File(templateFileName), Charsets.UTF_8);
        } catch (IOException e) {
            log.warn("Error loading file " + templateFileName, e);
            throw Exceptions.propagate(e);
        }
        return processTemplateContents(templateContents, driver, extraSubstitutions);
    }

    public static String processTemplateContents(String templateContents, EntityDriver driver, Map<String,? extends Object> extraSubstitutions) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
                .put("javaSysProps", System.getProperties())
                .putAll(driver.getEntity().getApplication().getManagementContext().getConfig().asMapWithStringKeys())
                .put("driver", driver)
                .put("entity", driver.getEntity())
                .put("config", ((EntityInternal) driver.getEntity()).getConfigMap().asMapWithStringKeys());
        if (driver.getLocation() != null) builder.put("location", driver.getLocation());
        builder.putAll(extraSubstitutions);

        return processTemplateContents(templateContents, builder.build());
    }

    public static String processTemplateContents(String templateContents, ManagementContextInternal managementContext, Map<String,? extends Object> extraSubstitutions) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
                .put("javaSysProps", System.getProperties())
                .putAll(managementContext.getConfig().asMapWithStringKeys())
                .put("config", managementContext.getConfig().asMapWithStringKeys());
        builder.putAll(extraSubstitutions);

        return processTemplateContents(templateContents, builder.build());
    }

    public static String processTemplateContents(String templateContents, EntityInternal entity, Map<String,? extends Object> extraSubstitutions) {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.<String, Object>builder()
                .put("javaSysProps", System.getProperties())
                .putAll(entity.getApplication().getManagementContext().getConfig().asMapWithStringKeys())
                .put("entity", entity)
                .put("config", entity.getConfigMap().asMapWithStringKeys());
        // TODO might want to look up locations, driver, if available
        builder.putAll(extraSubstitutions);

        return processTemplateContents(templateContents, builder.build());
    }

    public static String processTemplateContents(String templateContents, Map<String, ? extends Object> substitutions) {
        try {
            Configuration cfg = new Configuration();
            StringTemplateLoader templateLoader = new StringTemplateLoader();
            templateLoader.putTemplate("config", templateContents);
            cfg.setTemplateLoader(templateLoader);
            Template template = cfg.getTemplate("config");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Writer out = new OutputStreamWriter(baos);
            template.process(substitutions, out);
            out.flush();

            return new String(baos.toByteArray());
        } catch (Exception e) {
            log.warn("Error processing template " + templateContents + " with vars " + substitutions, e);
            throw Exceptions.propagate(e);
        }
    }
}
