package brooklyn.entity.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.management.EntityManager;
import brooklyn.management.ManagementContext;

import com.google.common.collect.Lists;

/**
 * For building an application. There are two ways to use this:
 * <ul>
 *   <li>By sub-classing and overriding doBuild(), putting the logic for creating and wiring 
 *       together entities in there.
 *   <li>As a conventional builder, using ApplicationBuilder.build().
 *       This is simpler to use, but less powerful for injecting configuration of one entity into other entities.
 * </ul>
 * 
 * The builder is mutable; a given instance should be used to build only a single application.
 * Once {@link manage()} has been called, the application will be built and no additional configuration
 * should be performed through this builder.  
 * 
 * Example (simplified) code for sub-classing is:
 * <code>
 *   app = new ApplicationBuilder() {
 *       @Override public void doBuild() {
 *           MySqlNode db = addChild(EntitySpecs.spec(MySqlNode.class)));
 *           JBoss7Server as = addChild(EntitySpecs.spec(JBoss7Server.class)
 *                   .configure(HTTP_PORT, "8080+")
 *                   .configure(javaSysProp("brooklyn.example.db.url"), attributeWhenReady(db, MySqlNode.MYSQL_URL));
 *       }
 *   }.manage();
 * </code>
 * 
 * And example code for using the builder:
 * 
 * <code>
 *   app = ApplicationBuilder.builder()
 *           .child(EntitySpecs.spec(MySqlNode.class))
 *           .child(EntitySpecs.spec(JBoss7Server.class))
 *           .manage();
 * </code>
 * 
 * @author aled
 */
public abstract class ApplicationBuilder {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(ApplicationBuilder.class);

    /**
     * @deprecated since 0.5.0-rc.1 (added in 0.5.0-M2)
     */
    public static <T extends StartableApplication> BasicEntitySpec<StartableApplication, ?> newAppSpec(Class<? extends T> type) {
        return EntitySpecs.appSpec(type);
    }

    public static Builder<StartableApplication> builder() {
        return new Builder<StartableApplication>().app(EntitySpecs.spec(BasicApplication.class));
    }

    public static <T extends Application> Builder<T> builder(EntitySpec<T> appSpec) {
        return new Builder<T>().app(appSpec);
    }

    /** smart builder factory method which takes an interface type _or_ an implementation type */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T extends Application,U extends T> Builder<T> builder(Class<? extends T> type) {
        // TODO Don't think we want to handle abstract classes like this; it's not an interface so can't be
        // used for proxying
        if (type.isInterface() || ((type.getModifiers() & Modifier.ABSTRACT)!=0))
            // is interface or abstract
            return new Builder<T>().app(type);
        else {
            // is implementation
            Class interfaceType = (StartableApplication.class.isAssignableFrom(type)) ? StartableApplication.class : Application.class;
            Class<?>[] additionalInterfaceClazzes = type.getInterfaces();
            EntitySpec<T> spec = EntitySpecs.spec((Class<T>)interfaceType, (Class<U>)type)
                    .additionalInterfaces(additionalInterfaceClazzes);
            return new Builder<T>().app(spec);
        }
    }

    public static <T extends Application> Builder<T> builder(Class<T> interfaceType, Class<T> implType) {
        return new Builder<T>().app(EntitySpecs.spec(interfaceType, implType));
    }

    public static class Builder<T extends Application> {
        protected volatile boolean managed = false;
        private BasicEntitySpec<T, ?> appSpec;
        private List<EntitySpec<?>> childSpecs = Lists.newArrayList();
        
        // Use static builder methods
        protected Builder() {}
        
        // Use static builder methods
        protected Builder<T> app(EntitySpec<? extends T> val) {
            checkNotManaged();
            this.appSpec = EntitySpecs.wrapSpec(val); 
            return this;
        }
        
        // Use static builder methods
        @SuppressWarnings("unchecked")
        protected Builder<T> app(Class<? extends T> type) {
            checkNotManaged();
            this.appSpec = EntitySpecs.spec((Class<T>)type); 
            return this;
        }
        
        public Builder<T> appImpl(Class<? extends T> val) {
            checkNotManaged();
            appSpec.impl(val);
            return this;
        }
        
        public Builder<T> displayName(String val) {
            checkNotManaged();
            appSpec.displayName(val);
            return this;
        }
        
        public final Builder<T> configure(Map<?,?> config) {
            appSpec.configure(config);
            return this;
        }

        public Builder<T> child(EntitySpec<?> val) {
            checkNotManaged();
            childSpecs.add(val);
            return this;
        }
        
        public final T manage() {
            return manage(Entities.newManagementContext());
        }
        
        public final T manage(ManagementContext managementContext) {
            checkNotManaged();
            managed = true;
            T app = managementContext.getEntityManager().createEntity(appSpec);
            for (EntitySpec<?> childSpec : childSpecs) {
                Entity child = managementContext.getEntityManager().createEntity(childSpec);
                app.addChild(child);
            }
            Entities.startManagement(app, managementContext);
            return app;
        }
        
        protected void checkNotManaged() {
            if (managed) throw new IllegalStateException("Builder already managed; cannot perform operation after call to manage()");
        }
    }
    
    protected volatile boolean managed = false;
    protected final AtomicBoolean inManage = new AtomicBoolean(false);
    private BasicEntitySpec<? extends StartableApplication, ?> appSpec;
    private ManagementContext managementContext;
    private StartableApplication app;
    
    public ApplicationBuilder() {
        this.appSpec = EntitySpecs.spec(BasicApplication.class);
    }

    public ApplicationBuilder(EntitySpec<? extends StartableApplication> appSpec) {
        this.appSpec = EntitySpecs.wrapSpec(appSpec);
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
        app.addChild(entity);
        return entity;
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
     * @deprecated since 0.5.0-rc.1 (added in 0.5.0-M2); use {@link #addChild(EntitySpec)}, 
     *             for consistency with {@link AbstractEntity#addChild(EntitySpec)}.
     */
    protected final <T extends Entity> T createChild(EntitySpec<T> spec) {
        return addChild(spec);
    }
    
    /**
     * @deprecated since 0.5.0-rc.1 (added in 0.5.0-M2); use {@link #addChild(Map, Class)}
     */
    protected final <T extends Entity> T createChild(Map<?,?> config, Class<T> type) {
        return addChild(config, type);
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
        EntitySpec<T> spec = EntitySpecs.spec(type).configure(config);
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
