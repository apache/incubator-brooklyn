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
package brooklyn.util.flags;

import groovy.lang.Closure;
import groovy.time.TimeDuration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.ClosureEntityFactory;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.basic.ConfigurableEntityFactoryFromEntityFactory;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.util.JavaGroovyEquivalents;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.javalang.Enums;
import brooklyn.util.net.Cidr;
import brooklyn.util.net.Networking;
import brooklyn.util.net.UserAndHostAndPort;
import brooklyn.util.text.StringEscapes.JavaStringEscapes;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.yaml.Yamls;

import com.google.common.base.CaseFormat;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.Primitives;
import com.google.common.reflect.TypeToken;

@SuppressWarnings("rawtypes")
public class TypeCoercions {

    private static final Logger log = LoggerFactory.getLogger(TypeCoercions.class);
    
    private TypeCoercions() {}

    /** Store the coercion {@link Function functions} in a {@link Table table}. */
    @GuardedBy("TypeCoercions.class")
    private static Table<Class, Class, Function> registry = HashBasedTable.create();

    /**
     * Attempts to coerce {@code value} to {@code targetType}.
     * <p>
     * Maintains a registry of adapter functions for type pairs in a {@link Table} which
     * is searched after checking various strategies, including the following:
     * <ul>
     * <li>{@code value.asTargetType()}
     * <li>{@code TargetType.fromType(value)} (if {@code value instanceof Type})
     * <li>{@code value.targetTypeValue()} (handy for primitives)
     * <li>{@code TargetType.valueOf(value)} (for enums)
     * </ul>
     *
     * @see #coerce(Object, TypeToken)
     */
    public static <T> T coerce(Object value, Class<T> targetType) {
        return coerce(value, TypeToken.of(targetType));
    }

    public static <T> Maybe<T> tryCoerce(Object value, TypeToken<T> targetTypeToken) {
        try {
            return Maybe.of( coerce(value, targetTypeToken) );
        } catch (Throwable t) {
            Exceptions.propagateIfFatal(t);
            return Maybe.absent(t); 
        }
    }
    
    /** @see #coerce(Object, Class) */
    @SuppressWarnings({ "unchecked" })
    public static <T> T coerce(Object value, TypeToken<T> targetTypeToken) {
        if (value==null) return null;
        // does not actually cast generified contents; that is left to the caller
        Class<? super T> targetType = targetTypeToken.getRawType();

        if (targetType.isInstance(value)) return (T) value;

        // TODO use registry first?

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

        //for enums call valueOf with the string representation of the value
        if (targetType.isEnum()) {
            T result = (T) stringToEnum((Class<Enum>) targetType, null).apply(String.valueOf(value));
            if (result != null) return result;
        }

        //now look in registry
        synchronized (TypeCoercions.class) {
            Map<Class, Function> adapters = registry.row(targetType);
            for (Map.Entry<Class, Function> entry : adapters.entrySet()) {
                if (entry.getKey().isInstance(value)) {
                    return (T) entry.getValue().apply(value);
                }
            }
        }

        //not found
        throw new ClassCoercionException("Cannot coerce type "+value.getClass()+" to "+targetType.getCanonicalName()+" ("+value+"): no adapter known");
    }

