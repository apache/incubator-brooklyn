package brooklyn.management.internal;

import static brooklyn.util.GroovyJavaMethods.truth;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BasicParameterType;
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Utility methods for invoking effectors.
 */
public class EffectorUtils {

    private static final Logger log = LoggerFactory.getLogger(EffectorUtils.class);

    /** prepares arguments for an effector either accepting:
     *  an array, which should contain the arguments in order, optionally omitting those which have defaults defined;
     *  or a map, which should contain the arguments by name, again optionally omitting those which have defaults defined,
     *  and in this case also performing type coercion.
     */
    public static Object[] prepareArgsForEffector(Effector<?> eff, Object args) {
        if (args!=null && args.getClass().isArray()) {
            return prepareArgsForEffectorFromArray(eff, (Object[]) args);
        }
        if (args instanceof Map) return prepareArgsForEffectorFromMap(eff, (Map)args);
        log.warn("Deprecated effector invocation style for call to "+eff+", expecting a map or an array, got: "+args);
        if (log.isDebugEnabled()) log.debug("Deprecated effector invocation style for call to "+eff+", expecting a map or an array, got: "+args, 
                new Throwable("Trace for deprecated effector invocation style"));
        return oldPrepareArgsForEffector(eff, args);
    }
    
    /** method used for calls such as   entity.effector(arg1, arg2)
     * get routed here from AbstractEntity.invokeMethod */
    private static Object[] prepareArgsForEffectorFromArray(Effector<?> eff, Object args[]) {
        int newArgsNeeded = eff.getParameters().size();
        if (args.length==1 && args[0] instanceof Map)
            if (newArgsNeeded!=1 || !eff.getParameters().get(0).getParameterClass().isAssignableFrom(args[0].getClass()))
                // treat a map in an array as a map passed directly (unless the method takes a single-arg map)
                // this is to support   effector(param1: val1)
                return prepareArgsForEffectorFromMap(eff, (Map)args[0]);
        
        return prepareArgsForEffectorAsMapFromArray(eff, args).values().toArray(new Object[0]);
    }
    
    public static Map prepareArgsForEffectorAsMapFromArray(Effector<?> eff, Object args[]) {
        int newArgsNeeded = eff.getParameters().size();
        List l = Lists.newArrayList();
        l.addAll(Arrays.asList(args));
        Map newArgs = new LinkedHashMap();

        for (int index = 0; index < eff.getParameters().size(); index++) {
            ParameterType<?> it = eff.getParameters().get(index);
            
            if (l.size() >= newArgsNeeded)
                //all supplied (unnamed) arguments must be used; ignore map
                newArgs.put(it.getName(), l.remove(0));
            // TODO do we ignore arguments in the same order that groovy does?
            else if (!l.isEmpty() && it.getParameterClass().isInstance(l.get(0))) {
                //if there are parameters supplied, and type is correct, they get applied before default values
                //(this is akin to groovy)
                newArgs.put(it.getName(), l.remove(0));
            } else if (it instanceof BasicParameterType && ((BasicParameterType)it).hasDefaultValue()) {
                //finally, default values are used to make up for missing parameters
                newArgs.put(it.getName(), ((BasicParameterType)it).getDefaultValue());
            } else {
                throw new IllegalArgumentException("Invalid arguments (count mismatch) for effector "+eff+": "+args);
            }

            newArgsNeeded--;
        }
        if (newArgsNeeded>0)
            throw new IllegalArgumentException("Invalid arguments (missing "+newArgsNeeded+") for effector "+eff+": "+args);
        if (!l.isEmpty())
            throw new IllegalArgumentException("Invalid arguments ("+l.size()+" extra) for effector "+eff+": "+args);
        return newArgs;    
    }

    private static Object[] prepareArgsForEffectorFromMap(Effector<?> eff, Map m) {
        m = Maps.newLinkedHashMap(m); //make editable copy
        List newArgs = Lists.newArrayList();
        int newArgsNeeded = eff.getParameters().size();
        boolean mapUsed = false;

        for (int index = 0; index < eff.getParameters().size(); index++) {
            ParameterType<?> it = eff.getParameters().get(index);
            Object v;
            if (truth(it.getName()) && m.containsKey(it.getName())) {
                // argument is in the map
                v = m.remove(it.getName());
            } else if (it instanceof BasicParameterType && ((BasicParameterType)it).hasDefaultValue()) {
                //finally, default values are used to make up for missing parameters
                v = ((BasicParameterType)it).getDefaultValue();
            } else {
                throw new IllegalArgumentException("Invalid arguments (missing argument "+it+") for effector "+eff+": "+m);
            }

            newArgs.add( TypeCoercions.coerce(v, it.getParameterClass()) );
            newArgsNeeded--;
        }
        if (newArgsNeeded>0)
            throw new IllegalArgumentException("Invalid arguments (missing "+newArgsNeeded+") for effector "+eff+": "+m);
        return newArgs.toArray(new Object[newArgs.size()]);    
    }

