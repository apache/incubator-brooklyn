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

import org.apache.brooklyn.management.ManagementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.drivers.EntityDriver;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.collections.MutableMap;
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

/** A variety of methods to assist in Freemarker template processing,
 * including passing in maps with keys flattened (dot-separated namespace),
 * and accessing {@link ManagementContext} brooklyn.properties 
 * and {@link Entity}, {@link EntityDriver}, and {@link Location} methods and config.
 * <p>
 * See {@link #processTemplateContents(String, ManagementContextInternal, Map)} for
 * a description of how management access is done.
 */
public class TemplateProcessor {

    private static final Logger log = LoggerFactory.getLogger(TemplateProcessor.class);

    protected static TemplateModel wrapAsTemplateModel(Object o) throws TemplateModelException {
        if (o instanceof Map) return new DotSplittingTemplateModel((Map<?,?>)o);
        return ObjectWrapper.DEFAULT_WRAPPER.wrap(o);
    }
    
    /** @deprecated since 0.7.0 use {@link #processTemplateFile(String, Map)} */ @Deprecated
    public static String processTemplate(String templateFileName, Map<String, ? extends Object> substitutions) {
        return processTemplateFile(templateFileName, substitutions);
    }
    
    /** As per {@link #processTemplateContents(String, Map)}, but taking a file. */
    public static String processTemplateFile(String templateFileName, Map<String, ? extends Object> substitutions) {
        String templateContents;
        try {
            templateContents = Files.toString(new File(templateFileName), Charsets.UTF_8);
        } catch (IOException e) {
            log.warn("Error loading file " + templateFileName, e);
            throw Exceptions.propagate(e);
        }
        return processTemplateContents(templateContents, substitutions);
    }

    /** @deprecated since 0.7.0 use {@link #processTemplateFile(String, EntityDriver, Map)} */ @Deprecated
    public static String processTemplate(String templateFileName, EntityDriver driver, Map<String, ? extends Object> extraSubstitutions) {
        return processTemplateFile(templateFileName, driver, extraSubstitutions);
    }
    
    /** Processes template contents according to {@link EntityAndMapTemplateModel}. */
    public static String processTemplateFile(String templateFileName, EntityDriver driver, Map<String, ? extends Object> extraSubstitutions) {
        String templateContents;
        try {
            templateContents = Files.toString(new File(templateFileName), Charsets.UTF_8);
        } catch (IOException e) {
            log.warn("Error loading file " + templateFileName, e);
            throw Exceptions.propagate(e);
        }
        return processTemplateContents(templateContents, driver, extraSubstitutions);
    }

    /** Processes template contents according to {@link EntityAndMapTemplateModel}. */
    public static String processTemplateContents(String templateContents, EntityDriver driver, Map<String,? extends Object> extraSubstitutions) {
        return processTemplateContents(templateContents, new EntityAndMapTemplateModel(driver, extraSubstitutions));
    }

    /** Processes template contents according to {@link EntityAndMapTemplateModel}. */
    public static String processTemplateContents(String templateContents, ManagementContext managementContext, Map<String,? extends Object> extraSubstitutions) {
        return processTemplateContents(templateContents, new EntityAndMapTemplateModel(managementContext, extraSubstitutions));
    }

    /**
     * A Freemarker {@link TemplateHashModel} which will correctly handle entries of the form "a.b" in this map,
     * matching against template requests for "${a.b}".
     * <p>
     * Freemarker requests "a" in a map when given such a request, and expects that to point to a map
     * with a key "b". This model provides such maps even for "a.b" in a map.
     * <p>
     * However if "a" <b>and</b> "a.b" are in the map, this will <b>not</b> currently do the deep mapping.
     * (It does not have enough contextual information from Freemarker to handle this case.) */
    public static final class DotSplittingTemplateModel implements TemplateHashModel {
        protected final Map<?,?> map;

        protected DotSplittingTemplateModel(Map<?,?> map) {
            this.map = map;
        }

        @Override
        public boolean isEmpty() { return map!=null && map.isEmpty(); }

        public boolean contains(String key) {
            if (map==null) return false;
            if (map.containsKey(key)) return true;
            for (Map.Entry<?,?> entry: map.entrySet()) {
                String k = Strings.toString(entry.getKey());
                if (k.startsWith(key+".")) {
                    // contains this prefix
                    return true;
                }
            }
            return false;
        }
        
        @Override
        public TemplateModel get(String key) throws TemplateModelException {
            if (map==null) return null;
            try {
                if (map.containsKey(key)) 
                    return wrapAsTemplateModel( map.get(key) );
                
                Map<String,Object> result = MutableMap.of();
                for (Map.Entry<?,?> entry: map.entrySet()) {
                    String k = Strings.toString(entry.getKey());
                    if (k.startsWith(key+".")) {
                        String k2 = Strings.removeFromStart(k, key+".");
                        result.put(k2, entry.getValue());
                    }
                }
                if (!result.isEmpty()) 
                        return wrapAsTemplateModel( result );
                
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                throw new IllegalStateException("Error accessing config '"+key+"'"+": "+e, e);
            }
            
            return null;
        }
        
        @Override
        public String toString() {
            return getClass().getName()+"["+map+"]";
        }
    }
    
