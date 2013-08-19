package brooklyn.entity.basic;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.drivers.EntityDriver;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.management.LocationManager;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.internal.EffectorUtils;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.task.ParallelTask;
import brooklyn.util.task.Tasks;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;

/**
 * Convenience methods for working with entities.
 * <p>
 * Also see the various {@code *Methods} classes for traits,
 * such as {@link StartableMethods} for {@link Startable} implementations.
 */
public class Entities {

    private static final Logger log = LoggerFactory.getLogger(Entities.class);

    /**
     * Names that, if they appear anywhere in an attribute/config/field indicates that it
     * may be private, so should not be logged etc.
     */
    private static final List<String> SECRET_NAMES = ImmutableList.of(
            "password",
            "passwd",
            "credential",
            "secret",
            "private",
            "access.cert",
            "access.key");

    /**
     * Invokes an {@link Effector} on multiple entities, with the named arguments from the parameters {@link Map}
     * using the context of the provided {@link Entity}.
     * <p>
     * Intended for use only from the callingEntity.
     * <p>
     * Returns a {@link ParallelTask} containing the results from each tasks invocation. Calling
     * {@link java.util.concurrent.Future#get() get()} on this will block until all tasks are complete,
     * and will throw an exception if any task resulted in an error.
     *
     * @return {@link ParallelTask} containing results from each invocation
     */
    public static <T> Task<List<T>> invokeEffectorList(EntityLocal callingEntity, Iterable<? extends Entity> entitiesToCall,
            final Effector<T> effector, final Map<String,?> parameters) {
        // formulation is complicated, but it is building up a list of tasks, without blocking on them initially,
        // but ensuring that when the parallel task is gotten it does block on all of them

        if (entitiesToCall == null){
            entitiesToCall = ImmutableList.of();
        }

        List<TaskAdaptable<T>> tasks = Lists.newArrayList();

        for (final Entity entity : entitiesToCall) {
            tasks.add( Effectors.invocation(entity, effector, parameters) );
        }
        ParallelTask<T> invoke = new ParallelTask<T>(
                MutableMap.of(
                        "displayName", effector.getName()+" (parallel)",
                        "description", "Invoking effector \""+effector.getName()+"\" on "+tasks.size()+(tasks.size() == 1 ? " entity" : " entities"),
                        "tag", BrooklynTasks.tagForCallerEntity(callingEntity)),
                tasks);
        ((EntityInternal)callingEntity).getManagementSupport().getExecutionContext().submit(invoke);
        return invoke;
    }
    public static <T> Task<List<T>> invokeEffectorListWithMap(EntityLocal callingEntity, Iterable<? extends Entity> entitiesToCall,
            final Effector<T> effector, final Map<String,?> parameters) {
        return invokeEffectorList(callingEntity, entitiesToCall, effector, parameters);
    }
    @SuppressWarnings("unchecked")
    public static <T> Task<List<T>> invokeEffectorListWithArgs(EntityLocal callingEntity, Iterable<? extends Entity> entitiesToCall,
            final Effector<T> effector, Object ...args) {
        return invokeEffectorListWithMap(callingEntity, entitiesToCall, effector,
                // putting into a map, unnecessarily, as it ends up being the array again...
                EffectorUtils.prepareArgsForEffectorAsMapFromArray(effector, args));
    }
    public static <T> Task<List<T>> invokeEffectorList(EntityLocal callingEntity, Iterable<? extends Entity> entitiesToCall,
            final Effector<T> effector) {
        return invokeEffectorList(callingEntity, entitiesToCall, effector, Collections.<String,Object>emptyMap());
    }

    /** @deprecated since 0.6.0 use invokeEffector */ @Deprecated
    public static <T> Task<List<T>> invokeEffectorWithMap(EntityLocal callingEntity, Entity entityToCall,
            final Effector<T> effector, final Map<String,?> parameters) {
        return invokeEffectorList(callingEntity, ImmutableList.of(entityToCall), effector, parameters);
    }
    