    /** 
     * Takes arguments, and returns an array of arguments suitable for use by the Effector
     * according to the ParameterTypes it exposes.
     * 
     * The args can be:
     *  1. an array of ordered arguments
     *  2. a collection (which will be automatically converted to an array)
     *  3. a single argument (which will then be wrapped in an array)
     *  4. a map containing the (named) arguments
     *  5. an array or collection single entry of a map (treated same as 5 above) 
     *  6. a semi-populated array or collection that also containing a map as first arg -
     *     uses ordered args in array, but uses named values from map in preference.
     *  7. semi-populated array or collection, where default values will otherwise be used.
     *   
     */
    public static Object[] oldPrepareArgsForEffector(Effector<?> eff, Object args) {
        //attempt to coerce unexpected types
        Object[] argsArray;
        if (args==null) {
            argsArray = new Object[0];
        } else if (args.getClass().isArray()) {
            argsArray = (Object[]) args;
        } else {
            if (args instanceof Collection) {
                argsArray = ((Collection)args).toArray(new Object[((Collection)args).size()]);
            } else {
                argsArray = new Object[] {args};
            }
        }

        //if args starts with a map, assume it contains the named arguments
        //(but only use it when we have insufficient supplied arguments)
        List l = Lists.newArrayList();
        l.addAll(Arrays.asList(argsArray));
        Map m = (argsArray.length > 0 && argsArray[0] instanceof Map ? Maps.newLinkedHashMap((Map)l.remove(0)) : null);
        List newArgs = Lists.newArrayList();
        int newArgsNeeded = eff.getParameters().size();
        boolean mapUsed = false;

        for (int index = 0; index < eff.getParameters().size(); index++) {
            ParameterType<?> it = eff.getParameters().get(index);
            
            if (l.size() >= newArgsNeeded)
                //all supplied (unnamed) arguments must be used; ignore map
                newArgs.add(l.remove(0));
            else if (truth(m) && truth(it.getName()) && m.containsKey(it.getName()))
                //some arguments were not supplied, and this one is in the map
                newArgs.add(m.remove(it.getName()));
            else if (index==0 && Map.class.isAssignableFrom(it.getParameterClass())) {
                //if first arg is a map it takes the supplied map
                newArgs.add(m);
                mapUsed = true;
            } else if (!l.isEmpty() && it.getParameterClass().isInstance(l.get(0)))
                //if there are parameters supplied, and type is correct, they get applied before default values
                //(this is akin to groovy)
                newArgs.add(l.remove(0));
            else if (it instanceof BasicParameterType && ((BasicParameterType)it).hasDefaultValue())
                //finally, default values are used to make up for missing parameters
                newArgs.add(((BasicParameterType)it).getDefaultValue());
            else
                throw new IllegalArgumentException("Invalid arguments (count mismatch) for effector "+eff+": "+args);

            newArgsNeeded--;
        }
        if (newArgsNeeded>0)
            throw new IllegalArgumentException("Invalid arguments (missing "+newArgsNeeded+") for effector "+eff+": "+args);
        if (!l.isEmpty())
            throw new IllegalArgumentException("Invalid arguments ("+l.size()+" extra) for effector "+eff+": "+args);
        if (truth(m) && !mapUsed)
            throw new IllegalArgumentException("Invalid arguments ("+m.size()+" extra named) for effector "+eff+": "+args);
        return newArgs.toArray(new Object[newArgs.size()]);
    }

