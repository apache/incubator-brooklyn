package brooklyn.util.flags;

import groovy.lang.Closure;
import groovy.time.TimeDuration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.ClosureEntityFactory;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.basic.ConfigurableEntityFactoryFromEntityFactory;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.util.JavaGroovyEquivalents;
import brooklyn.util.net.Cidr;
import brooklyn.util.net.Networking;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.TypeToken;

public class TypeCoercions {

    private static final Logger log = LoggerFactory.getLogger(TypeCoercions.class);
    
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
        return coerce(value, TypeToken.of(targetType));
    }

    /** see {@link #coerce(Object, Class)} */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> T coerce(Object value, TypeToken<T> targetTypeToken) {
        if (value==null) return null;
        // does not actually cast generified contents; that is left to the caller
        Class<? super T> targetType = targetTypeToken.getRawType();
        
        if (targetType.isInstance(value)) return (T) value;

        //deal with primitive->primitive casting
        if (isPrimitiveOrBoxer(targetType) && isPrimitiveOrBoxer(value.getClass())) {
            // Don't just rely on Java to do its normal casting later; if caller writes
            // long `l = coerce(new Integer(1), Long.class)` then letting java do its casting will fail,
            // because an Integer will not automatically be unboxed and cast to a long
            return castPrimitive(value, (Class<T>)targetType);
        }

        //deal with string->primitive
        if (value instanceof String && isPrimitiveOrBoxer(targetType)) {
            return stringToPrimitive((String)value, (Class<T>)targetType);
        }

        //deal with primitive->string
        if (isPrimitiveOrBoxer(value.getClass()) && targetType.equals(String.class)) {
            return (T) value.toString();
        }

        //look for value.asType where Type is castable to targetType
        String targetTypeSimpleName = getVerySimpleName(targetType);
        if (targetTypeSimpleName!=null && targetTypeSimpleName.length()>0) {
            for (Method m: value.getClass().getMethods()) {
                if (m.getName().startsWith("as") && m.getParameterTypes().length==0 &&
                        targetType.isAssignableFrom(m.getReturnType()) ) {
                    if (m.getName().equals("as"+getVerySimpleName(m.getReturnType()))) {
                        try {
                            return (T) m.invoke(value);
                        } catch (Exception e) {
                            throw new ClassCoercionException("Cannot coerce type "+value.getClass()+" to "+targetType.getCanonicalName()+" ("+value+"): "+m.getName()+" adapting failed, "+e);
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
                        throw new ClassCoercionException("Cannot coerce type "+value.getClass()+" to "+targetType.getCanonicalName()+" ("+value+"): "+m.getName()+" adapting failed, "+e);
                    }
                }
            }
        }
        
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
                throw new ClassCoercionException("Cannot coerce type "+value.getClass()+" to "+targetType.getCanonicalName()+" ("+value+"): unboxing failed, "+e);
            }
        }

        if (targetType.isEnum()) {
            try {
                return (T) Enum.valueOf((Class)targetType, ""+value);
            } catch (IllegalArgumentException e) {
                // some enums (eg Lifecycle) use a tostring which is different, not ideal...
                return (T) Enum.valueOf((Class)targetType, (""+value).toUpperCase());
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
        throw new ClassCoercionException("Cannot coerce type "+value.getClass()+" to "+targetType.getCanonicalName()+" ("+value+"): no adapter known");
    }

    /**
     * Sometimes need to explicitly cast primitives, rather than relying on Java casting.
     * For example, when using generics then type-erasure means it doesn't actually cast,
     * which causes tests to fail with 0 != 0.0
     */
    @SuppressWarnings("unchecked")
    public static <T> T castPrimitive(Object value, Class<T> targetType) {
        assert isPrimitiveOrBoxer(targetType) : "targetType="+targetType;
        assert isPrimitiveOrBoxer(value.getClass()) : "value="+targetType+"; valueType="+value.getClass();

        Class<?> sourceWrapType = Primitives.wrap(value.getClass());
        Class<?> targetWrapType = Primitives.wrap(targetType);
        
        // optimization, for when already correct type
        if (sourceWrapType == targetWrapType) {
            return (T) value;
        }
        
        // boolean can only be cast to itself
        if (targetWrapType == Boolean.class) {
            return (T) value;
        } else if (sourceWrapType == Boolean.class) {
            return (T) value;
        }
        
        // for whole-numbers (where casting to long won't lose anything)...
        long v = 0;
        boolean islong = true;
        if (sourceWrapType == Character.class) {
            v = (long) ((Character)value).charValue();
        } else if (sourceWrapType == Byte.class) {
            v = (long) ((Byte)value).byteValue();
        } else if (sourceWrapType == Short.class) {
            v = (long) ((Short)value).shortValue();
        } else if (sourceWrapType == Integer.class) {
            v = (long) ((Integer)value).intValue();
        } else if (sourceWrapType == Long.class) {
            v = ((Long)value).longValue();
        } else {
            islong = false;
        }
        if (islong) {
            if (targetWrapType == Character.class) return (T) Character.valueOf((char)v); 
            if (targetWrapType == Byte.class) return (T) Byte.valueOf((byte)v); 
            if (targetWrapType == Short.class) return (T) Short.valueOf((short)v); 
            if (targetWrapType == Integer.class) return (T) Integer.valueOf((int)v); 
            if (targetWrapType == Long.class) return (T) Long.valueOf((long)v); 
            if (targetWrapType == Float.class) return (T) Float.valueOf((float)v); 
            if (targetWrapType == Double.class) return (T) Double.valueOf((double)v);
            throw new IllegalStateException("Unexpected: sourceType="+sourceWrapType+"; targetType="+targetWrapType);
        }
        
        // for real-numbers (cast to double)...
        double d = 0;
        boolean isdouble = true;
        if (sourceWrapType == Float.class) {
            d = (double) ((Float)value).floatValue();
        } else if (sourceWrapType == Double.class) {
            d = (double) ((Double)value).doubleValue();
        } else {
            isdouble = false;
        }
        if (isdouble) {
            if (targetWrapType == Character.class) return (T) Character.valueOf((char)d); 
            if (targetWrapType == Byte.class) return (T) Byte.valueOf((byte)d); 
            if (targetWrapType == Short.class) return (T) Short.valueOf((short)d); 
            if (targetWrapType == Integer.class) return (T) Integer.valueOf((int)d); 
            if (targetWrapType == Long.class) return (T) Long.valueOf((long)d); 
            if (targetWrapType == Float.class) return (T) Float.valueOf((float)d); 
            if (targetWrapType == Double.class) return (T) Double.valueOf((double)d);
            throw new IllegalStateException("Unexpected: sourceType="+sourceWrapType+"; targetType="+targetWrapType);
        } else {
            throw new IllegalStateException("Unexpected: sourceType="+sourceWrapType+"; targetType="+targetWrapType);
        }
    }
    
    @SuppressWarnings("unchecked")
    public static boolean isPrimitiveOrBoxer(Class<?> type) {
        return Primitives.allPrimitiveTypes().contains(type) || Primitives.allWrapperTypes().contains(type);
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T stringToPrimitive(String value, Class<T> targetType) {
        assert Primitives.allPrimitiveTypes().contains(targetType) || Primitives.allWrapperTypes().contains(targetType) : "targetType="+targetType;

        // If char, then need to do explicit conversion
        if (targetType == Character.class || targetType == char.class) {
            if (value.length() == 1) {
                return (T) (Character) value.charAt(0);
            } else if (value.length() != 1) {
                throw new ClassCoercionException("Cannot coerce type String to "+targetType.getCanonicalName()+" ("+value+"): adapting failed");
            }
        }
        
        // Otherwise can use valueOf reflectively
        Class<?> wrappedType;
        if (Primitives.allPrimitiveTypes().contains(targetType)) {
            wrappedType = Primitives.wrap(targetType);
        } else {
            wrappedType = targetType;
        }
        
        try {
            return (T) wrappedType.getMethod("valueOf", String.class).invoke(null, value);
        } catch (Exception e) {
            ClassCoercionException tothrow = new ClassCoercionException("Cannot coerce type String to "+targetType.getCanonicalName()+" ("+value+"): adapting failed");
            tothrow.initCause(e);
            throw tothrow;
        }
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
        registerAdapter(CharSequence.class, String.class, new Function<CharSequence,String>() {
            @Override
            public String apply(CharSequence input) {
                return input.toString();
            }
        });
        registerAdapter(byte[].class, String.class, new Function<byte[],String>() {
            @Override
            public String apply(byte[] input) {
                return new String(input);
            }
        });
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
                return Networking.getInetAddressWithFixedName(input);
            }
        });
        registerAdapter(String.class, Cidr.class, new Function<String,Cidr>() {
            @Override
            public Cidr apply(String input) {
                return new Cidr(input);
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
        registerAdapter(Closure.class, ConfigurableEntityFactory.class, new Function<Closure,ConfigurableEntityFactory>() {
            @Override
            public ConfigurableEntityFactory apply(Closure input) {
                return new ClosureEntityFactory(input);
            }
        });
        registerAdapter(EntityFactory.class, ConfigurableEntityFactory.class, new Function<EntityFactory,ConfigurableEntityFactory>() {
            @Override
            public ConfigurableEntityFactory apply(EntityFactory input) {
                if (input instanceof ConfigurableEntityFactory) return (ConfigurableEntityFactory)input;
                return new ConfigurableEntityFactoryFromEntityFactory(input);
            }
        });
        registerAdapter(Closure.class, EntityFactory.class, new Function<Closure,EntityFactory>() {
            @Override
            public EntityFactory apply(Closure input) {
                return new ClosureEntityFactory(input);
            }
        });
        registerAdapter(Closure.class, Predicate.class, new Function<Closure,Predicate>() {
            @Override
            public Predicate<?> apply(final Closure closure) {
                return new Predicate<Object>() {
                    @Override public boolean apply(Object input) {
                        return (Boolean) closure.call(input);
                    }
                };
            }
        });
        registerAdapter(Closure.class, Function.class, new Function<Closure,Function>() {
            @Override
            public Function apply(final Closure closure) {
                return new Function() {
                    @Override public Object apply(Object input) {
                        return closure.call(input);
                    }
                };
            }
        });
        registerAdapter(Object.class, Duration.class, new Function<Object,Duration>() {
            @Override
            public Duration apply(final Object input) {
                return brooklyn.util.time.Duration.of(input);
            }
        });
        registerAdapter(Object.class, TimeDuration.class, new Function<Object,TimeDuration>() {
            @Override
            public TimeDuration apply(final Object input) {
                log.warn("deprecated automatic coercion of Object to TimeDuration (set breakpoint in TypeCoercions to inspect, convert to Duration)");
                return JavaGroovyEquivalents.toTimeDuration(input);
            }
        });
        registerAdapter(TimeDuration.class, Long.class, new Function<TimeDuration,Long>() {
            @Override
            public Long apply(final TimeDuration input) {
                log.warn("deprecated automatic coercion of TimeDuration to Long (set breakpoint in TypeCoercions to inspect, use Duration instead of Long!)");
                return input.toMilliseconds();
            }
        });
        registerAdapter(Integer.class, AtomicLong.class, new Function<Integer,AtomicLong>() {
            @Override public AtomicLong apply(final Integer input) {
                return new AtomicLong(input);
            }
        });
        registerAdapter(Long.class, AtomicLong.class, new Function<Long,AtomicLong>() {
            @Override public AtomicLong apply(final Long input) {
                return new AtomicLong(input);
            }
        });
        registerAdapter(String.class, AtomicLong.class, new Function<String,AtomicLong>() {
            @Override public AtomicLong apply(final String input) {
                return new AtomicLong(Long.parseLong(input));
            }
        });
        registerAdapter(Integer.class, AtomicInteger.class, new Function<Integer,AtomicInteger>() {
            @Override public AtomicInteger apply(final Integer input) {
                return new AtomicInteger(input);
            }
        });
        registerAdapter(String.class, AtomicInteger.class, new Function<String,AtomicInteger>() {
            @Override public AtomicInteger apply(final String input) {
                return new AtomicInteger(Integer.parseInt(input));
            }
        });
        /** This actually produce a {@link Double} for any input, cast to a {@link Number}. */
        registerAdapter(String.class, Number.class, new Function<String,Number>() {
            @Override
            public Number apply(String input) {
                return Double.valueOf(input);
            }
        });
    }
}