    /** FreeMarker {@link TemplateHashModel} which resolves keys inside the given entity or management context.
     * Callers are required to include dots for dot-separated keys.
     * Freemarker will only due this when in inside bracket notation in an outer map, as in <code>${outer['a.b.']}</code>; 
     * as a result this is intended only for use by {@link EntityAndMapTemplateModel} where 
     * a caller has used bracked notation, as in <code>${mgmt['key.subkey']}</code>. */
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
                    return wrapAsTemplateModel( result );
                
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

    protected final static class EntityAttributeTemplateModel implements TemplateHashModel {
        protected final EntityInternal entity;

        protected EntityAttributeTemplateModel(EntityInternal entity) {
            this.entity = entity;
        }

        @Override
        public boolean isEmpty() throws TemplateModelException {
            return false;
        }

        @Override
        public TemplateModel get(String key) throws TemplateModelException {
            Object result;
            try {
                result = Entities.submit(entity, DependentConfiguration.attributeWhenReady(entity,
                        Sensors.builder(Object.class, key).persistence(AttributeSensor.SensorPersistenceMode.NONE).build())).get();
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
            if (result == null) {
                return null;
            } else {
                return wrapAsTemplateModel(result);
            }
        }

        @Override
        public String toString() {
            return getClass().getName()+"["+entity+"]";
        }
    }

    /**
     * Provides access to config on an entity or management context, using
     * <code>${config['entity.config.key']}</code> or <code>${mgmt['brooklyn.properties.key']}</code> notation,
     * and also allowing access to <code>getX()</code> methods on entity (interface) or driver
     * using <code>${entity.x}</code> or <code><${driver.x}</code>.
     * Optional extra properties can be supplied, treated as per {@link DotSplittingTemplateModel}.
     */
    protected static final class EntityAndMapTemplateModel implements TemplateHashModel {
        protected final EntityInternal entity;
        protected final EntityDriver driver;
        protected final ManagementContext mgmt;
        protected final DotSplittingTemplateModel extraSubstitutionsModel;

        protected EntityAndMapTemplateModel(ManagementContext mgmt, Map<String,? extends Object> extraSubstitutions) {
            this.entity = null;
            this.driver = null;
            this.mgmt = mgmt;
            this.extraSubstitutionsModel = new DotSplittingTemplateModel(extraSubstitutions);
        }

        protected EntityAndMapTemplateModel(EntityDriver driver, Map<String,? extends Object> extraSubstitutions) {
            this.driver = driver;
            this.entity = (EntityInternal) driver.getEntity();
            this.mgmt = entity.getManagementContext();
            this.extraSubstitutionsModel = new DotSplittingTemplateModel(extraSubstitutions);
        }

        protected EntityAndMapTemplateModel(EntityInternal entity, Map<String,? extends Object> extraSubstitutions) {
            this.entity = entity;
            this.driver = null;
            this.mgmt = entity.getManagementContext();
            this.extraSubstitutionsModel = new DotSplittingTemplateModel(extraSubstitutions);
        }

        @Override
        public boolean isEmpty() { return false; }

        @Override
        public TemplateModel get(String key) throws TemplateModelException {
            if (extraSubstitutionsModel.contains(key))
                return wrapAsTemplateModel( extraSubstitutionsModel.get(key) );

            if ("entity".equals(key) && entity!=null)
                return wrapAsTemplateModel( entity );
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
                return wrapAsTemplateModel( driver );
            if ("location".equals(key)) {
                if (driver!=null && driver.getLocation()!=null)
                    return wrapAsTemplateModel( driver.getLocation() );
                if (entity!=null)
                    return wrapAsTemplateModel( Iterables.getOnlyElement( entity.getLocations() ) );
            }
            if ("attribute".equals(key)) {
                return new EntityAttributeTemplateModel(entity);
            }
            
            if (mgmt!=null) {
                // TODO deprecated in 0.7.0, remove after next version
                // ie not supported to access global props without qualification
                Object result = mgmt.getConfig().getConfig(ConfigKeys.builder(Object.class).name(key).build());
                if (result!=null) { 
                    log.warn("Deprecated access of global brooklyn.properties value for "+key+"; should be qualified with 'mgmt.'");
                    return wrapAsTemplateModel( result );
                }
            }
            
            if ("javaSysProps".equals(key))
                return wrapAsTemplateModel( System.getProperties() );

            return null;
        }
        
        @Override
        public String toString() {
            return getClass().getName()+"["+(entity!=null ? entity : mgmt)+"]";
        }
    }

    /** Processes template contents with the given items in scope as per {@link EntityAndMapTemplateModel}. */
    public static String processTemplateContents(String templateContents, final EntityInternal entity, Map<String,? extends Object> extraSubstitutions) {
        return processTemplateContents(templateContents, new EntityAndMapTemplateModel(entity, extraSubstitutions));
    }
    
    /** Processes template contents using the given map, passed to freemarker,
     * with dot handling as per {@link DotSplittingTemplateModel}. */
    public static String processTemplateContents(String templateContents, final Map<String, ? extends Object> substitutions) {
        TemplateHashModel root;
        try {
            root = substitutions != null
                ? (TemplateHashModel)wrapAsTemplateModel(substitutions)
                : null;
        } catch (TemplateModelException e) {
            throw new IllegalStateException("Unable to set up TemplateHashModel to parse template, given "+substitutions+": "+e, e);
        }
        
        return processTemplateContents(templateContents, root);
    }
    
    /** Processes template contents against the given {@link TemplateHashModel}. */
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