    /**
     * Invokes the effector so that its progress is tracked.
     */
    public static <T> T invokeEffector(Entity entity, Effector<T> eff, Object[] args) {
        String id = entity.getId();
        String name = eff.getName();
        
        try {
            if (log.isDebugEnabled()) log.debug("Invoking effector {} on {}", new Object[] {name, entity});
            if (log.isTraceEnabled()) log.trace("Invoking effector {} on {} with args {}", new Object[] {name, entity, args});
            EntityManagementSupport mgmtSupport = ((EntityInternal)entity).getManagementSupport();
            if (!mgmtSupport.isDeployed()) {
                mgmtSupport.attemptLegacyAutodeployment(name);
            }
            ManagementContextInternal mgmtContext = (ManagementContextInternal) ((EntityInternal)entity).getManagementContext();
            
            mgmtSupport.getEntityChangeListener().onEffectorStarting(eff);
            try {
                return mgmtContext.invokeEffectorMethodSync(entity, eff, args);
            } finally {
                mgmtSupport.getEntityChangeListener().onEffectorCompleted(eff);
            }
        } catch (CancellationException ce) {
            log.info("Execution of effector {} on entity {} was cancelled", name, id);
            throw ce;
        } catch (ExecutionException ee) {
            log.info("Execution of effector {} on entity {} failed with {}", new Object[] {name, id, ee});
            // Exceptions thrown in Futures are wrapped
            // FIXME Shouldn't pretend exception came from this thread?! Should we remove this unwrapping?
            if (ee.getCause() != null) throw Exceptions.propagate(ee.getCause());
            else throw Exceptions.propagate(ee);
        }
    }

    /**
     * Invokes the effector so that its progress is tracked.
     * 
     * If the given method is not defined as an effector, then a warning will be logged and the
     * method will be invoked directly.
     */
    public static Object invokeEffector(AbstractEntity entity, Method method, Object[] args) {
        Effector<?> effector = findEffectorMatching(entity, method);
        if (effector == null) {
            log.warn("No matching effector found for method {} on entity {}; invoking directly", method, entity);
            try {
                return method.invoke(entity, args);
            } catch (Exception e) {
                log.info("Execution of method {} on entity {} failed with {} (rethrowing)", new Object[] {method, entity.getId(), e});
                throw Exceptions.propagate(e);
            }
        } else {
            return invokeEffector(entity, effector, args);
        }
    }
 
    public static <T> Task<T> invokeEffectorAsync(Entity entity, Effector<T> eff, Map<String,?> parameters) {
        String id = entity.getId();
        String name = eff.getName();
        
        if (log.isDebugEnabled()) log.debug("Invoking-async effector {} on {}", new Object[] {name, entity});
        if (log.isTraceEnabled()) log.trace("Invoking-async effector {} on {} with args {}", new Object[] {name, entity, parameters});
        EntityManagementSupport mgmtSupport = ((EntityInternal)entity).getManagementSupport();
        if (!mgmtSupport.isDeployed()) {
            mgmtSupport.attemptLegacyAutodeployment(name);
        }
        ManagementContextInternal mgmtContext = (ManagementContextInternal) ((EntityInternal)entity).getManagementContext();
        
        // FIXME seems brittle to have the listeners in the Utils method; better to move into the context.invokeEff
        // (or whatever the last mile before invoking the effector is)
        mgmtSupport.getEntityChangeListener().onEffectorStarting(eff);
        try {
            return mgmtContext.invokeEffector(entity, eff, parameters);
        } finally {
            // FIXME this is really Effector submitted
            mgmtSupport.getEntityChangeListener().onEffectorCompleted(eff);
        }
    }

    public static Effector<?> findEffectorMatching(Entity entity, Method method) {
        effector: for (Effector<?> effector : entity.getEntityType().getEffectors()) {
            if (!effector.getName().equals(entity)) continue;
            if (effector.getParameters().size() != method.getParameterTypes().length) continue;
            for (int i = 0; i < effector.getParameters().size(); i++) {
                if (effector.getParameters().get(i).getParameterClass() != method.getParameterTypes()[i]) continue effector; 
            }
            return effector;
        }
        return null;
    }

    public static Effector<?> findEffectorMatching(Set<Effector<?>> effectors, String effectorName, Map<String, ?> parameters) {
        // TODO Support overloading: check parameters as well
        for (Effector<?> effector : effectors) {
            if (effector.getName().equals(effectorName)) {
                return effector;
            }
        }
        return null;
    }
    
    /** returns a (mutable) map of the standard flags which should be placed on an effector */
    public static Map<Object,Object> getTaskFlagsForEffectorInvocation(Entity entity, Effector<?> effector) {
        return MutableMap.builder()
                .put("description", "Invoking effector "+effector.getName()+" on "+entity.getDisplayName())
                .put("displayName", effector.getName())
                .put("tags", MutableList.of(ManagementContextInternal.EFFECTOR_TAG, 
                        BrooklynTasks.tagForTargetEntity(entity)))
                .build();
    }

}