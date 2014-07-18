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
package brooklyn.entity.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.EntityManager;
import brooklyn.management.ManagementContext;

import com.google.common.annotations.Beta;

/**
 * Experimental mechanism for defining/building applications. In future releases, this
 * API will change. Its concepts will most likely be merged with a TOSCA implementation
 * and with {@link EntitySpec}.
 *
 * For building an application. Users can sub-class and override doBuild(), putting the logic for  
 * creating and wiring together entities in there.
 * 
 * The builder is mutable; a given instance should be used to build only a single application.
 * Once {@link #manage()} has been called, the application will be built and no additional configuration
 * should be performed through this builder.  
 * 
 * Example (simplified) code for sub-classing is:
 * <pre>
 * {@code
 *   app = new ApplicationBuilder() {
 *       //@Override
 *       public void doBuild() {
 *           MySqlNode db = addChild(EntitySpec.create(MySqlNode.class)));
 *           JBoss7Server as = addChild(EntitySpec.create(JBoss7Server.class)
 *                   .configure(HTTP_PORT, "8080+")
 *                   .configure(javaSysProp("brooklyn.example.db.url"), attributeWhenReady(db, MySqlNode.MYSQL_URL));
 *       }
 *   }.manage();
 * }
 * </pre>
 * 
 * @author aled
 */
@Beta
public abstract class ApplicationBuilder {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(ApplicationBuilder.class);

    @SuppressWarnings("unchecked")
    @Beta
    /** @deprecated since 0.7.0 the management context should normally be passed in;
     * for TestApplication also see TestApplication.Factory.newManagedInstanceForTests() */ 
    @Deprecated
    public static <T extends StartableApplication> T newManagedApp(Class<T> type) {
        if (type.isInterface()) {
            return (T) newManagedApp(EntitySpec.create(type));
        } else {
            return (T) newManagedApp(EntitySpec.create(StartableApplication.class, type));
        }
    }

    @SuppressWarnings("unchecked")
    @Beta
    /** @deprecated since 0.7.0 the management context should normally be passed in;
     * for TestApplication also see TestApplication.Factory.newManagedInstanceForTests() */ 
    @Deprecated
    public static <T extends StartableApplication> T newManagedApp(EntitySpec<T> spec) {
        return (T) new ApplicationBuilder(spec) {
            @Override protected void doBuild() {
            }
        }.manage();
    }

    @SuppressWarnings("unchecked")
    @Beta
    public static <T extends StartableApplication> T newManagedApp(Class<T> type, ManagementContext managementContext) {
        if (type.isInterface()) {
            return (T) newManagedApp(EntitySpec.create(type), managementContext);
        } else {
            return (T) newManagedApp(EntitySpec.create(StartableApplication.class, type), managementContext);
        }
    }

    @SuppressWarnings("unchecked")
    @Beta
    public static <T extends StartableApplication> T newManagedApp(EntitySpec<T> spec, ManagementContext managementContext) {
        return (T) new ApplicationBuilder(spec) {
            @Override protected void doBuild() {
            }
        }.manage(managementContext);
    }

    protected volatile boolean managed = false;
    protected final AtomicBoolean inManage = new AtomicBoolean(false);
    private EntitySpec<? extends StartableApplication> appSpec;
    private ManagementContext managementContext;
    private StartableApplication app;
    
    public ApplicationBuilder() {
        this.appSpec = EntitySpec.create(BasicApplication.class);
    }

    public ApplicationBuilder(EntitySpec<? extends StartableApplication> appSpec) {
        this.appSpec = EntitySpec.create(appSpec);
    }

    public final ApplicationBuilder appDisplayName(String val) {
        checkPreManage();
        appSpec.displayName(val);
        return this;
    }
    
    protected final <T extends Entity> T createEntity(EntitySpec<T> spec) {
        checkDuringManage();
        EntityManager entityManager = managementContext.getEntityManager();
        return entityManager.createEntity(spec);
    }

    /**
     * Adds the given entity as a child of the application being built.
     * To be called during {@link #doBuild()}.
     */
    protected final <T extends Entity> T addChild(T entity) {
        checkDuringManage();
        return app.addChild(entity);
    }

    /**
     * Returns the type of the application being built.
     */
    public final Class<? extends StartableApplication> getType() {
        return appSpec.getType();
    }
    
    /**
     * Configures the application instance.
     */
    public final ApplicationBuilder configure(Map<?,?> config) {
        checkPreManage();
        appSpec.configure(config);
        return this;
    }
    
    /**
     * Adds the given entity as a child of the application being built.
     */
    protected final <T extends Entity> T addChild(EntitySpec<T> spec) {
        checkDuringManage();
        return addChild(createEntity(spec));
    }
    
    protected final <T extends Entity> T addChild(Map<?,?> config, Class<T> type) {
        checkDuringManage();
        EntitySpec<T> spec = EntitySpec.create(type).configure(config);
        return addChild(createEntity(spec));
    }
    
    protected final ManagementContext getManagementContext() {
        return checkNotNull(managementContext, "must only be called after manage()");
    }

    protected final StartableApplication getApp() {
        return checkNotNull(app, "must only be called after manage()");
    }

    /**
     * For overriding, to create and wire together entities.
     */
    protected abstract void doBuild();

    /**
     * Creates a new {@link ManagementContext}, and then builds and manages the application.
     * 
     * @see #manage(ManagementContext)
     */
    public final StartableApplication manage() {
        return manage(Entities.newManagementContext());
    }
    
    /**
     * Builds and manages the application, calling the user's {@link #doBuild()} method.
     * 
     * @throws IllegalStateException If already managed, or if called during {@link #doBuild()}, or if 
     *                               multiple concurrent calls
     */
    public final StartableApplication manage(ManagementContext managementContext) {
        if (!inManage.compareAndSet(false, true)) {
            throw new IllegalStateException("Concurrent and re-entrant calls to manage() forbidden on "+this);
        }
        try {
            checkNotManaged();
            this.app = managementContext.getEntityManager().createEntity(appSpec);
            this.managementContext = managementContext;
            doBuild();
            Entities.startManagement(app, managementContext);
            managed = true;
            return app;
        } finally {
            inManage.set(false);
        }
    }
    
    protected void checkPreManage() {
        if (inManage.get()) {
            throw new IllegalStateException("Builder being managed; cannot perform operation during call to manage(), or in doBuild()");
        }
        if (managed) {
            throw new IllegalStateException("Builder already managed; cannot perform operation after call to manage()");
        }
    }
    
    protected void checkNotManaged() {
        if (managed) {
            throw new IllegalStateException("Builder already managed; cannot perform operation after call to manage()");
        }
    }
    
    protected void checkDuringManage() {
        if (!inManage.get() || app == null) {
            throw new IllegalStateException("Operation only permitted during manage, e.g. called from doBuild() of "+this);
        }
    }
}