    public static <T> Task<T> invokeEffector(EntityLocal callingEntity, Entity entityToCall,
            final Effector<T> effector, final Map<String,?> parameters) {
        Task<T> t = Effectors.invocation(entityToCall, effector, parameters).asTask();
        // we pass to callingEntity for consistency above, but in exec-context it should be
        // re-dispatched to targetEntity
        ((EntityInternal)callingEntity).getManagementSupport().getExecutionContext().submit(
                MutableMap.of("tag", BrooklynTasks.tagForCallerEntity(callingEntity)), t);
        return t;
    }
    @SuppressWarnings("unchecked")
    public static <T> Task<T> invokeEffectorWithArgs(EntityLocal callingEntity, Entity entityToCall,
            final Effector<T> effector, Object ...args) {
        return invokeEffector(callingEntity, entityToCall, effector,
                EffectorUtils.prepareArgsForEffectorAsMapFromArray(effector, args));
    }
    public static <T> Task<T> invokeEffector(EntityLocal callingEntity, Entity entityToCall,
            final Effector<T> effector) {
        return invokeEffector(callingEntity, entityToCall, effector, Collections.<String,Object>emptyMap());
    }

    /** convenience - invokes in parallel if multiple, but otherwise invokes the item directly */
    public static Task<?> invokeEffector(EntityLocal callingEntity, Iterable<? extends Entity> entitiesToCall,
            final Effector<?> effector, final Map<String,?> parameters) {
        if (Iterables.size(entitiesToCall)==1)
            return invokeEffector(callingEntity, entitiesToCall.iterator().next(), effector, parameters);
        else
            return invokeEffectorList(callingEntity, entitiesToCall, effector, parameters);
    }
    /** convenience - invokes in parallel if multiple, but otherwise invokes the item directly */
    public static Task<?> invokeEffector(EntityLocal callingEntity, Iterable<? extends Entity> entitiesToCall,
            final Effector<?> effector) {
        return invokeEffector(callingEntity, entitiesToCall, effector, Collections.<String,Object>emptyMap());
    }
    
    // ------------------------------------
    
    public static boolean isSecret(String name) {
        String lowerName = name.toLowerCase();
        for (String secretName : SECRET_NAMES) {
            if (lowerName.contains(secretName)) return true;
        }
        return false;
    }

    public static boolean isTrivial(Object v) {
        return v==null || (v instanceof Map && ((Map<?,?>)v).isEmpty()) ||
                (v instanceof Collection && ((Collection<?>)v).isEmpty()) ||
                (v instanceof CharSequence&& ((CharSequence)v).length() == 0);
    }