    /**
     * Type coercion {@link Function function} for {@link Enum enums}.
     * <p>
     * Tries to convert the string to {@link CaseFormat#UPPER_UNDERSCORE} first,
     * handling all of the different {@link CaseFormat format} possibilites. Failing 
     * that, it tries a case-insensitive comparison with the valid enum values.
     * <p>
     * Returns {@code defaultValue} if the string cannot be converted.
     *
     * @see TypeCoercions#coerce(Object, Class)
     * @see Enum#valueOf(Class, String)
     */
    public static <E extends Enum<E>> Function<String, E> stringToEnum(final Class<E> type, @Nullable final E defaultValue) {
        return new Function<String, E>() {
            @Override
            public E apply(String input) {
                Preconditions.checkNotNull(input, "input");
                List<String> options = ImmutableList.of(
                        input,
                        CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, input),
                        CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_UNDERSCORE, input),
                        CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, input),
                        CaseFormat.UPPER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE, input));
                for (String value : options) {
                    try {
                        return Enum.valueOf(type, value);
                    } catch (IllegalArgumentException iae) {
                        continue;
                    }
                }
                Maybe<E> result = Enums.valueOfIgnoreCase(type, input);
                return (result.isPresent()) ? result.get() : defaultValue;
            }
        };
    }

    /**
     * Sometimes need to explicitly cast primitives, rather than relying on Java casting.
     * For example, when using generics then type-erasure means it doesn't actually cast,
     * which causes tests to fail with 0 != 0.0
     */
    @SuppressWarnings("unchecked")
    public static <T> T castPrimitive(Object value, Class<T> targetType) {
        if (value==null) return null;
        assert isPrimitiveOrBoxer(targetType) : "targetType="+targetType;
        assert isPrimitiveOrBoxer(value.getClass()) : "value="+targetType+"; valueType="+value.getClass();

        Class<?> sourceWrapType = Primitives.wrap(value.getClass());
        Class<?> targetWrapType = Primitives.wrap(targetType);
        
        // optimization, for when already correct type
        if (sourceWrapType == targetWrapType) {
            return (T) value;
        }
        
        if (targetWrapType == Boolean.class) {
            // only char can be mapped to boolean
            // (we could say 0=false, nonzero=true, but there is no compelling use case so better
            // to encourage users to write as boolean)
            if (sourceWrapType == Character.class)
                return (T) stringToPrimitive(value.toString(), targetType);
            
            throw new ClassCoercionException("Cannot cast "+sourceWrapType+" ("+value+") to "+targetType);
        } else if (sourceWrapType == Boolean.class) {
            // boolean can't cast to anything else
            
            throw new ClassCoercionException("Cannot cast "+sourceWrapType+" ("+value+") to "+targetType);
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
        value = value.trim();
        // For boolean we could use valueOf, but that returns false whereas we'd rather throw errors on bad values
        if (targetType == Boolean.class || targetType == boolean.class) {
            if ("true".equalsIgnoreCase(value)) return (T) Boolean.TRUE;
            if ("false".equalsIgnoreCase(value)) return (T) Boolean.FALSE;
            if ("yes".equalsIgnoreCase(value)) return (T) Boolean.TRUE;
            if ("no".equalsIgnoreCase(value)) return (T) Boolean.FALSE;
            if ("t".equalsIgnoreCase(value)) return (T) Boolean.TRUE;
            if ("f".equalsIgnoreCase(value)) return (T) Boolean.FALSE;
            if ("y".equalsIgnoreCase(value)) return (T) Boolean.TRUE;
            if ("n".equalsIgnoreCase(value)) return (T) Boolean.FALSE;
            
            throw new ClassCoercionException("Cannot coerce type String to "+targetType.getCanonicalName()+" ("+value+"): adapting failed"); 
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
        registry.put(targetType, sourceType, fn);
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
            @SuppressWarnings("unchecked")
            @Override
            public Set apply(Collection input) {
                return new LinkedHashSet(input);
            }
        });
        registerAdapter(Collection.class, List.class, new Function<Collection,List>() {
            @SuppressWarnings("unchecked")
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
        registerAdapter(String.class, HostAndPort.class, new Function<String,HostAndPort>() {
            @Override
            public HostAndPort apply(String input) {
                return HostAndPort.fromString(input);
            }
        });
        registerAdapter(String.class, UserAndHostAndPort.class, new Function<String,UserAndHostAndPort>() {
            @Override
            public UserAndHostAndPort apply(String input) {
                return UserAndHostAndPort.fromString(input);
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
                    throw Exceptions.propagate(e);
                }
            }
        });
        registerAdapter(String.class, URI.class, new Function<String,URI>() {
            @Override
            public URI apply(String input) {
                return URI.create(input);
            }
        });
        registerAdapter(Closure.class, ConfigurableEntityFactory.class, new Function<Closure,ConfigurableEntityFactory>() {
            @SuppressWarnings("unchecked")
            @Override
            public ConfigurableEntityFactory apply(Closure input) {
                return new ClosureEntityFactory(input);
            }
        });
        registerAdapter(EntityFactory.class, ConfigurableEntityFactory.class, new Function<EntityFactory,ConfigurableEntityFactory>() {
            @SuppressWarnings("unchecked")
            @Override
            public ConfigurableEntityFactory apply(EntityFactory input) {
                if (input instanceof ConfigurableEntityFactory) return (ConfigurableEntityFactory)input;
                return new ConfigurableEntityFactoryFromEntityFactory(input);
            }
        });
        registerAdapter(Closure.class, EntityFactory.class, new Function<Closure,EntityFactory>() {
            @SuppressWarnings("unchecked")
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
            @SuppressWarnings("deprecation")
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
                return new AtomicLong(Long.parseLong(input.trim()));
            }
        });
        registerAdapter(Integer.class, AtomicInteger.class, new Function<Integer,AtomicInteger>() {
            @Override public AtomicInteger apply(final Integer input) {
                return new AtomicInteger(input);
            }
        });
        registerAdapter(String.class, AtomicInteger.class, new Function<String,AtomicInteger>() {
            @Override public AtomicInteger apply(final String input) {
                return new AtomicInteger(Integer.parseInt(input.trim()));
            }
        });
        /** This always returns a {@link Double}, cast as a {@link Number}; 
         * however primitives and boxers get exact typing due to call in #stringToPrimitive */
        registerAdapter(String.class, Number.class, new Function<String,Number>() {
            @Override
            public Number apply(String input) {
                return Double.valueOf(input);
            }
        });
        registerAdapter(BigDecimal.class, Double.class, new Function<BigDecimal,Double>() {
            @Override
            public Double apply(BigDecimal input) {
                return input.doubleValue();
            }
        });
        registerAdapter(BigInteger.class, Long.class, new Function<BigInteger,Long>() {
            @Override
            public Long apply(BigInteger input) {
                return input.longValue();
            }
        });
        registerAdapter(BigInteger.class, Integer.class, new Function<BigInteger,Integer>() {
            @Override
            public Integer apply(BigInteger input) {
                return input.intValue();
            }
        });
        registerAdapter(Double.class, BigDecimal.class, new Function<Double,BigDecimal>() {
            @Override
            public BigDecimal apply(Double input) {
                return BigDecimal.valueOf(input);
            }
        });
        registerAdapter(Long.class, BigInteger.class, new Function<Long,BigInteger>() {
            @Override
            public BigInteger apply(Long input) {
                return BigInteger.valueOf(input);
            }
        });
        registerAdapter(Integer.class, BigInteger.class, new Function<Integer,BigInteger>() {
            @Override
            public BigInteger apply(Integer input) {
                return BigInteger.valueOf(input);
            }
        });
        registerAdapter(String.class, Class.class, new Function<String,Class>() {
            @Override
            public Class apply(final String input) {
                try {
                    return Class.forName(input);
                } catch (ClassNotFoundException e) {
                    throw Exceptions.propagate(e);
                }
            }
        });
        registerAdapter(String.class, AttributeSensor.class, new Function<String,AttributeSensor>() {
            @Override
            public AttributeSensor apply(final String input) {
                return new BasicAttributeSensor<Object>(Object.class, input);
            }
        });
        registerAdapter(String.class, List.class, new Function<String,List>() {
            @Override
            public List<String> apply(final String input) {
                return JavaStringEscapes.unwrapJsonishListIfPossible(input);
            }
        });
        registerAdapter(String.class, Map.class, new Function<String,Map>() {
            @Override
            public Map apply(final String input) {
                Exception error = null;
                
                // first try wrapping in braces if needed
                if (!input.trim().startsWith("{")) {
                    try {
                        return apply("{ "+input+" }");
                    } catch (Exception e) {
                        Exceptions.propagateIfFatal(e);
                        // prefer this error
                        error = e;
                        // fall back to parsing without braces, e.g. if it's multiline
                    }
                }

                try {
                    return Yamls.getAs( Yamls.parseAll(input), Map.class );
                } catch (Exception e) {
                    Exceptions.propagateIfFatal(e);
                    if (error!=null && input.indexOf('\n')==-1) {
                        // prefer the original error if it wasn't braced and wasn't multiline
                        e = error;
                    }
                    throw new IllegalArgumentException("Cannot parse string as map with flexible YAML parsing; "+
                        (e instanceof ClassCastException ? "yaml treats it as a string" : 
                        (e instanceof IllegalArgumentException && Strings.isNonEmpty(e.getMessage())) ? e.getMessage() :
                        ""+e) );
                }

                // NB: previously we supported this also, when we did json above;
                // yaml support is better as it supports quotes (and better than json because it allows dropping quotes)
                // snake-yaml, our parser, also accepts key=value -- although i'm not sure this is strictly yaml compliant;
                // our tests will catch it if snake behaviour changes, and we can reinstate this
                // (but note it doesn't do quotes; see http://code.google.com/p/guava-libraries/issues/detail?id=412 for that):
//                return ImmutableMap.copyOf(Splitter.on(",").trimResults().omitEmptyStrings().withKeyValueSeparator("=").split(input));
            }
        });
    }
}
