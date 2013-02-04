package brooklyn.entity.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.WrappingEntitySpec;
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
 *           MySqlNode db = createChild(BasicEntitySpec.newInstance(MySqlNode.class)));
 *           JBoss7Server as = createChild(BasicEntitySpec.newInstance(JBoss7Server.class)
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
 *           .child(BasicEntitySpec.newInstance(MySqlNode.class))
 *           .child(BasicEntitySpec.newInstance(JBoss7Server.class))
 *           .manage();
 * </code>
 * 
 * @author aled
 */
public abstract class ApplicationBuilder {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(ApplicationBuilder.class);

    public static Builder<StartableApplication> builder() {
        return new Builder<StartableApplication>().app(BasicEntitySpec.newInstance(BasicApplication.class));
    }

    public static <T extends Application> Builder<T> builder(EntitySpec<T> appSpec) {
        return new Builder<T>().app(appSpec);
    }

    public static <T extends Application> Builder<T> builder(Class<T> type) {
        return new Builder<T>().app(type);
    }

    public static class Builder<T extends Application> {
        protected volatile boolean managed = false;
        private BasicEntitySpec<? extends T, ?> appSpec;
        private List<EntitySpec<?>> childSpecs = Lists.newArrayList();
        
        // Use static builder methods
        protected Builder() {}
        
        // Use static builder methods
        protected Builder<T> app(EntitySpec<? extends T> val) {
            checkNotManaged();
            this.appSpec = WrappingEntitySpec.newInstance(val); 
            return this;
        }
        
        // Use static builder methods
        protected Builder<T> app(Class<? extends T> type) {
            checkNotManaged();
            this.appSpec = BasicEntitySpec.newInstance(type); 
            return this;
        }
        
        public Builder<T> displayName(String val) {
            checkNotManaged();
            appSpec.displayName(val);
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
    private BasicEntitySpec<? extends StartableApplication, ?> appSpec;
    private ManagementContext managementContext;
    private StartableApplication app;
    
    public ApplicationBuilder() {
        this.appSpec = BasicEntitySpec.newInstance(BasicApplication.class);
    }

    public ApplicationBuilder(EntitySpec<? extends StartableApplication> appSpec) {
        this.appSpec = WrappingEntitySpec.newInstance(appSpec);
    }

    public final ApplicationBuilder appDisplayName(String val) {
        appSpec.displayName(val);
        return this;
    }
    
    protected final <T extends Entity> T createEntity(EntitySpec<T> spec) {
        checkNotManaged();
        EntityManager entityManager = managementContext.getEntityManager();
        return entityManager.createEntity(spec);
    }

    protected final StartableApplication getApplication() { return app; }
    
    protected final <T extends Entity> T addChild(T entity) {
        checkNotManaged();
        app.addChild(entity);
        return entity;
    }
    
    protected final <T extends Entity> T createChild(EntitySpec<T> spec) {
        checkNotManaged();
        return addChild(createEntity(spec));
    }
    
    protected final <T extends Entity> T createChild(Map<?,?> config, Class<T> type) {
        checkNotManaged();
        BasicEntitySpec<T,?> spec = BasicEntitySpec.newInstance(type);
        spec.configure(config);
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

    public final StartableApplication manage() {
        return manage(Entities.newManagementContext());
    }
    
    public final StartableApplication manage(ManagementContext managementContext) {
        checkNotManaged();
        this.app = managementContext.getEntityManager().createEntity(appSpec);
        this.managementContext = managementContext;
        doBuild();
        Entities.startManagement(app, managementContext);
        managed = true;
        return app;
    }
    
    protected void checkNotManaged() {
        if (managed) {
            throw new IllegalStateException("Builder already managed; cannot perform operation after call to manage()");
        }
    }
}