    public static <K> Map<K,Object> sanitize(Map<K,?> input) {
        Map<K,Object> result = Maps.newLinkedHashMap();
        for (Map.Entry<K,?> e: input.entrySet()) {
            if (isSecret(""+e.getKey())) result.put(e.getKey(), "xxxxxxxx");
            else result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    public static void dumpInfo(Iterable<? extends Entity> entities) {
        for (Entity e : entities) {
            dumpInfo(e);
        }
    }

    public static void dumpInfo(Entity e) {
        try {
            dumpInfo(e, new PrintWriter(System.out), "", "  ");
        } catch (IOException exc) {
            // system.out throwing an exception is odd, so don't have IOException on signature
            throw new RuntimeException(exc);
        }
    }
    public static void dumpInfo(Entity e, Writer out) throws IOException {
        dumpInfo(e, out, "", "  ");
    }
    public static void dumpInfo(Entity e, String currentIndentation, String tab) throws IOException {
        dumpInfo(e, new PrintWriter(System.out), currentIndentation, tab);
    }
    public static void dumpInfo(Entity e, Writer out, String currentIndentation, String tab) throws IOException {
        out.append(currentIndentation+e.toString()+"\n");

        out.append(currentIndentation+tab+tab+"locations = "+e.getLocations()+"\n");

        for (ConfigKey<?> it : sortConfigKeys(e.getEntityType().getConfigKeys())) {
            Object v = ((EntityInternal)e).getConfigMap().getRawConfig(it);
            if (!isTrivial(v)) {
                out.append(currentIndentation+tab+tab+it.getName());
                out.append(" = ");
                if (isSecret(it.getName())) out.append("xxxxxxxx");
                else if ((v instanceof Task) && ((Task<?>)v).isDone()) {
                    if (((Task<?>)v).isError()) {
                        out.append("ERROR in "+v);
                    } else {
                        try {
                            out.append(((Task<?>)v).get() + " (from "+v+")");
                        } catch (ExecutionException ee) {
                            throw new IllegalStateException("task "+v+" done and !isError, but threw exception on get", ee);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                } else out.append(""+v);
                out.append("\n");
            }
        }

        for (Sensor<?> it : sortSensors(e.getEntityType().getSensors())) {
            if (it instanceof AttributeSensor) {
                Object v = e.getAttribute((AttributeSensor<?>)it);
                if (!isTrivial(v)) {
                    out.append(currentIndentation+tab+tab+it.getName());
                    out.append(": ");
                    if (isSecret(it.getName())) out.append("xxxxxxxx");
                    else out.append(""+v);
                    out.append("\n");
                }
            }
        }

        if (e instanceof Group) {
            StringBuilder members = new StringBuilder();
            for (Entity it : ((Group)e).getMembers()) {
                members.append(it.getId()+", ");
            }
            out.append(currentIndentation+tab+tab+"Members: "+members.toString()+"\n");
        }

        out.append(currentIndentation+tab+tab+"Policies:\n");
        for (Policy policy : e.getPolicies()) {
            dumpInfo(policy, out, currentIndentation+tab+tab+tab, tab);
        }

        for (Entity it : e.getChildren()) {
            dumpInfo(it, out, currentIndentation+tab, tab);
        }

        out.flush();
    }

    public static void dumpInfo(Location loc) {
        try {
            dumpInfo(loc, new PrintWriter(System.out), "", "  ");
        } catch (IOException exc) {
            // system.out throwing an exception is odd, so don't have IOException on signature
            throw new RuntimeException(exc);
        }
    }
    public static void dumpInfo(Location loc, Writer out) throws IOException {
        dumpInfo(loc, out, "", "  ");
    }
    public static void dumpInfo(Location loc, String currentIndentation, String tab) throws IOException {
        dumpInfo(loc, new PrintWriter(System.out), currentIndentation, tab);
    }
    @SuppressWarnings("rawtypes")
    public static void dumpInfo(Location loc, Writer out, String currentIndentation, String tab) throws IOException {
        out.append(currentIndentation+loc.toString()+"\n");

        for (Object entryO : loc.getAllConfig(true).entrySet()) {
            Map.Entry entry = (Map.Entry)entryO;
            Object keyO = entry.getKey();
            String key =
                    keyO instanceof HasConfigKey ? ((HasConfigKey)keyO).getConfigKey().getName() :
                    keyO instanceof ConfigKey ? ((ConfigKey)keyO).getName() :
                    keyO == null ? null :
                    keyO.toString();
            Object val = entry.getValue();
            if (!isTrivial(val)) {
                out.append(currentIndentation+tab+tab+key);
                out.append(" = ");
                if (isSecret(key)) out.append("xxxxxxxx");
                else out.append(""+val);
                out.append("\n");
            }
        }


        for (Map.Entry<String,?> entry : sortMap(FlagUtils.getFieldsWithFlags(loc)).entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (!isTrivial(val)) {
                out.append(currentIndentation+tab+tab+key);
                out.append(" = ");
                if (isSecret(key)) out.append("xxxxxxxx");
                else out.append(""+val);
                out.append("\n");
            }
        }

        for (Location it : loc.getChildren()) {
            dumpInfo(it, out, currentIndentation+tab, tab);
        }

        out.flush();
    }

    public static void dumpInfo(Policy pol) {
        try {
            dumpInfo(pol, new PrintWriter(System.out), "", "  ");
        } catch (IOException exc) {
            // system.out throwing an exception is odd, so don't have IOException on signature
            throw new RuntimeException(exc);
        }
    }
    public static void dumpInfo(Policy pol, Writer out) throws IOException {
        dumpInfo(pol, out, "", "  ");
    }
    public static void dumpInfo(Policy pol, String currentIndentation, String tab) throws IOException {
        dumpInfo(pol, new PrintWriter(System.out), currentIndentation, tab);
    }
    public static void dumpInfo(Policy pol, Writer out, String currentIndentation, String tab) throws IOException {
        out.append(currentIndentation+pol.toString()+"\n");

        for (ConfigKey<?> key : sortConfigKeys(pol.getPolicyType().getConfigKeys())) {
            Object val = ((AbstractPolicy)pol).getConfigMap().getRawConfig(key);
            if (!isTrivial(val)) {
                out.append(currentIndentation+tab+tab+key);
                out.append(" = ");
                if (isSecret(key.getName())) out.append("xxxxxxxx");
                else out.append(""+val);
                out.append("\n");
            }
        }

        out.flush();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static List<Sensor<?>> sortSensors(Set<Sensor<?>> sensors) {
        List result = new ArrayList(sensors);
        Collections.sort(result, new Comparator<Sensor>() {
                    @Override
                    public int compare(Sensor arg0, Sensor arg1) {
                        return arg0.getName().compareTo(arg1.getName());
                    }

        });
        return result;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static List<ConfigKey<?>> sortConfigKeys(Set<ConfigKey<?>> configs) {
        List result = new ArrayList(configs);
        Collections.sort(result, new Comparator<ConfigKey>() {
                    @Override
                    public int compare(ConfigKey arg0, ConfigKey arg1) {
                        return arg0.getName().compareTo(arg1.getName());
                    }

        });
        return result;
    }

    public static <T> Map<String, T> sortMap(Map<String, T> map) {
        Map<String,T> result = Maps.newLinkedHashMap();
        List<String> order = Lists.newArrayList(map.keySet());
        Collections.sort(order, String.CASE_INSENSITIVE_ORDER);

        for (String key : order) {
            result.put(key, map.get(key));
        }
        return result;
    }

    public static boolean isAncestor(Entity descendant, Entity potentialAncestor) {
        Entity ancestor = descendant.getParent();
        while (ancestor != null) {
            if (ancestor.equals(potentialAncestor)) return true;
            ancestor = ancestor.getParent();
        }
        return false;
    }

    /** note, it is usually preferred to use isAncestor() and swap the order, it is a cheaper method */
    public static boolean isDescendant(Entity ancestor, Entity potentialDescendant) {
        Set<Entity> inspected = Sets.newLinkedHashSet();
        Stack<Entity> toinspect = new Stack<Entity>();
        toinspect.add(ancestor);

        while (!toinspect.isEmpty()) {
            Entity e = toinspect.pop();
            if (e.getChildren().contains(potentialDescendant)) {
                return true;
            }
            inspected.add(e);
            toinspect.addAll(e.getChildren());
            toinspect.removeAll(inspected);
        }

        return false;
    }

    private static final List<Entity> entitiesToStopOnShutdown = Lists.newArrayList();
    private static final AtomicBoolean isShutdownHookRegistered = new AtomicBoolean();

    public static void invokeStopOnShutdown(Entity entity) {
        if (isShutdownHookRegistered.compareAndSet(false, true)) {
            ResourceUtils.addShutdownHook(new Runnable() {
                @SuppressWarnings({ "unchecked", "rawtypes" })
                public void run() {
                    synchronized (entitiesToStopOnShutdown) {
                        log.info("Brooklyn stopOnShutdown shutdown-hook invoked: stopping "+entitiesToStopOnShutdown);
                        List<Task> stops = new ArrayList<Task>();
                        for (Entity entity: entitiesToStopOnShutdown) {
                            try {
                                stops.add(entity.invoke(Startable.STOP, new MutableMap()));
                            } catch (Exception exc) {
                                log.debug("stopOnShutdown of "+entity+" returned error: "+exc, exc);
                            }
                        }
                        for (Task t: stops) {
                            try {
                                log.debug("stopOnShutdown of {} completed: {}", t, t.get());
                            } catch (Exception exc) {
                                log.debug("stopOnShutdown of "+t+" returned error: "+exc, exc);
                            }
                        }
                    }
                }
            });
        }
        synchronized (entitiesToStopOnShutdown) {
            entitiesToStopOnShutdown.add(entity);
        }
    }

    /** convenience for starting an entity, esp a new Startable instance which has been created dynamically
     * (after the application is started) */
    public static void start(Entity e, Collection<? extends Location> locations) {
        if (!isManaged(e) && !manage(e)) {
            log.warn("Using discouraged mechanism to start management -- Entities.start(Application, Locations) -- caller should create and use the preferred management context");
            startManagement(e);
        }
        if (e instanceof Startable) Entities.invokeEffector((EntityLocal)e, e, Startable.START,
                MutableMap.of("locations", locations)).getUnchecked();
    }

    /** stops, destroys, and unmanages the given entity -- does as many as are valid given the type and state */
    public static void destroy(Entity e) {
        if (isManaged(e)) {
            if (e instanceof Startable) Entities.invokeEffector((EntityLocal)e, e, Startable.STOP).getUnchecked();
            if (e instanceof EntityInternal) ((EntityInternal)e).destroy();
            unmanage(e);
            log.debug("destroyed and unmanaged "+e+"; mgmt now "+
                    (e.getApplicationId()==null ? "(no app)" : e.getApplication().getManagementContext())+" - managed? "+isManaged(e));
        } else {
            log.debug("skipping destroy of "+e+": not managed");
        }
    }
    
    /** as {@link #destroy(Entity)} but catching all errors */
    public static void destroyCatching(Entity entity) {
        try {
            destroy(entity);
        } catch (Exception e) {
            log.warn("ERROR destroying "+entity+" (ignoring): "+e, e);
            Exceptions.propagateIfFatal(e);
        }
    }

    /** stops, destroys, and unmanages all apps in the given context,
     * and then terminates the management context */
    public static void destroyAll(ManagementContext mgmt) {
        if (!mgmt.isRunning()) return;
        log.debug("destroying all apps in "+mgmt+": "+mgmt.getApplications());
        for (Application app: mgmt.getApplications()) {
            log.debug("destroying app "+app+" (managed? "+isManaged(app)+"; mgmt is "+mgmt+")");
            destroy(app);
            log.debug("destroyed app "+app+"; mgmt now "+mgmt);
        }
        if (mgmt instanceof ManagementContextInternal) 
            ((ManagementContextInternal)mgmt).terminate();
    }

    /** as {@link #destroyAll(ManagementContext)} but catching all errors */
    public static void destroyAllCatching(ManagementContext mgmt) {
        try {
            destroyAll(mgmt);
        } catch (Exception e) {
            log.warn("ERROR destroying "+mgmt+" (ignoring): "+e, e);
            Exceptions.propagateIfFatal(e);
        }
    }

    /**
     * stops, destroys, and unmanages the given application -- and terminates the mangaement context;
     * does as many as are valid given the type and state
     * @deprecated since 0.6.0 use destroy(Application) if you DONT want to destroy the mgmt context,
     * or destroy(app.getManagementContext()) if you want to destory everything in the app's mgmt context
     */
    @Deprecated
    public static void destroyAll(Application app) {
        if (isManaged(app)) {
            ManagementContext managementContext = app.getManagementContext();
            if (app instanceof Startable) Entities.invokeEffector((EntityLocal)app, app, Startable.STOP).getUnchecked();
            if (app instanceof AbstractEntity) ((AbstractEntity)app).destroy();
            unmanage(app);
            if (managementContext instanceof ManagementContextInternal) ((ManagementContextInternal)managementContext).terminate();
        }
    }

    public static boolean isManaged(Entity e) {
        return ((EntityInternal)e).getManagementSupport().isDeployed() && ((EntityInternal)e).getManagementContext().isRunning();
    }

    /** brings this entity under management iff its ancestor is managed, returns true in that case;
     * otherwise returns false in the expectation that the ancestor will become managed,
     * or throws exception if it has no parent or a non-application root
     * (will throw if e is an Application; see also {@link #startManagement(Entity)} ) */
    public static boolean manage(Entity e) {
        Entity o = e.getParent();
        Entity eum = e; //highest unmanaged ancestor
        if (o==null) throw new IllegalStateException("Can't manage "+e+" because it is an orphan");
        while (o.getParent()!=null) {
            if (!isManaged(o)) eum = o;
            o = o.getParent();
        }
        if (isManaged(o)) {
            ((EntityInternal)o).getManagementContext().getEntityManager().manage(eum);
            return true;
        }
        if (!(o instanceof Application))
            throw new IllegalStateException("Can't manage "+e+" because it is not rooted at an application");
        return false;
    }

    /** brings this entity under management, creating a local management context if necessary
     * (assuming root is an application).
     * returns existing management context if there is one (non-deployment),
     * or new local mgmt context if not,
     * or throwing exception if root is not an application
     * <p>
     * callers are recommended to use {@link #manage(Entity)} instead unless they know
     * a plain-vanilla non-root management context is sufficient (e.g. in tests)
     * <p>
     * this method may change, but is provided as a stop-gap to prevent ad-hoc things
     * being done in the code which are even more likely to break! */
    public static ManagementContext startManagement(Entity e) {
        Entity o = e;
        Entity eum = e; //highest unmanaged ancestor
        while (o.getParent()!=null) {
            if (!isManaged(o)) eum = o;
            o = o.getParent();
        }
        if (isManaged(o)) {
            ManagementContext mgmt = ((EntityInternal)o).getManagementContext();
            mgmt.getEntityManager().manage(eum);
            return mgmt;
        }
        if (!(o instanceof Application))
            throw new IllegalStateException("Can't manage "+e+" because it is not rooted at an application");
        ManagementContext mgmt = new LocalManagementContext();
        mgmt.getEntityManager().manage(o);
        return mgmt;
    }

    /**
     * Starts managing the given (unmanaged) app, using the given management context.
     *
     * @see #startManagement(Entity)
     */
    public static ManagementContext startManagement(Application app, ManagementContext mgmt) {
        if (isManaged(app)) {
            throw new IllegalStateException("Application "+app+" is already managed, so can't set brooklyn properties");
        }
        mgmt.getEntityManager().manage(app);
        return mgmt;
    }

    /**
     * Starts managing the given (unmanaged) app, setting the given brooklyn properties on the new
     * management context.
     *
     * @see #startManagement(Entity)
     */
    public static ManagementContext startManagement(Application app, BrooklynProperties props) {
        if (isManaged(app)) {
            throw new IllegalStateException("Application "+app+" is already managed, so can't set brooklyn properties");
        }
        ManagementContext mgmt = new LocalManagementContext(props);
        mgmt.getEntityManager().manage(app);
        return mgmt;
    }

    public static ManagementContext newManagementContext() {
        return new LocalManagementContext();
    }

    public static ManagementContext newManagementContext(BrooklynProperties props) {
        return new LocalManagementContext(props);
    }

    public static ManagementContext newManagementContext(Map<?,?> props) {
        return new LocalManagementContext( BrooklynProperties.Factory.newEmpty().addFromMap(props));
    }

    public static void unmanage(Entity entity) {
        if (((EntityInternal)entity).getManagementSupport().isDeployed()) {
            ((EntityInternal)entity).getManagementContext().getEntityManager().unmanage(entity);
        }
    }

    public static DownloadResolver newDownloader(EntityDriver driver) {
        EntityInternal internal = (EntityInternal) driver.getEntity();
        return internal.getManagementContext().getEntityDownloadsManager().newDownloader(driver);
    }

    public static <T> Supplier<T> attributeSupplier(final Entity entity, final AttributeSensor<T> sensor) {
        return new Supplier<T>() {
            public T get() { return entity.getAttribute(sensor); }
        };
    }

    
    public static <T> Supplier<T> attributeSupplier(final EntityAndAttribute<T> tuple) {
        return Entities.attributeSupplier(tuple.getEntity(), tuple.getAttribute());
    }

    public static <T> Supplier<T> attributeSupplierWhenReady(final EntityAndAttribute<T> tuple) {
        return attributeSupplierWhenReady(tuple.getEntity(), tuple.getAttribute());
    }
    
    @SuppressWarnings({ "unchecked", "serial" })
    public static <T> Supplier<T> attributeSupplierWhenReady(final Entity entity, final AttributeSensor<T> sensor) {
        final Task<T> task = DependentConfiguration.attributeWhenReady(entity, sensor);
        return new Supplier<T>() {
            @Override public T get() {
                try {
                    TypeToken<T> type = new TypeToken<T>(sensor.getType()) {};
                    return Tasks.resolveValue(task, (Class<T>) type.getRawType(), ((EntityInternal) entity).getExecutionContext(), "attributeSupplierWhenReady");
                } catch (Exception e) {
                    throw Exceptions.propagate(e);
                }
            }
        };
    }
    
    /**
     * Registers the given location (and all its children) with the management context. 
     * @throws IllegalStateException if the parent location is not already managed
     * 
     * @since 0.6.0 (added only for backwards compatibility, where locations are being created directly).
     * @deprecated in 0.6.0; use {@link LocationManager#createLocation(LocationSpec)} instead.
     */
    public static void manage(Location loc, ManagementContext managementContext) {
        if (!managementContext.getLocationManager().isManaged(loc)) {
            log.warn("Deprecated use of unmanaged location ("+loc+"); will be managed automatically now but not supported in future versions");
            // FIXME this occurs MOST OF THE TIME e.g. including BrooklynLauncher.location(locationString)
            // not sure what is the recommend way to convert from locationString to locationSpec, or the API we want to expose;
            // deprecating some of the LocationRegistry methods seems sensible?
            log.debug("Stack trace for location of: Deprecated use of unmanaged location; will be managed automatically now but not supported in future versions", new Exception("TRACE for: Deprecated use of unmanaged location"));
            managementContext.getLocationManager().manage(loc);
        }
    }
    
    /** fails-fast if value of the given key is null or unresolveable */
    public static String getRequiredUrlConfig(Entity entity, ConfigKey<String> urlKey) {
        String url = entity.getConfig(urlKey);
        if (url==null)
            throw new NullPointerException("Key "+urlKey+" on "+entity+" should not be null");
        return new ResourceUtils(entity).checkUrlExists(url);
    }
}
