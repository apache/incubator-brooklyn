package brooklyn.management.internal;

import static brooklyn.util.GroovyJavaMethods.truth;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.EntityType;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.BasicParameterType;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * The abstract {@link Effector} implementation.
 * 
 * The concrete subclass (often anonymous) will supply the {@link #call(EntityType, Map)} implementation,
 * and the fields in the constructor.
 */
public class EffectorUtils<T> {

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

    public static Effector<?> findEffectorMatching(Set<Effector<?>> effectors, String effectorName, Map<String, ?> parameters) {
        // TODO Support overloading: check parameters as well
        for (Effector<?> effector : effectors) {
            if (effector.getName().equals(effectorName)) {
                return effector;
            }
        }
        return null;
    }
}
