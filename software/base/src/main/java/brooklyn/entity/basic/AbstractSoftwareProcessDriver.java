/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package brooklyn.entity.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.InputStream;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.location.Location;
import brooklyn.util.ResourceUtils;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.TemplateProcessor;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;

/**
 * An abstract implementation of the {@link SoftwareProcessDriver}.
 */
public abstract class AbstractSoftwareProcessDriver implements SoftwareProcessDriver {

    private static final Logger log = LoggerFactory.getLogger(AbstractSoftwareProcessDriver.class);

    protected final EntityLocal entity;
    protected final ResourceUtils resource;
    protected final Location location;

    public AbstractSoftwareProcessDriver(EntityLocal entity, Location location) {
        this.entity = checkNotNull(entity, "entity");
        this.location = checkNotNull(location, "location");
        this.resource = ResourceUtils.create(entity);
    }

    /*
     * (non-Javadoc)
     * @see brooklyn.entity.basic.SoftwareProcessDriver#rebind()
     */
    @Override
    public void rebind() {
        // no-op
    }

    /**
     * Start the entity.
     *
     * this installs, configures and launches the application process. However,
     * users can also call the {@link #install()}, {@link #customize()} and
     * {@link #launch()} steps independently. The {@link #postLaunch()} will
     * be called after the {@link #launch()} metheod is executed, but the
     * process may not be completely initialised at this stage, so care is
     * required when implementing these stages.
     *
     * @see #stop()
     */
    @Override
    public void start() {
        DynamicTasks.queue("install", new Runnable() { public void run() {
            waitForConfigKey(BrooklynConfigKeys.INSTALL_LATCH);
            install();
        }});

        DynamicTasks.queue("customize", new Runnable() { public void run() {
            waitForConfigKey(BrooklynConfigKeys.CUSTOMIZE_LATCH);
            customize();
        }});

        DynamicTasks.queue("launch", new Runnable() { public void run() {
            waitForConfigKey(BrooklynConfigKeys.LAUNCH_LATCH);
            launch();
        }});

        DynamicTasks.queue("post-launch", new Runnable() { public void run() {
            postLaunch();
        }});
    }

    @Override
    public abstract void stop();

    public abstract void install();
    public abstract void customize();
    public abstract void launch();

    @Override
    public void kill() {
        stop();
    }

    /**
     * Implement this method in child classes to add some post-launch behavior
     */
    public void postLaunch() {}

    @Override
    public void restart() {
        DynamicTasks.queue("stop (best effort)", new Runnable() { public void run() {
            DynamicTasks.markInessential();
            boolean previouslyRunning = isRunning();
            try {
                getEntity().setAttribute(Attributes.SERVICE_STATE, Lifecycle.STOPPING);
                stop();
            } catch (Exception e) {
                // queue a failed task so that there is visual indication that this task had a failure,
                // without interrupting the parent
                if (previouslyRunning) {
                    log.warn(getEntity() + " restart: stop failed, when was previously running (ignoring)", e);
                    DynamicTasks.queue(Tasks.fail("Primary job failure (when previously running)", e));
                } else {
                    log.debug(getEntity() + " restart: stop failed (but was not previously running, so not a surprise)", e);
                    DynamicTasks.queue(Tasks.fail("Primary job failure (when not previously running)", e));
                }
                // the above queued tasks will cause this task to be indicated as failed, with an indication of severity
            }
        }});

        if (doFullStartOnRestart()) {
            DynamicTasks.waitForLast();
            getEntity().setAttribute(Attributes.SERVICE_STATE, Lifecycle.STARTING);
            start();
        } else {
            DynamicTasks.queue("launch", new Runnable() { public void run() {
                getEntity().setAttribute(Attributes.SERVICE_STATE, Lifecycle.STARTING);
                launch();
            }});
            DynamicTasks.queue("post-launch", new Runnable() { public void run() {
                postLaunch();
            }});
        }
    }

    @Beta
    /** ideally restart() would take options, e.g. whether to do full start, skip installs, etc;
     * however in the absence here is a toggle - not sure how well it works;
     * default is false which is similar to previous behaviour (with some seemingly-obvious tidies),
     * meaning install and configure will NOT be done on restart. */
    protected boolean doFullStartOnRestart() {
        return false;
    }

    @Override
    public EntityLocal getEntity() { return entity; }

    @Override
    public Location getLocation() { return location; }

    public InputStream getResource(String url) {
        return resource.getResourceFromUrl(url);
    }

    public String getResourceAsString(String url) {
        return resource.getResourceAsString(url);
    }

    public String processTemplate(File templateConfigFile, Map<String,Object> extraSubstitutions) {
        return processTemplate(templateConfigFile.toURI().toASCIIString(), extraSubstitutions);
    }

    public String processTemplate(File templateConfigFile) {
        return processTemplate(templateConfigFile.toURI().toASCIIString());
    }

    /** Takes the contents of a template file from the given URL (often a classpath://com/myco/myprod/myfile.conf or .sh)
     * and replaces "${entity.xxx}" with the result of entity.getXxx() and similar for other driver, location;
     * as well as replacing config keys on the management context
     * <p>
     * uses Freemarker templates under the covers
     **/
    public String processTemplate(String templateConfigUrl) {
        return processTemplate(templateConfigUrl, ImmutableMap.<String,String>of());
    }

    public String processTemplate(String templateConfigUrl, Map<String,? extends Object> extraSubstitutions) {
        return processTemplateContents(getResourceAsString(templateConfigUrl), extraSubstitutions);
    }

    public String processTemplateContents(String templateContents) {
        return processTemplateContents(templateContents, ImmutableMap.<String,String>of());
    }

    public String processTemplateContents(String templateContents, Map<String,? extends Object> extraSubstitutions) {
        return TemplateProcessor.processTemplateContents(templateContents, this, extraSubstitutions);
    }

    protected void waitForConfigKey(ConfigKey<?> configKey) {
        Object val = entity.getConfig(configKey);
        if (val != null) log.debug("{} finished waiting for {} (value {}); continuing...", new Object[] {this, configKey, val});
    }
}
