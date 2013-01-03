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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.policy.Policy;
import brooklyn.util.MutableMap;
import brooklyn.util.ResourceUtils;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.task.ParallelTask;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;


/** Convenience methods for working with entities. 
 * Also see the various *Methods classes for traits 
 * (eg StartableMethods for Startable implementations). */
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
    
	/** invokes the given effector with the given named arguments on the entitiesToCall, from the calling context of the callingEntity;
	 * intended for use only from the callingEntity.
	 * @return ParallelTask containing a results from each invocation; calling get() on the result will block until all complete,
	 * and throw error if any threw error   
	 */
	public static <T> Task<List<T>> invokeEffectorList(EntityLocal callingEntity, Iterable<? extends Entity> entitiesToCall, 
	        final Effector<T> effector, final Map<String,?> parameters) {
        // formulation is complicated, but it is building up a list of tasks, without blocking on them initially,
        // but ensuring that when the parallel task is gotten it does block on all of them
	    // TODO why not just get list of tasks with `entity.invoke(effector, parameters))`?
	    //      What is advantage of invoking in callingEntity's context?
        
		if (entitiesToCall == null || Iterables.isEmpty(entitiesToCall)) return null;
		List<Callable<T>> tasks = Lists.newArrayList();
		
		for (final Entity entity : entitiesToCall) {
		    tasks.add(new Callable<T>() {
		        public T call() throws Exception {
		            return entity.invoke(effector, parameters).get();
		        }});
		}
	    ParallelTask<T> invoke = new ParallelTask<T>(tasks);
	    callingEntity.getManagementSupport().getExecutionContext().submit(invoke);
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
    public static <T> Task<List<T>> invokeEffectorWithMap(EntityLocal callingEntity, Entity entityToCall, 
            final Effector<T> effector, final Map<String,?> parameters) {
        return invokeEffectorList(callingEntity, ImmutableList.of(entityToCall), effector, parameters);
    }
    public static <T> Task<List<T>> invokeEffectorWithArgs(EntityLocal callingEntity, Entity entityToCall, 
            final Effector<T> effector, Object ...args) {
        return invokeEffectorListWithArgs(callingEntity, ImmutableList.of(entityToCall), effector, args); 
    }
    public static <T> Task<List<T>> invokeEffector(EntityLocal callingEntity, Entity entityToCall, 
            final Effector<T> effector) {
        return invokeEffectorWithMap(callingEntity, entityToCall, effector, Collections.<String,Object>emptyMap());
    }

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
            Object v = e.getConfig(it);
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
		
        for (Policy policy : e.getPolicies()) {
            out.append(currentIndentation+tab+tab+"Policy: ");
            out.append(policy.getId()+"; "+policy.getClass()+"; "+policy.getName()+"; ");
            out.append(policy.isRunning() ? "running" : (policy.isDestroyed() ? "destroyed" : (policy.isSuspended() ? "suspended" : "state-unknown")));
            out.append("\n");
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
    public static void dumpInfo(Location loc, Writer out, String currentIndentation, String tab) throws IOException {
        out.append(currentIndentation+loc.toString()+"\n");
        
        for (Map.Entry<String,?> entry : sortMap(loc.getLocationProperties()).entrySet()) {
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
        
        for (Location it : loc.getChildLocations()) {
            dumpInfo(it, out, currentIndentation+tab, tab);
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

    /** @deprecated use start(Entity) */
    public static Entity start(ManagementContext context, Entity e, Collection<? extends Location> locations) {
        if (context != null) context.manage(e);
        if (e instanceof Startable) ((Startable)e).start(locations);
        return e;
    }

    /** @deprecated use destroy(Entity) */
    public static void destroy(ManagementContext context, Entity e) {
        if (e instanceof Startable) ((Startable)e).stop();
        if (e instanceof AbstractEntity) ((AbstractEntity)e).destroy();
        if (context != null) context.unmanage(e);
    }

    /** convenience for starting an entity, esp a new Startable instance which has been created dynamically
     * (after the application is started) */
    public static void start(Entity e, Collection<Location> locations) {
        if (!isManaged(e) && !manage(e)) {
            log.warn("Using discouraged mechanism to start management -- Entities.start(Application, Locations) -- caller should create and use the preferred management context");
            startManagement(e);
        }
        if (e instanceof Startable) Entities.invokeEffectorWithMap((EntityLocal)e, e, Startable.START,
                MutableMap.of("locations", locations)).getUnchecked();
    }

    /** stops, destroys, and unmanages the given entity -- does as many as are valid given the type and state */
    public static void destroy(Entity e) {
        if (isManaged(e)) {
            if (e instanceof Startable) Entities.invokeEffector((EntityLocal)e, e, Startable.STOP).getUnchecked();
            if (e instanceof AbstractEntity) ((AbstractEntity)e).destroy();
            unmanage(e);
        }
    }

    public static boolean isManaged(Entity e) {
        return ((AbstractEntity)e).getManagementSupport().isDeployed() && ((AbstractEntity)e).getManagementSupport().getManagementContext(true).isRunning();
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
            ((AbstractEntity)o).getManagementSupport().getManagementContext(false).manage(eum);
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
            ManagementContext mgmt = ((AbstractEntity)o).getManagementSupport().getManagementContext(false);
            mgmt.manage(eum);
            return mgmt;
        }
        if (!(o instanceof Application))
            throw new IllegalStateException("Can't manage "+e+" because it is not rooted at an application");
        ManagementContext mgmt = new LocalManagementContext();
        mgmt.manage(o);
        return mgmt;
    }

    /**
     * Starts managing the given (unmanaged) app, setting the given brooklyn properties on the new
     * management context.
     * 
     * @see startManagement(Entity)
     */
    public static ManagementContext startManagement(Application app, BrooklynProperties props) {
        if (isManaged(app)) {
            throw new IllegalStateException("Application "+app+" is already managed, so can't set brooklyn properties");
        }
        ManagementContext mgmt = new LocalManagementContext(props);
        mgmt.manage(app);
        return mgmt;
    }
    
    public static void unmanage(Entity entity) {
        if (((AbstractEntity)entity).getManagementSupport().isDeployed()) {
            ((AbstractEntity)entity).getManagementSupport().getManagementContext(true).unmanage(entity);
        }
    }

}
