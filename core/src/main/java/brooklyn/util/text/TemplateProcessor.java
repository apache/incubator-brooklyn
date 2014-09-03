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

import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.drivers.EntityDriver;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.ObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

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
        return processTemplateContents(templateContents, new EntityAndMapTemplateModel(driver, extraSubstitutions));
    }

    public static String processTemplateContents(String templateContents, ManagementContextInternal managementContext, Map<String,? extends Object> extraSubstitutions) {
        return processTemplateContents(templateContents, new EntityAndMapTemplateModel(managementContext, extraSubstitutions));
    }

    protected static final class EntityConfigTemplateModel implements TemplateHashModel {
        protected final EntityInternal entity;
        protected final ManagementContext mgmt;

        protected EntityConfigTemplateModel(EntityInternal entity) {
            this.entity = entity;
            this.mgmt = entity.getManagementContext();
        }

        protected EntityConfigTemplateModel(ManagementContext mgmt) {
            this.entity = null;
            this.mgmt = mgmt;
        }

        @Override
        public boolean isEmpty() { return false; }

        @Override
        public TemplateModel get(String key) throws TemplateModelException {
            try {
                Object result = null;
                
                if (entity!=null)
                    result = entity.getConfig(ConfigKeys.builder(Object.class).name(key).build());
                if (result==null && mgmt!=null)
                    result = mgmt.getConfig().getConfig(ConfigKeys.builder(Object.class).name(key).build());
                
                if (result!=null)
                    return ObjectWrapper.DEFAULT_WRAPPER.wrap( result );
                
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                throw new IllegalStateException("Error accessing config '"+key+"'"
                    + (entity!=null ? " on "+entity : "")+": "+e, e);
            }
            
            return null;
        }
        
        @Override
        public String toString() {
            return getClass().getName()+"["+entity+"]";
        }
    }

    protected static final class EntityAndMapTemplateModel implements TemplateHashModel {
        protected final EntityInternal entity;
        protected final EntityDriver driver;
        protected final ManagementContext mgmt;
        protected final Map<String,? extends Object> extraSubstitutions;

        protected EntityAndMapTemplateModel(ManagementContext mgmt, Map<String,? extends Object> extraSubstitutions) {
            this.entity = null;
            this.driver = null;
            this.mgmt = mgmt;
            this.extraSubstitutions = extraSubstitutions;
        }

        protected EntityAndMapTemplateModel(EntityDriver driver, Map<String,? extends Object> extraSubstitutions) {
            this.driver = driver;
            this.entity = (EntityInternal) driver.getEntity();
            this.mgmt = entity.getManagementContext();
            this.extraSubstitutions = extraSubstitutions;
        }

        protected EntityAndMapTemplateModel(EntityInternal entity, Map<String,? extends Object> extraSubstitutions) {
            this.entity = entity;
            this.driver = null;
            this.mgmt = entity.getManagementContext();
            this.extraSubstitutions = extraSubstitutions;
        }

        @Override
        public boolean isEmpty() { return false; }

        @Override
        public TemplateModel get(String key) throws TemplateModelException {
            if (extraSubstitutions!=null && extraSubstitutions.containsKey(key))
                return ObjectWrapper.DEFAULT_WRAPPER.wrap( extraSubstitutions.get(key) );

            if ("entity".equals(key) && entity!=null)
                return ObjectWrapper.DEFAULT_WRAPPER.wrap( entity );
            if ("config".equals(key)) {
                if (entity!=null)
                    return new EntityConfigTemplateModel(entity);
                else
                    return new EntityConfigTemplateModel(mgmt);
            }
            if ("mgmt".equals(key)) {
                return new EntityConfigTemplateModel(mgmt);
            }

            if ("driver".equals(key) && driver!=null)
                return ObjectWrapper.DEFAULT_WRAPPER.wrap( driver );
            if ("location".equals(key)) {
                if (driver!=null && driver.getLocation()!=null)
                    return ObjectWrapper.DEFAULT_WRAPPER.wrap( driver.getLocation() );
                if (entity!=null)
                    return ObjectWrapper.DEFAULT_WRAPPER.wrap( Iterables.getOnlyElement( entity.getLocations() ) );
            }
            
            if (mgmt!=null) {
                // TODO deprecated in 0.7.0, remove after next version
                // ie not supported to access global props without qualification
                Object result = mgmt.getConfig().getConfig(ConfigKeys.builder(Object.class).name(key).build());
                if (result!=null) { 
                    log.warn("Deprecated access of global brooklyn.properties value for "+key+"; should be qualified with 'mgmt.'");
                    return ObjectWrapper.DEFAULT_WRAPPER.wrap( result );
                }
            }
            
            if ("javaSysProps".equals(key))
                return ObjectWrapper.DEFAULT_WRAPPER.wrap( System.getProperties() );

            return null;
        }
        
        @Override
        public String toString() {
            return getClass().getName()+"["+(entity!=null ? entity : mgmt)+"]";
        }
    }

    public static String processTemplateContents(String templateContents, final EntityInternal entity, Map<String,? extends Object> extraSubstitutions) {
        return processTemplateContents(templateContents, new EntityAndMapTemplateModel(entity, extraSubstitutions));
    }
    
    public static String processTemplateContents(String templateContents, final Map<String, ? extends Object> substitutions) {
        TemplateHashModel root;
        try {
            root = substitutions != null
                ? (TemplateHashModel)ObjectWrapper.DEFAULT_WRAPPER.wrap(substitutions)
                : null;
        } catch (TemplateModelException e) {
            throw new IllegalStateException("Unable to set up TemplateHashModel to parse template, given "+substitutions+": "+e, e);
        }
        
        return processTemplateContents(templateContents, root);
    }
    
    public static String processTemplateContents(String templateContents, final TemplateHashModel substitutions) {
        try {
            Configuration cfg = new Configuration();
            StringTemplateLoader templateLoader = new StringTemplateLoader();
            templateLoader.putTemplate("config", templateContents);
            cfg.setTemplateLoader(templateLoader);
            Template template = cfg.getTemplate("config");

            // TODO could expose CAMP '$brooklyn:' style dsl, based on template.createProcessingEnvironment
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Writer out = new OutputStreamWriter(baos);
            template.process(substitutions, out);
            out.flush();

            return new String(baos.toByteArray());
        } catch (Exception e) {
            log.warn("Error processing template (propagating): "+e, e);
            log.debug("Template which could not be parsed (causing "+e+") is:"
                + (Strings.isMultiLine(templateContents) ? "\n"+templateContents : templateContents));
            throw Exceptions.propagate(e);
        }
    }
}
