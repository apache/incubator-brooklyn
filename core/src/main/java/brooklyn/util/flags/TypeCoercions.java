package brooklyn.util.flags;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;

public class TypeCoercions {

    private TypeCoercions() {}

    private static Map<Class,Map<Class,Function>> registeredAdapters = Collections.synchronizedMap(
            new LinkedHashMap<Class,Map<Class,Function>>());
    
    /** attempts to coerce 'value' to 'targetType', 
     * using a variety of strategies,
     * including looking at:
     * 
     * value.asTargetType()
     * static TargetType.fromType(value) where value instanceof Type
     * 
     * value.targetTypeValue()  //handy for primitives
     * 
     * registeredAdapters.get(targetType).findFirst({ k,v -> k.isInstance(value) }, { k,v -> v.apply(value) })
     **/
    public static <T> T coerce(Object value, Class<T> targetType) {
        if (value==null) return null;
        if (targetType.isInstance(value)) return (T) value;

        //first look for value.asType where Type is castable to targetType
        String targetTypeSimpleName = getVerySimpleName(targetType);
        if (targetTypeSimpleName!=null && targetTypeSimpleName.length()>0) {
            for (Method m: value.getClass().getMethods()) {
                if (m.getName().startsWith("as") && m.getParameterTypes().length==0 &&
                        targetType.isAssignableFrom(m.getReturnType()) ) {
                    if (m.getName().equals("as"+getVerySimpleName(m.getReturnType()))) {
                        try {
                            return (T) m.invoke(value);
                        } catch (Exception e) {
                            throw new ClassCastException("Cannot coerce type "+value.getClass()+" to "+targetType.getCanonicalName()+" ("+value+"): "+m.getName()+" adapting failed, "+e);
                        }
                    }
                }
            }
        }
        
        //now look for static TargetType.fromType(Type t) where value instanceof Type  
        for (Method m: targetType.getMethods()) {
            if (((m.getModifiers()&Modifier.STATIC)==Modifier.STATIC) && 
                    m.getName().startsWith("from") && m.getParameterTypes().length==1 &&
                    m.getParameterTypes()[0].isInstance(value)) {
                if (m.getName().equals("from"+getVerySimpleName(m.getParameterTypes()[0]))) {
                    try {
                        return (T) m.invoke(null, value);
                    } catch (Exception e) {
                        throw new ClassCastException("Cannot coerce type "+value.getClass()+" to "+targetType.getCanonicalName()+" ("+value+"): "+m.getName()+" adapting failed, "+e);
                    }
                }
            }
        }
        
        //now look whether there is a TypeCoercionsSource declared on the target type
        
        //ENHANCEMENT could look in type hierarchy of both types for a conversion method...
        
        //primitives get run through again boxed up
        Class boxedT = UNBOXED_TO_BOXED_TYPES.get(targetType);
        Class boxedVT = UNBOXED_TO_BOXED_TYPES.get(value.getClass());
        if (boxedT!=null || boxedVT!=null) {
            try {
                if (boxedT==null) boxedT=targetType;
                Object boxedV;
                if (boxedVT==null) { boxedV = value; }
                else { boxedV = boxedVT.getConstructor(value.getClass()).newInstance(value); }
                return (T) coerce(boxedV, boxedT);
            } catch (Exception e) {
                throw new ClassCastException("Cannot coerce type "+value.getClass()+" to "+targetType.getCanonicalName()+" ("+value+"): unboxing failed, "+e);
            }
        }

        //now look in registry - TODO use registry first?
        Map<?,?> adaptersToTarget = registeredAdapters.get(targetType);
        if (adaptersToTarget!=null) {
            for (Map.Entry e: adaptersToTarget.entrySet()) {
                if ( ((Class)e.getKey()).isInstance(value) ) {
                    return (T) ((Function)e.getValue()).apply(value);
                }
            }
        }
        
        //not found
        throw new ClassCastException("Cannot coerce type "+value.getClass()+" to "+targetType.getCanonicalName()+" ("+value+"): no adapter known");
    }
    
    /** returns the simple class name, and for any inner class the portion after the $ */
    public static String getVerySimpleName(Class c) {
        String s = c.getSimpleName();
        if (s.indexOf('$')>=0)
            s = s.substring(s.lastIndexOf('$')+1);
        return s;
    }
    public static final Map<Class,Class> BOXED_TO_UNBOXED_TYPES = ImmutableMap.<Class,Class>builder().
            put(Integer.class, Integer.TYPE).
            put(Long.class, Long.TYPE).
            put(Boolean.class, Boolean.TYPE).
            put(Byte.class, Byte.TYPE).
            put(Double.class, Double.TYPE).
            put(Float.class, Float.TYPE).
            put(Character.class, Character.TYPE).
            put(Short.class, Short.TYPE).
            build();
    public static final Map<Class,Class> UNBOXED_TO_BOXED_TYPES = ImmutableMap.<Class,Class>builder().
            put(Integer.TYPE, Integer.class).
            put(Long.TYPE, Long.class).
            put(Boolean.TYPE, Boolean.class).
            put(Byte.TYPE, Byte.class).
            put(Double.TYPE, Double.class).
            put(Float.TYPE, Float.class).
            put(Character.TYPE, Character.class).
            put(Short.TYPE, Short.class).
            build();
    
    /** for automatic conversion */
    public static Object getMatchingConstructor(Class target, Object ...arguments) {
        Constructor[] cc = target.getConstructors();
        for (Constructor c: cc) {
            if (c.getParameterTypes().length != arguments.length)
                continue;
            boolean matches = true;
            Class[] tt = c.getParameterTypes();
            for (int i=0; i<tt.length; i++) {
                if (arguments[i]!=null && !tt[i].isInstance(arguments[i])) {
                    matches=false;
                    break;
                }
            }
            if (matches) 
                return c;
        }
        return null;
    }
    
    public synchronized static <A,B> void registerAdapter(Class<A> sourceType, Class<B> targetType, Function<A,B> fn) {
        Map<Class, Function> sources = registeredAdapters.get(targetType);
        if (sources==null) {
            sources = Collections.synchronizedMap(new LinkedHashMap<Class, Function>());
            registeredAdapters.put(targetType, sources);
        }
        sources.put(sourceType, fn);
    }
    
    static {
        registerAdapter(Collection.class, Set.class, new Function<Collection,Set>() {
            @Override
            public Set apply(Collection input) {
                return new LinkedHashSet(input);
            }
        });
        registerAdapter(Collection.class, List.class, new Function<Collection,List>() {
            @Override
            public List apply(Collection input) {
                return new ArrayList(input);
            }
        });
        registerAdapter(String.class, InetAddress.class, new Function<String,InetAddress>() {
            @Override
            public InetAddress apply(String input) {
                try {
                    return Inet4Address.getByName(input);
                } catch (UnknownHostException e) {
                    throw Throwables.propagate(e);
                }
            }
        });
        registerAdapter(String.class, URL.class, new Function<String,URL>() {
            @Override
            public URL apply(String input) {
                try {
                    return new URL(input);
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }
        });
    }
    
}
