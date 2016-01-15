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
package org.apache.brooklyn.enricher.stock;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.core.enricher.AbstractEnricher;
import org.apache.brooklyn.enricher.stock.reducer.Reducer;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.collections.QuorumCheck;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.text.StringPredicates;
import org.apache.brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.reflect.TypeToken;

public class Enrichers {

    private Enrichers() {}
    
    public static InitialBuilder builder() {
        return new InitialBuilder();
    }

    public abstract static class Builder<B extends Builder<B>> {
        @SuppressWarnings("unchecked")
        protected B self() {
           return (B) this;
        }
    }
    
    public abstract static class AbstractEnricherBuilder<B extends AbstractEnricherBuilder<B>> extends Builder<B> {
        final Class<? extends Enricher> enricherType;
        Boolean suppressDuplicates;
        String uniqueTag;
        Set<Object> tags = MutableSet.of();
        
        public AbstractEnricherBuilder(Class<? extends Enricher> enricherType) {
            this.enricherType = enricherType;
        }
        
        public B uniqueTag(String tag) {
            uniqueTag = Preconditions.checkNotNull(tag);
            return self();
        }
        public B addTag(Object tag) {
            tags.add(Preconditions.checkNotNull(tag));
            return self();
        }
        public B suppressDuplicates(Boolean suppressDuplicates) {
            this.suppressDuplicates = suppressDuplicates;
            return self();
        }

        protected abstract String getDefaultUniqueTag();
        
        protected EnricherSpec<? extends Enricher> build() {
            EnricherSpec<? extends Enricher> spec = EnricherSpec.create(enricherType);
            
            String uniqueTag2 = uniqueTag;
            if (uniqueTag2==null)
                uniqueTag2 = getDefaultUniqueTag();
            if (uniqueTag2!=null)
                spec.uniqueTag(uniqueTag2);
            
            if (!tags.isEmpty()) spec.tags(tags);
            if (suppressDuplicates!=null)
                spec.configure(AbstractEnricher.SUPPRESS_DUPLICATES, suppressDuplicates);
            
            return spec;
        }
    }
    
    protected abstract static class AbstractInitialBuilder<B extends AbstractInitialBuilder<B>> extends Builder<B> {
        public PropagatorBuilder propagating(Map<? extends Sensor<?>, ? extends Sensor<?>> vals) {
            return new PropagatorBuilder(vals);
        }
        public PropagatorBuilder propagating(Iterable<? extends Sensor<?>> vals) {
            return new PropagatorBuilder(vals);
        }
        public PropagatorBuilder propagating(Sensor<?>... vals) {
            return new PropagatorBuilder(vals);
        }
        public PropagatorBuilder propagatingAll() {
            return new PropagatorBuilder(true, null);
        }
        public PropagatorBuilder propagatingAllButUsualAnd(Sensor<?>... vals) {
            return new PropagatorBuilder(true, ImmutableSet.<Sensor<?>>builder().addAll(Propagator.SENSORS_NOT_USUALLY_PROPAGATED).add(vals).build());
        }
        public PropagatorBuilder propagatingAllBut(Sensor<?>... vals) {
            return new PropagatorBuilder(true, ImmutableSet.copyOf(vals));
        }
        public PropagatorBuilder propagatingAllBut(Iterable<? extends Sensor<?>> vals) {
            return new PropagatorBuilder(true, vals);
        }
        
