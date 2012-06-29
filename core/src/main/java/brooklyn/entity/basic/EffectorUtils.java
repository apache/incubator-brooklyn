package brooklyn.entity.basic;

import static brooklyn.util.GroovyJavaMethods.truth;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import brooklyn.entity.Effector;
import brooklyn.entity.ParameterType;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * The abstract {@link Effector} implementation.
 * 
 * The concrete subclass (often anonymous) will supply the {@link #call(EntityType, Map)} implementation,
 * and the fields in the constructor.
 */
public class EffectorUtils<T> {

    // FIXME The versatility of the prepareArgsForEffector is asking for trouble.
    // It's way to complicated and will hide programming errors.
    // We should definitely lock down what is being passed in and make it strongly typed.
    
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
    public static Object[] prepareArgsForEffector(Effector<?> eff, Object args) {
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
}
