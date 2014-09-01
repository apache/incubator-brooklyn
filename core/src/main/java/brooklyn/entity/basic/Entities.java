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

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.Application;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.drivers.EntityDriver;
import brooklyn.entity.drivers.downloads.DownloadResolver;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocationInternal;
import brooklyn.location.basic.Locations;
import brooklyn.management.ExecutionContext;
import brooklyn.management.LocationManager;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.TaskFactory;
import brooklyn.management.internal.EffectorUtils;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.management.internal.NonDeploymentManagementContext;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.guava.Maybe;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.stream.Streams;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.ParallelTask;
import brooklyn.util.task.TaskTags;
import brooklyn.util.task.Tasks;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.task.system.SystemTasks;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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

    /** Special object used by some setting methods to indicate that a value should be ignored. 
     * See specific usages of this field to confirm where. */
    public static final Object UNCHANGED = new Object();
    /** Special object used by some setting methods to indicate that a value should be removed. 
     * See specific usages of this field to confirm where. */
    public static final Object REMOVE = new Object();
    
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
                        "tag", BrooklynTaskTags.tagForCallerEntity(callingEntity)),
                tasks);
        TaskTags.markInessential(invoke);
        return DynamicTasks.queueIfPossible(invoke).orSubmitAsync(callingEntity).asTask();
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

    public static <T> Task<T> invokeEffector(EntityLocal callingEntity, Entity entityToCall,
            final Effector<T> effector, final Map<String,?> parameters) {
        Task<T> t = Effectors.invocation(entityToCall, effector, parameters).asTask();
        TaskTags.markInessential(t);
        
        // we pass to callingEntity for consistency above, but in exec-context it should be re-dispatched to targetEntity
        // reassign t as the return value may be a wrapper, if it is switching execution contexts; see submitInternal's javadoc
        t = ((EntityInternal)callingEntity).getManagementSupport().getExecutionContext().submit(
                MutableMap.of("tag", BrooklynTaskTags.tagForCallerEntity(callingEntity)), t);
        
        if (DynamicTasks.getTaskQueuingContext()!=null) {
            // include it as a child (in the gui), marked inessential, because the caller is invoking programmatically
            DynamicTasks.queue(t);
        }
        
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
        if (v instanceof Maybe) {
            if (!((Maybe<?>)v).isPresent())
                return true;
            v = ((Maybe<?>) v).get();
        }
        
        return v==null || (v instanceof Map && ((Map<?,?>)v).isEmpty()) ||
                (v instanceof Collection && ((Collection<?>)v).isEmpty()) ||
                (v instanceof CharSequence&& ((CharSequence)v).length() == 0);
    }

    public static Map<String,Object> sanitize(ConfigBag input) {
        return sanitize(input.getAllConfig());
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
        out.append(currentIndentation+e.toString()+" "+e.getId()+"\n");

        out.append(currentIndentation+tab+tab+"locations = "+e.getLocations()+"\n");

        Set<ConfigKey<?>> keys = Sets.newLinkedHashSet( ((EntityInternal)e).getConfigMap().getLocalConfig().keySet() );
        for (ConfigKey<?> it : sortConfigKeys(keys)) {
            // use the official config key declared on the type if available
            // (since the map sometimes contains <object> keys
            ConfigKey<?> realKey = e.getEntityType().getConfigKey(it.getName());
            if (realKey!=null) it = realKey;
            
            Maybe<Object> mv = ((EntityInternal)e).getConfigMap().getConfigRaw(it, false);
            if (!isTrivial(mv)) {
                Object v = mv.get();
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
                if (members.length()>0) members.append(", ");
                members.append(it.getId());
            }
            out.append(currentIndentation+tab+tab+"Members: "+members.toString()+"\n");
        }

        if (!e.getPolicies().isEmpty()) {
            out.append(currentIndentation+tab+tab+"Policies:\n");
            for (Policy policy : e.getPolicies()) {
                dumpInfo(policy, out, currentIndentation+tab+tab+tab, tab);
            }
        }

        if (!e.getEnrichers().isEmpty()) {
            out.append(currentIndentation+tab+tab+"Enrichers:\n");
            for (Enricher enricher : e.getEnrichers()) {
                dumpInfo(enricher, out, currentIndentation+tab+tab+tab, tab);
            }
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

        for (Object entryO : ((LocationInternal)loc).getAllConfigBag().getAllConfig().entrySet()) {
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

    public static void dumpInfo(Enricher enr) {
        try {
            dumpInfo(enr, new PrintWriter(System.out), "", "  ");
        } catch (IOException exc) {
            // system.out throwing an exception is odd, so don't have IOException on signature
            throw new RuntimeException(exc);
        }
    }
    public static void dumpInfo(Enricher enr, Writer out) throws IOException {
        dumpInfo(enr, out, "", "  ");
    }
    public static void dumpInfo(Enricher enr, String currentIndentation, String tab) throws IOException {
        dumpInfo(enr, new PrintWriter(System.out), currentIndentation, tab);
    }
    public static void dumpInfo(Enricher enr, Writer out, String currentIndentation, String tab) throws IOException {
        out.append(currentIndentation+enr.toString()+"\n");

        for (ConfigKey<?> key : sortConfigKeys(enr.getEnricherType().getConfigKeys())) {
            Maybe<Object> val = ((AbstractEnricher)enr).getConfigMap().getConfigRaw(key, true);
            if (!isTrivial(val)) {
                out.append(currentIndentation+tab+tab+key);
                out.append(" = ");
                if (isSecret(key.getName())) out.append("xxxxxxxx");
                else out.append(""+val.get());
                out.append("\n");
            }
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
            Maybe<Object> val = ((AbstractPolicy)pol).getConfigMap().getConfigRaw(key, true);
            if (!isTrivial(val)) {
                out.append(currentIndentation+tab+tab+key);
                out.append(" = ");
                if (isSecret(key.getName())) out.append("xxxxxxxx");
                else out.append(""+val.get());
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

    /** true if the given descendant includes the given ancestor in its chain.
     * does NOT count a node as its ancestor.
     */
    public static boolean isAncestor(Entity descendant, Entity potentialAncestor) {
        Entity ancestor = descendant.getParent();
        while (ancestor != null) {
            if (ancestor.equals(potentialAncestor)) return true;
            ancestor = ancestor.getParent();
        }
        return false;
    }
    
    /** checks whether the descendants of the given ancestor contains the given potentialDescendant.
     * in this test, unlike in {@link #descendants(Entity)}, an entity is not counted as a descendant.
     * note, it is usually preferred to use isAncestor() and swap the order, it is a cheaper method. */
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
    
    /** return all descendants of given entity matching the given predicate.
     * see {@link EntityPredicates} for useful second arguments! */
    public static Iterable<Entity> descendants(Entity root, Predicate<? super Entity> matching, boolean includeSelf) {
        Iterable<Entity> descs = Iterables.concat(Iterables.transform(root.getChildren(), new Function<Entity,Iterable<Entity>>() {
            @Override
            public Iterable<Entity> apply(Entity input) {
                return descendants(input);
            }
        }));
        return Iterables.filter(Iterables.concat(descs, Collections.singleton(root)), matching);
    }

    /** as {@link #descendants(Entity, Predicate)}, for common case of including self */ 
    public static Iterable<Entity> descendants(Entity root, Predicate<Entity> matching) {
        return descendants(root, matching, true);
    }

    /**
     * returns the entity, its children, and all its children, and so on. 
     * as {@link #descendants(Entity, Predicate)}, for common case of matching everything and including self. */ 
    public static Iterable<Entity> descendants(Entity root) {
        return descendants(root, Predicates.alwaysTrue(), true);
    }

    /** return all descendants of given entity of the given type, potentially including the given root.
     * as {@link #descendants(Entity, Predicate)}, for common case of {@link Predicates#instanceOf(Class)}, 
     * including self, and returning the correct generics signature. */
    public static <T extends Entity> Iterable<T> descendants(Entity root, Class<T> ofType) {
        return Iterables.filter(descendants(root), ofType);
    }
    
    /**
     * returns the entity, its parent, its parent, and so on. */ 
    public static Iterable<Entity> ancestors(final Entity root) {
        return new Iterable<Entity>() {
            @Override
            public Iterator<Entity> iterator() {
                return new Iterator<Entity>() {
                    Entity next = root;
                    @Override
                    public boolean hasNext() {
                        return next!=null;
                    }
                    @Override
                    public Entity next() {
                        Entity result = next;
                        next = next.getParent();
                        return result;
                    }
                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    /**
     * Registers a {@link BrooklynShutdownHooks#invokeStopOnShutdown(Entity)} to shutdown this entity when the JVM exits.
     * (Convenience method located in this class for easy access.)
     */
    public static void invokeStopOnShutdown(Entity entity) {
        BrooklynShutdownHooks.invokeStopOnShutdown(entity);
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

    /** destroys the given location -- does as many as are valid given the type and state
     * TODO: unmanage the location, if possible?
     */
    public static void destroy(Location loc) {
        if (loc instanceof Closeable) {
            Streams.closeQuietly((Closeable)loc);
            log.debug("closed "+loc);
        }
    }
    
    /** as {@link #destroy(Location)} but catching all errors */
    public static void destroyCatching(Location loc) {
        try {
            destroy(loc);
        } catch (Exception e) {
            log.warn("ERROR destroying "+loc+" (ignoring): "+e, e);
            Exceptions.propagateIfFatal(e);
        }
    }


    /** stops, destroys, and unmanages all apps in the given context,
     * and then terminates the management context */
    public static void destroyAll(ManagementContext mgmt) {
        Exception error = null;
        if (mgmt instanceof NonDeploymentManagementContext) {
            // log here because it is easy for tests to destroyAll(app.getMgmtContext())
            // which will *not* destroy the mgmt context if the app has been stopped!
            log.warn("Entities.destroyAll invoked on non-deployment "+mgmt+" - not likely to have much effect! " +
            		"(This usually means the mgmt context has been taken from entity has been destroyed. " +
            		"To destroy other things on the management context ensure you keep a handle to the context " +
            		"before the entity is destroyed, such as by creating the management context first.)");
        }
        if (!mgmt.isRunning()) return;
        log.debug("destroying all apps in "+mgmt+": "+mgmt.getApplications());
        for (Application app: mgmt.getApplications()) {
            log.debug("destroying app "+app+" (managed? "+isManaged(app)+"; mgmt is "+mgmt+")");
            try {
                destroy(app);
                log.debug("destroyed app "+app+"; mgmt now "+mgmt);
            } catch (Exception e) {
                log.warn("problems destroying app "+app+" (mgmt now "+mgmt+", will rethrow at least one exception): "+e);
                if (error==null) error = e;
            }
        }
        for (Location loc : mgmt.getLocationManager().getLocations()) {
            destroyCatching(loc);
        }
        if (mgmt instanceof ManagementContextInternal) 
            ((ManagementContextInternal)mgmt).terminate();
        if (error!=null) throw Exceptions.propagate(error);
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

    public static boolean isManaged(Entity e) {
        return ((EntityInternal)e).getManagementSupport().isDeployed() && ((EntityInternal)e).getManagementContext().isRunning();
    }

    public static boolean isNoLongerManaged(Entity e) {
        return ((EntityInternal)e).getManagementSupport().isNoLongerManaged();
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
        return newDownloader(driver, ImmutableMap.<String,Object>of());
    }

    public static DownloadResolver newDownloader(EntityDriver driver, Map<String,?> properties) {
        EntityInternal internal = (EntityInternal) driver.getEntity();
        return internal.getManagementContext().getEntityDownloadsManager().newDownloader(driver, properties);
    }

    public static DownloadResolver newDownloader(EntityDriver driver, String addon) {
        return newDownloader(driver, addon, ImmutableMap.<String,Object>of());
    }

    public static DownloadResolver newDownloader(EntityDriver driver, String addon, Map<String,?> properties) {
        EntityInternal internal = (EntityInternal) driver.getEntity();
        return internal.getManagementContext().getEntityDownloadsManager().newDownloader(driver, addon, properties);
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
     * @since 0.6.0 (added only for backwards compatibility, where locations are being created directly).
     * @deprecated in 0.6.0; use {@link LocationManager#createLocation(LocationSpec)} instead; or {@link Locations#manage(Location, ManagementContext)}
     */
    public static void manage(Location loc, ManagementContext managementContext) {
        Locations.manage(loc, managementContext);
    }
    
    /** fails-fast if value of the given key is null or unresolveable */
    public static String getRequiredUrlConfig(Entity entity, ConfigKey<String> urlKey) {
        String url = entity.getConfig(urlKey);
        Preconditions.checkNotNull(url, "Key %s on %s should not be null", urlKey, entity);
        if (!ResourceUtils.create(entity).doesUrlExist(url)) {
            throw new IllegalStateException(String.format("Key %s on %s contains unavailable URL %s", urlKey, entity, url));
        }
        return url;
    }
    
    /** as {@link #getRequiredUrlConfig(Entity, ConfigKey)} */
    public static String getRequiredUrlConfig(Entity entity, HasConfigKey<String> urlKey) {
        return getRequiredUrlConfig(entity, urlKey.getConfigKey());
    }
    
    /** fails-fast if value of the given URL is null or unresolveable */
    public static String checkRequiredUrl(Entity entity, String url) {
        Preconditions.checkNotNull(url, "url");
        if (!ResourceUtils.create(entity).doesUrlExist(url)) {
            throw new IllegalStateException(String.format("URL %s on %s is unavailable", url, entity));
        }
        return url;
    }

    /** submits a task factory to construct its task at the entity (in a precursor task) and then to submit it;
     * important if e.g. task construction relies on an entity being in scope (in tags, via {@link BrooklynTaskTags}) */
    public static <T extends TaskAdaptable<?>> T submit(final Entity entity, final TaskFactory<T> taskFactory) {
        // TODO it is messy to have to do this, but not sure there is a cleaner way :(
        final Semaphore s = new Semaphore(0);
        final AtomicReference<T> result = new AtomicReference<T>();
        final ExecutionContext executionContext = ((EntityInternal)entity).getManagementSupport().getExecutionContext();
        executionContext.execute(new Runnable() {
            // TODO could give this task a name, like "create task from factory"
            @Override
            public void run() {
                T t = taskFactory.newTask();
                result.set(t);
                s.release();
            }
        });
        try {
            s.acquire();
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
        executionContext.submit(result.get().asTask());
        return result.get();
    }

    /** submits a task to run at the entity 
     * @return the task passed in, for fluency */
    public static <T extends TaskAdaptable<?>> T submit(final Entity entity, final T task) {
        final ExecutionContext executionContext = ((EntityInternal)entity).getManagementSupport().getExecutionContext();
        executionContext.submit(task.asTask());
        return task;
    }

    /** logs a warning if an entity has a value for a config key */
    public static void warnOnIgnoringConfig(Entity entity, ConfigKey<?> key) {
        if (entity.getConfigRaw(key, true).isPresentAndNonNull())
            log.warn("Ignoring "+key+" set on "+entity+" ("+entity.getConfig(key)+")");
    }

    /** waits until {@link Startable#SERVICE_UP} returns true */
    public static void waitForServiceUp(final Entity entity, Duration timeout) {
        String description = "Waiting for SERVICE_UP on "+entity;
        Tasks.setBlockingDetails(description);
        if (!Repeater.create(description).limitTimeTo(timeout)
                .rethrowException().backoffTo(Duration.ONE_SECOND)
                .until(new Callable<Boolean>() {
                    public Boolean call() {
                        return entity.getAttribute(Startable.SERVICE_UP);
                    }})
                .run()) {
            throw new IllegalStateException("Timeout waiting for SERVICE_UP from "+entity);
        }
        Tasks.resetBlockingDetails();
        log.debug("Detected SERVICE_UP for software {}", entity);
    }
    public static void waitForServiceUp(final Entity entity, long duration, TimeUnit units) {
        waitForServiceUp(entity, Duration.of(duration, units));
    }
    public static void waitForServiceUp(final Entity entity) {
        Duration timeout = entity.getConfig(BrooklynConfigKeys.START_TIMEOUT);
        waitForServiceUp(entity, timeout);
    }

    /** convenience for creating and submitted a given shell command against the given mgmt context;
     * primarily intended for use in the groovy GUI console */
    @Beta
    public static ProcessTaskWrapper<Integer> shell(ManagementContext mgmt, String command) {
        ProcessTaskWrapper<Integer> t = SystemTasks.exec(command).newTask();
        mgmt.getExecutionManager().submit(t).getUnchecked();
        System.out.println(t.getStdout());
        System.err.println(t.getStderr());
        return t;
    }
    
}