        /**
         * Builds an enricher which transforms a given sensor:
         * <li> applying a (required) function ({@link TransformerBuilder#computing(Function)}, or
         *      {@link AbstractAggregatorBuilder#computingAverage()}/
         *      {@link AbstractAggregatorBuilder#computingSum()}, mandatory);
         * <li> and publishing it on the entity where the enricher is attached;
         * <li> optionally taking the sensor from a different source entity ({@link TransformerBuilder#from(Entity)});
         * <li> and optionally publishing it as a different sensor ({@link TransformerBuilder#publishing(AttributeSensor)});
         * <p> You must supply at least one of the optional values, of course, otherwise the enricher may loop endlessly!
         */
        public <S> TransformerBuilder<S, Object> transforming(AttributeSensor<S> val) {
            return new TransformerBuilder<S, Object>(val);
        }
        /** as {@link #transforming(AttributeSensor)} but accepting multiple sensors, with the function acting on the set of values */
        public <S> CombinerBuilder<S, Object> combining(Collection<AttributeSensor<? extends S>> vals) {
            return new CombinerBuilder<S, Object>(vals);
        }
        /** as {@link #combining(Collection)} */
        @SafeVarargs
        public final <S> CombinerBuilder<S, Object> combining(AttributeSensor<? extends S>... vals) {
            return new CombinerBuilder<S, Object>(vals);
        }
        /** as {@link #combining(Collection)} but the collection of values comes from the given sensor on multiple entities */
        public <S> AggregatorBuilder<S, Object> aggregating(AttributeSensor<S> val) {
            return new AggregatorBuilder<S,Object>(val);
        }
        /** creates an {@link UpdatingMap} enricher: 
         * {@link UpdatingMapBuilder#from(AttributeSensor)} and {@link UpdatingMapBuilder#computing(Function)} are required
         **/
        public <S,TKey,TVal> UpdatingMapBuilder<S, TKey, TVal> updatingMap(AttributeSensor<Map<TKey,TVal>> target) {
            return new UpdatingMapBuilder<S, TKey, TVal>(target);
        }
        /** creates a {@link org.apache.brooklyn.enricher.stock.Joiner} enricher builder
         * which joins entries in a list to produce a String
         **/
        public JoinerBuilder joining(AttributeSensor<?> source) {
            return new JoinerBuilder(source);
        }
        public  ReducerBuilder reducing(Class<? extends Reducer> clazz, List<AttributeSensor<?>> sourceSensors) {
            return new ReducerBuilder(clazz, sourceSensors);
        }
    }


    protected abstract static class AbstractAggregatorBuilder<S, T, B extends AbstractAggregatorBuilder<S, T, B>> extends AbstractEnricherBuilder<B> {
        protected final AttributeSensor<S> aggregating;
        protected AttributeSensor<T> publishing;
        protected Entity fromEntity;
        /** @deprecated since 0.7.0, kept for backwards compatibility for rebind, but not used otherwise */
        @Deprecated protected Function<? super Collection<S>, ? extends T> computing;
        // use supplier so latest values of other fields can be used
        protected Supplier<Function<? super Collection<S>, ? extends T>> computingSupplier;
        protected Boolean fromMembers;
        protected Boolean fromChildren;
        protected Boolean excludingBlank;
        protected ImmutableSet<Entity> fromHardcodedProducers;
        protected Predicate<? super Entity> entityFilter;
        protected Predicate<Object> valueFilter;
        protected Object defaultValueForUnreportedSensors;
        protected Object valueToReportIfNoSensors;
        
        public AbstractAggregatorBuilder(AttributeSensor<S> aggregating) {
            super(Aggregator.class);
            this.aggregating = aggregating;
        }
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public <T2 extends T> AggregatorBuilder<S,T2> publishing(AttributeSensor<? extends T2> val) {
            this.publishing = (AttributeSensor) checkNotNull(val);
            return (AggregatorBuilder) self();
        }
        public B from(Entity val) {
            this.fromEntity = checkNotNull(val);
            return self();
        }
        public B fromMembers() {
            this.fromMembers = true;
            return self();
        }
        public B fromChildren() {
            this.fromChildren = true;
            return self();
        }
        public B fromHardcodedProducers(Iterable<? extends Entity> val) {
            this.fromHardcodedProducers = ImmutableSet.copyOf(val);
            return self();
        }
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public B computing(Function<? super Collection<S>, ? extends T> val) {
            this.computingSupplier = (Supplier)Suppliers.ofInstance(checkNotNull(val));
            return self();
        }
        @SuppressWarnings({ "unchecked", "rawtypes", "unused" })
        private B computingSumLegacy() {
            // since 0.7.0, kept in case we need to rebind to this
            Function<Collection<S>, Number> function = new Function<Collection<S>, Number>()  {
                @Override public Number apply(Collection<S> input) {
                    return sum((Collection)input, (Number)defaultValueForUnreportedSensors, (Number)valueToReportIfNoSensors, (TypeToken) publishing.getTypeToken());
                }};
            this.computing((Function)function);
            return self();
        }
        @SuppressWarnings({ "unchecked", "rawtypes", "unused" })
        private B computingAverageLegacy() {
            // since 0.7.0, kept in case we need to rebind to this
            Function<Collection<S>, Number> function = new Function<Collection<S>, Number>() {
                @Override public Number apply(Collection<S> input) {
                    return average((Collection)input, (Number)defaultValueForUnreportedSensors, (Number)valueToReportIfNoSensors, (TypeToken) publishing.getTypeToken());
                }};
            this.computing((Function)function);
            return self();
        }
        public B computingSum() {
            this.computingSupplier = new Supplier<Function<? super Collection<S>, ? extends T>>() {
                @Override
                @SuppressWarnings({ "unchecked", "rawtypes" })
                public Function<? super Collection<S>, ? extends T> get() {
                    // relies on TypeCoercion of result from Number to T, and type erasure for us to get away with it!
                    return (Function)new ComputingSum((Number)defaultValueForUnreportedSensors, (Number)valueToReportIfNoSensors, publishing.getTypeToken());
                }
            };
            return self();
        }

        public B computingAverage() {
            this.computingSupplier = new Supplier<Function<? super Collection<S>, ? extends T>>() {
                @Override
                @SuppressWarnings({ "unchecked", "rawtypes" })
                public Function<? super Collection<S>, ? extends T> get() {
                    // relies on TypeCoercion of result from Number to T, and type erasure for us to get away with it!
                    return (Function)new ComputingAverage((Number)defaultValueForUnreportedSensors, (Number)valueToReportIfNoSensors, publishing.getTypeToken());
                }
            };
            return self();
        }
        public B defaultValueForUnreportedSensors(S val) {
            this.defaultValueForUnreportedSensors = val;
            return self();
        }
        public B valueToReportIfNoSensors(Object val) {
            this.valueToReportIfNoSensors = val;
            return self();
        }
        public B entityFilter(Predicate<? super Entity> val) {
            this.entityFilter = val;
            return self();
        }
        public B excludingBlank() {
            this.excludingBlank = true;
            return self();
        }
        @Override
        protected String getDefaultUniqueTag() {
            if (publishing==null) return null;
            return "aggregator:"+publishing.getName();
        }
        public EnricherSpec<?> build() {
            Predicate<Object> valueFilter;
            if (Boolean.TRUE.equals(excludingBlank)) {
                valueFilter = new Predicate<Object>() {
                    @Override public boolean apply(Object input) {
                        return (input != null) &&
                                ((input instanceof CharSequence) ? Strings.isNonBlank((CharSequence)input) : true);
                    }
                };
                // above kept for deserialization; not sure necessary
                valueFilter = StringPredicates.isNonBlank(); 
            } else {
                valueFilter = null;
            }
            // FIXME excludingBlank; use valueFilter? exclude means ignored entirely or substituted for defaultMemberValue?
            return super.build().configure(MutableMap.builder()
                            .putIfNotNull(Aggregator.PRODUCER, fromEntity)
                            .put(Aggregator.TARGET_SENSOR, publishing)
                            .put(Aggregator.SOURCE_SENSOR, aggregating)
                            .putIfNotNull(Aggregator.FROM_CHILDREN, fromChildren)
                            .putIfNotNull(Aggregator.FROM_MEMBERS, fromMembers)
                            .putIfNotNull(Aggregator.TRANSFORMATION, computingSupplier.get())
                            .putIfNotNull(Aggregator.FROM_HARDCODED_PRODUCERS, fromHardcodedProducers)
                            .putIfNotNull(Aggregator.ENTITY_FILTER, entityFilter)
                            .putIfNotNull(Aggregator.VALUE_FILTER, valueFilter)
                            .putIfNotNull(Aggregator.DEFAULT_MEMBER_VALUE, defaultValueForUnreportedSensors)
                            .build());
        }
        
        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .omitNullValues()
                    .add("aggregating", aggregating)
                    .add("publishing", publishing)
                    .add("fromEntity", fromEntity)
                    .add("computing", computingSupplier)
                    .add("fromMembers", fromMembers)
                    .add("fromChildren", fromChildren)
                    .add("excludingBlank", excludingBlank)
                    .add("fromHardcodedProducers", fromHardcodedProducers)
                    .add("entityFilter", entityFilter)
                    .add("valueFilter", valueFilter)
                    .add("defaultValueForUnreportedSensors", defaultValueForUnreportedSensors)
                    .add("valueToReportIfNoSensors", valueToReportIfNoSensors)
                    .toString();
        }
    }
    
    protected abstract static class AbstractCombinerBuilder<S, T, B extends AbstractCombinerBuilder<S, T, B>> extends AbstractEnricherBuilder<B> {
        protected final List<AttributeSensor<? extends S>> combining;
        protected AttributeSensor<T> publishing;
        protected Entity fromEntity;
        protected Function<? super Collection<S>, ? extends T> computing;
        protected Boolean excludingBlank;
        protected Object valueToReportIfNoSensors;
        protected Predicate<Object> valueFilter;

        // For summing/averaging
        protected Object defaultValueForUnreportedSensors;
        
        @SafeVarargs
        public AbstractCombinerBuilder(AttributeSensor<? extends S>... vals) {
            this(ImmutableList.copyOf(vals));
        }
        public AbstractCombinerBuilder(Collection<AttributeSensor<? extends S>> vals) {
            super(Combiner.class);
            checkArgument(checkNotNull(vals).size() > 0, "combining-sensors must be non-empty");
            this.combining = ImmutableList.<AttributeSensor<? extends S>>copyOf(vals);
        }
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public <T2 extends T> CombinerBuilder<S,T2> publishing(AttributeSensor<? extends T2> val) {
            this.publishing = (AttributeSensor) checkNotNull(val);
            return (CombinerBuilder) this;
        }
        public B from(Entity val) {
            this.fromEntity = checkNotNull(val);
            return self();
        }
        public B computing(Function<? super Collection<S>, ? extends T> val) {
            this.computing = checkNotNull(val);
            return self();
        }
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public B computingSum() {
            Function<Collection<S>, Number> function = new Function<Collection<S>, Number>() {
                @Override public Number apply(Collection<S> input) {
                    return sum((Collection)input, (Number)defaultValueForUnreportedSensors, (Number)valueToReportIfNoSensors, (TypeToken) publishing.getTypeToken());
                }};
            this.computing((Function)function);
            return self();
        }
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public B computingAverage() {
            Function<Collection<S>, Number> function = new Function<Collection<S>, Number>() {
                @Override public Number apply(Collection<S> input) {
                    return average((Collection)input, (Number)defaultValueForUnreportedSensors, (Number)valueToReportIfNoSensors, (TypeToken) publishing.getTypeToken());
                }};
            this.computing((Function)function);
            return self();
        }
        public B defaultValueForUnreportedSensors(Object val) {
            this.defaultValueForUnreportedSensors = val;
            return self();
        }
        public B valueToReportIfNoSensors(Object val) {
            this.valueToReportIfNoSensors = val;
            return self();
        }
        public B excludingBlank() {
            this.excludingBlank = true;
            return self();
        }
        @Override
        protected String getDefaultUniqueTag() {
            if (publishing==null) return null;
            return "combiner:"+publishing.getName();
        }
        public EnricherSpec<?> build() {
            return super.build().configure(MutableMap.builder()
                            .putIfNotNull(Combiner.PRODUCER, fromEntity)
                            .put(Combiner.TARGET_SENSOR, publishing)
                            .put(Combiner.SOURCE_SENSORS, combining)
                            .putIfNotNull(Combiner.TRANSFORMATION, computing)
                            .putIfNotNull(Combiner.VALUE_FILTER, valueFilter)
                            .build());
        }
        
        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .omitNullValues()
                    .add("combining", combining)
                    .add("publishing", publishing)
                    .add("fromEntity", fromEntity)
                    .add("computing", computing)
                    .add("excludingBlank", excludingBlank)
                    .add("valueToReportIfNoSensors", valueToReportIfNoSensors)
                    .add("valueFilter", valueFilter)
                    .toString();
        }
    }

    protected abstract static class AbstractTransformerBuilder<S, T, B extends AbstractTransformerBuilder<S, T, B>> extends AbstractEnricherBuilder<B> {
        protected final AttributeSensor<S> transforming;
        protected AttributeSensor<T> publishing;
        protected Entity fromEntity;
        protected Function<? super S, ?> computing;
        protected Function<? super SensorEvent<S>, ?> computingFromEvent;

        public AbstractTransformerBuilder(AttributeSensor<S> val) {
            super(Transformer.class);
            this.transforming = checkNotNull(val);
        }
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public <T2 extends T> TransformerBuilder<S,T2> publishing(AttributeSensor<? extends T2> val) {
            this.publishing = (AttributeSensor) checkNotNull(val);
            return (TransformerBuilder) this;
        }
        public B from(Entity val) {
            this.fromEntity = checkNotNull(val);
            return self();
        }
        public B computing(Function<? super S, ? extends T> val) {
            this.computing = checkNotNull(val);
            return self();
        }
        public B computingFromEvent(Function<? super SensorEvent<S>, ? extends T> val) {
            this.computingFromEvent = checkNotNull(val);
            return self();
        }
        @Override
        protected String getDefaultUniqueTag() {
            if (publishing==null) return null;
            return "transformer:"+publishing.getName();
        }
        public EnricherSpec<?> build() {
            return super.build().configure(MutableMap.builder()
                            .putIfNotNull(Transformer.PRODUCER, fromEntity)
                            .put(Transformer.TARGET_SENSOR, publishing)
                            .put(Transformer.SOURCE_SENSOR, transforming)
                            .putIfNotNull(Transformer.TRANSFORMATION_FROM_VALUE, computing)
                            .putIfNotNull(Transformer.TRANSFORMATION_FROM_EVENT, computingFromEvent)
                            .build());
        }
        
        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .omitNullValues()
                    .add("publishing", publishing)
                    .add("transforming", transforming)
                    .add("fromEntity", fromEntity)
                    .add("computing", computing)
                    .toString();
        }
    }

    protected abstract static class AbstractPropagatorBuilder<B extends AbstractPropagatorBuilder<B>> extends AbstractEnricherBuilder<B> {
        protected final Map<? extends Sensor<?>, ? extends Sensor<?>> propagating;
        protected final Boolean propagatingAll;
        protected final Iterable<? extends Sensor<?>> propagatingAllBut;
        protected Entity fromEntity;
        protected Task<? extends Entity> fromEntitySupplier;
        
        public AbstractPropagatorBuilder(Map<? extends Sensor<?>, ? extends Sensor<?>> vals) {
            super(Propagator.class);
            checkArgument(checkNotNull(vals).size() > 0, "propagating-sensors must be non-empty");
            this.propagating = vals;
            this.propagatingAll = null;
            this.propagatingAllBut = null;
        }
        public AbstractPropagatorBuilder(Iterable<? extends Sensor<?>> vals) {
            this(newIdentityMap(ImmutableSet.copyOf(vals)));
        }
        public AbstractPropagatorBuilder(Sensor<?>... vals) {
            this(newIdentityMap(ImmutableSet.copyOf(vals)));
        }
        AbstractPropagatorBuilder(boolean propagatingAll, Iterable<? extends Sensor<?>> butVals) {
            super(Propagator.class);
            // Ugly constructor! Taking boolean to differentiate it from others; could use a static builder
            // but feels like overkill having a builder for a builder, being called by a builder!
            checkArgument(propagatingAll, "Not propagating all; use PropagatingAll(vals)");
            this.propagating = null;
            this.propagatingAll = true;
            this.propagatingAllBut = (butVals == null || Iterables.isEmpty(butVals)) ? null: butVals;
        }
        public B from(Entity val) {
            this.fromEntity = checkNotNull(val);
            return self();
        }
        public B from(Task<? extends Entity> val) {
            this.fromEntitySupplier = checkNotNull(val);
            return self();
        }
        @Override
        protected String getDefaultUniqueTag() {
            List<String> summary = MutableList.of();
            if (propagating!=null) {
                for (Map.Entry<? extends Sensor<?>, ? extends Sensor<?>> entry: propagating.entrySet()) {
                    if (entry.getKey().getName().equals(entry.getValue().getName()))
                        summary.add(entry.getKey().getName());
                    else
                        summary.add(entry.getKey().getName()+"->"+entry.getValue().getName());
                }
            }
            if (Boolean.TRUE.equals(propagatingAll))
                summary.add("ALL");
            if (propagatingAllBut!=null && !Iterables.isEmpty(propagatingAllBut)) {
                List<String> allBut = MutableList.of();
                for (Sensor<?> s: propagatingAllBut) allBut.add(s.getName());
                summary.add("ALL_BUT:"+com.google.common.base.Joiner.on(",").join(allBut));
            }
            
            // TODO What to use as the entity id if using fromEntitySupplier? 
            String fromId = (fromEntity != null) ? fromEntity.getId() : fromEntitySupplier.getId();
            
            return "propagating["+fromId+":"+com.google.common.base.Joiner.on(",").join(summary)+"]";
        }
        public EnricherSpec<? extends Enricher> build() {
            return super.build().configure(MutableMap.builder()
                            .putIfNotNull(Propagator.PRODUCER, fromEntity)
                            .putIfNotNull(Propagator.PRODUCER, fromEntitySupplier)
                            .putIfNotNull(Propagator.SENSOR_MAPPING, propagating)
                            .putIfNotNull(Propagator.PROPAGATING_ALL, propagatingAll)
                            .putIfNotNull(Propagator.PROPAGATING_ALL_BUT, propagatingAllBut)
                            .build());
        }
        
        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .omitNullValues()
                    .add("fromEntity", fromEntity)
                    .add("fromEntitySupplier", fromEntitySupplier)
                    .add("propagating", propagating)
                    .add("propagatingAll", propagatingAll)
                    .add("propagatingAllBut", propagatingAllBut)
                    .toString();
        }
    }

    public abstract static class AbstractUpdatingMapBuilder<S, TKey, TVal, B extends AbstractUpdatingMapBuilder<S, TKey, TVal, B>> extends AbstractEnricherBuilder<B> {
        protected AttributeSensor<Map<TKey,TVal>> targetSensor;
        protected AttributeSensor<? extends S> fromSensor;
        protected TKey key;
        protected Function<S, ? extends TVal> computing;
        protected Boolean removingIfResultIsNull;
        
        public AbstractUpdatingMapBuilder(AttributeSensor<Map<TKey,TVal>> target) {
            super(UpdatingMap.class);
            this.targetSensor = target;
        }
        @SuppressWarnings({ "unchecked", "rawtypes" })
        public <S2 extends S> UpdatingMapBuilder<S2,TKey,TVal> from(AttributeSensor<S2> fromSensor) {
            this.fromSensor = checkNotNull(fromSensor);
            return (UpdatingMapBuilder) this;
        }
        public B computing(Function<S,? extends TVal> val) {
            this.computing = checkNotNull(val);
            return self();
        }
        /** sets an explicit key to use; defaults to using the name of the source sensor specified in {@link #from(AttributeSensor)} */
        public B key(TKey key) {
            this.key = key;
            return self();
        }
        /** sets explicit behaviour for treating <code>null</code> return values;
         * default is to remove */
        public B removingIfResultIsNull(boolean val) {
            this.removingIfResultIsNull = val;
            return self();
        }
        @Override
        protected String getDefaultUniqueTag() {
            if (targetSensor==null || fromSensor==null) return null;
            return "updating:"+targetSensor.getName()+"<-"+fromSensor.getName();
        }
        public EnricherSpec<?> build() {
            return super.build().configure(MutableMap.builder()
                            .put(UpdatingMap.TARGET_SENSOR, targetSensor)
                            .put(UpdatingMap.SOURCE_SENSOR, fromSensor)
                            .putIfNotNull(UpdatingMap.KEY_IN_TARGET_SENSOR, key)
                            .put(UpdatingMap.COMPUTING, computing)
                            .putIfNotNull(UpdatingMap.REMOVING_IF_RESULT_IS_NULL, removingIfResultIsNull)
                            .build());
        }
        
        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .omitNullValues()
                    .add("publishing", targetSensor)
                    .add("fromSensor", fromSensor)
                    .add("key", key)
                    .add("computing", computing)
                    .add("removingIfResultIsNull", removingIfResultIsNull)
                    .toString();
        }
    }

    protected abstract static class AbstractJoinerBuilder<B extends AbstractJoinerBuilder<B>> extends AbstractEnricherBuilder<B> {
        protected final AttributeSensor<?> transforming;
        protected AttributeSensor<String> publishing;
        protected Entity fromEntity;
        protected String separator;
        protected Boolean quote;
        protected Integer minimum;
        protected Integer maximum;

        public AbstractJoinerBuilder(AttributeSensor<?> source) {
            super(Joiner.class);
            this.transforming = checkNotNull(source);
        }
        public B publishing(AttributeSensor<String> target) {
            this.publishing = checkNotNull(target);
            return self();
        }
        public B separator(String separator) {
            this.separator = separator;
            return self();
        }
        public B quote(Boolean quote) {
            this.quote = quote;
            return self();
        }
        public B minimum(Integer minimum) {
            this.minimum = minimum;
            return self();
        }
        public B maximum(Integer maximum) {
            this.maximum = maximum;
            return self();
        }
        @Override
        protected String getDefaultUniqueTag() {
            if (transforming==null || publishing==null) return null;
            return "joiner:"+transforming.getName()+"->"+publishing.getName();
        }
        public EnricherSpec<?> build() {
            return super.build().configure(MutableMap.builder()
                            .putIfNotNull(Joiner.PRODUCER, fromEntity)
                            .put(Joiner.TARGET_SENSOR, publishing)
                            .put(Joiner.SOURCE_SENSOR, transforming)
                            .putIfNotNull(Joiner.SEPARATOR, separator)
                            .putIfNotNull(Joiner.QUOTE, quote)
                            .putIfNotNull(Joiner.MINIMUM, minimum)
                            .putIfNotNull(Joiner.MAXIMUM, maximum)
                            .build());
        }
        
        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .omitNullValues()
                    .add("publishing", publishing)
                    .add("transforming", transforming)
                    .add("separator", separator)
                    .toString();
        }
    }

    protected abstract static class AbstractReducerBuilder<B extends AbstractReducerBuilder<B>> extends AbstractEnricherBuilder<B> {
        protected AttributeSensor<?> publishing;
        protected Entity fromEntity;
        protected List<AttributeSensor<?>> reducing;
        protected Function<? extends Iterable<?>, ?> computing;
        protected String functionName;
        private Map<String, Object> parameters;

        public AbstractReducerBuilder(Class<? extends Reducer> clazz, List<AttributeSensor<?>> val) {
            super(checkNotNull(clazz));
            this.reducing = checkNotNull(val);
        }
        
        public B publishing(AttributeSensor<?> val) {
            this.publishing =  checkNotNull(val);
            return self();
        }

        public B from(Entity val) {
            this.fromEntity = checkNotNull(val);
            return self();
        }

        public B computing(Function<? extends Iterable<?>, ?> val) {
            this.computing = checkNotNull(val);
            return self();
        }
        
        public B computing(String functionName) {
            return computing(functionName, ImmutableMap.<String, Object>of());
        }
        
        public B computing(String functionName, Map<String, Object> parameters) {
            this.functionName = functionName;
            this.parameters = parameters;
            return self();
        }

        public EnricherSpec<?> build() {
            return super.build().configure(MutableMap.builder()
                    .put(Reducer.SOURCE_SENSORS, reducing)
                    .put(Reducer.PRODUCER, fromEntity)
                    .put(Reducer.TARGET_SENSOR, publishing)
                    .putIfNotNull(Reducer.REDUCER_FUNCTION, computing)
                    .putIfNotNull(Reducer.REDUCER_FUNCTION_TRANSFORMATION, functionName)
                    .putIfNotNull(Reducer.PARAMETERS, parameters)
                    .build()
            );
        }

        @Override
        protected String getDefaultUniqueTag() {
            return "reducer:" + reducing.toString();
        }
    }
    
    public static class InitialBuilder extends AbstractInitialBuilder<InitialBuilder> {
    }

    public static class AggregatorBuilder<S, T> extends AbstractAggregatorBuilder<S, T, AggregatorBuilder<S, T>> {
        public AggregatorBuilder(AttributeSensor<S> aggregating) {
            super(aggregating);
        }
    }

    public static class PropagatorBuilder extends AbstractPropagatorBuilder<PropagatorBuilder> {
        public PropagatorBuilder(Map<? extends Sensor<?>, ? extends Sensor<?>> vals) {
            super(vals);
        }
        public PropagatorBuilder(Iterable<? extends Sensor<?>> vals) {
            super(vals);
        }
        public PropagatorBuilder(Sensor<?>... vals) {
            super(vals);
        }
        PropagatorBuilder(boolean propagatingAll, Iterable<? extends Sensor<?>> butVals) {
            super(propagatingAll, butVals);
        }
    }

    public static class CombinerBuilder<S, T> extends AbstractCombinerBuilder<S, T, CombinerBuilder<S, T>> {
        @SafeVarargs
        public CombinerBuilder(AttributeSensor<? extends S>... vals) {
            super(vals);
        }
        public CombinerBuilder(Collection<AttributeSensor<? extends S>> vals) {
            super(vals);
        }
    }

    public static class TransformerBuilder<S, T> extends AbstractTransformerBuilder<S, T, TransformerBuilder<S, T>> {
        public TransformerBuilder(AttributeSensor<S> val) {
            super(val);
        }
    }

    public static class UpdatingMapBuilder<S, TKey, TVal> extends AbstractUpdatingMapBuilder<S, TKey, TVal, UpdatingMapBuilder<S, TKey, TVal>> {
        public UpdatingMapBuilder(AttributeSensor<Map<TKey,TVal>> val) {
            super(val);
        }
    }

    public static class JoinerBuilder extends AbstractJoinerBuilder<JoinerBuilder> {
        public JoinerBuilder(AttributeSensor<?> source) {
            super(source);
        }
    }

    public static class ReducerBuilder extends AbstractReducerBuilder<ReducerBuilder> {
        public ReducerBuilder(Class<? extends Reducer> clazz, List<AttributeSensor<?>> val) {
            super(clazz, val);
        }
    }

    @Beta
    private abstract static class ComputingNumber<T extends Number> implements Function<Collection<T>, T> {
        protected final Number defaultValueForUnreportedSensors;
        protected final Number valueToReportIfNoSensors;
        protected final TypeToken<T> typeToken;
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public ComputingNumber(Number defaultValueForUnreportedSensors, Number valueToReportIfNoSensors, TypeToken<T> typeToken) {
            this.defaultValueForUnreportedSensors = defaultValueForUnreportedSensors;
            this.valueToReportIfNoSensors = valueToReportIfNoSensors;
            if (typeToken!=null && TypeToken.of(Number.class).isAssignableFrom(typeToken.getType())) {
                this.typeToken = typeToken;
            } else if (typeToken==null || typeToken.isAssignableFrom(Number.class)) {
                // use double if e.g. Object is supplied
                this.typeToken = (TypeToken)TypeToken.of(Double.class);
            } else {
                throw new IllegalArgumentException("Type "+typeToken+" is not valid for "+this);
            }
        }
        @Override public abstract T apply(Collection<T> input);
    }

    @Beta
    public static class ComputingSum<T extends Number> extends ComputingNumber<T> {
        public ComputingSum(Number defaultValueForUnreportedSensors, Number valueToReportIfNoSensors, TypeToken<T> typeToken) {
            super(defaultValueForUnreportedSensors, valueToReportIfNoSensors, typeToken);
        }
        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override public T apply(Collection<T> input) {
            return (T) sum((Collection)input, (Number)defaultValueForUnreportedSensors, (Number)valueToReportIfNoSensors, typeToken);
        }
    }

    @Beta
    public static class ComputingAverage<T extends Number> extends ComputingNumber<T> {
        public ComputingAverage(Number defaultValueForUnreportedSensors, Number valueToReportIfNoSensors, TypeToken<T> typeToken) {
            super(defaultValueForUnreportedSensors, valueToReportIfNoSensors, typeToken);
        }
        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override public T apply(Collection<T> input) {
            return (T) average((Collection)input, (Number)defaultValueForUnreportedSensors, (Number)valueToReportIfNoSensors, typeToken);
        }
    }

    protected static <T extends Number> T average(Collection<T> vals, Number defaultValueForUnreportedSensors, Number valueToReportIfNoSensors, TypeToken<T> type) {
        Double doubleValueToReportIfNoSensors = (valueToReportIfNoSensors == null) ? null : valueToReportIfNoSensors.doubleValue();
        int count = count(vals, defaultValueForUnreportedSensors!=null);
        Double result = (count==0) ? doubleValueToReportIfNoSensors : 
            (Double) ((sum(vals, defaultValueForUnreportedSensors, 0, TypeToken.of(Double.class)) / count));
        
        return cast(result, type);
    }
    
    @SuppressWarnings("unchecked")
    protected static <N extends Number> N cast(Number n, TypeToken<? extends N> numberType) {
        return (N) TypeCoercions.castPrimitive(n, numberType.getRawType());
    }

    @Beta  //may be moved
    public static <N extends Number> N sum(Iterable<? extends Number> vals, Number valueIfNull, Number valueIfNone, TypeToken<N> type) {
        double result = 0d;
        int count = 0;
        if (vals!=null) {
            for (Number val : vals) { 
                if (val!=null) {
                    result += val.doubleValue();
                    count++;
                } else if (valueIfNull!=null) {
                    result += valueIfNull.doubleValue();
                    count++;
                }
            }
        }
        if (count==0) return cast(valueIfNone, type);
        return cast(result, type);
    }
    
    protected static int count(Iterable<? extends Object> vals, boolean includeNullValues) {
        int result = 0;
        if (vals != null) 
            for (Object val : vals) 
                if (val!=null || includeNullValues) result++;
        return result;
    }
    
    @Beta
    public static class ComputingIsQuorate<T> implements Function<Collection<Boolean>, Boolean> {
        protected final TypeToken<T> typeToken;
        protected final QuorumCheck quorumCheck;
        protected final int totalSize;

        @SuppressWarnings({ "unchecked", "rawtypes" })
        public ComputingIsQuorate(TypeToken<T> typeToken, QuorumCheck quorumCheck, int totalSize) {
            this.quorumCheck = quorumCheck;
            this.totalSize = totalSize;

            if (typeToken!=null && TypeToken.of(Boolean.class).isAssignableFrom(typeToken.getType())) {
                this.typeToken = typeToken;
            } else if (typeToken==null || typeToken.isAssignableFrom(Boolean.class)) {
                this.typeToken = (TypeToken)TypeToken.of(Boolean.class);
            } else {
                throw new IllegalArgumentException("Type " + typeToken + " is not valid for " + this + " -- expected " + TypeToken.of(Boolean.class));
            }
        }

        @Override
        public Boolean apply(Collection<Boolean> input) {
            int numTrue = 0;

            for (Boolean inputVal : input)
                if (Boolean.TRUE.equals(inputVal))
                    numTrue++;

            return Boolean.valueOf(quorumCheck.isQuorate(numTrue, totalSize));
        }
    }

    private static <T> Map<T,T> newIdentityMap(Set<T> keys) {
        Map<T,T> result = Maps.newLinkedHashMap();
        for (T key : keys) {
            result.put(key, key);
        }
        return result;
    }
    
}
