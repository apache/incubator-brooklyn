package brooklyn.entity.java;

import static brooklyn.util.GroovyJavaMethods.truth;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.management.openmbean.CompositeData;

import brooklyn.config.render.RendererHints;
import brooklyn.enricher.RollingTimeWindowMeanEnricher;
import brooklyn.enricher.TimeFractionDeltaEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.util.math.MathFunctions;
import brooklyn.util.text.ByteSizeStrings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Function;

public class JavaAppUtils {

    public static boolean isEntityMxBeanStatsEnabled(Entity entity) {
        return truth(entity.getConfig(UsesJavaMXBeans.MXBEAN_STATS_ENABLED));
    }

    /**
     * @see #connectJavaAppServerPolicies(EntityLocal, Duration)
     * @see #getMxBeanSensorsBuilder(EntityLocal)
     */
    @Nullable
    public static JmxFeed connectMXBeanSensors(EntityLocal entity) {
        if (isEntityMxBeanStatsEnabled(entity)) {
            return getMxBeanSensorsBuilder(entity).build();
        } else {
            return null;
        }
    }

    /** @see #connectJavaAppServerPolicies(EntityLocal, Duration) */
    @Nullable
    public static JmxFeed connectMXBeanSensors(EntityLocal entity, long jmxPollPeriodMs) {
        if (isEntityMxBeanStatsEnabled(entity)) {
            return getMxBeanSensorsBuilder(entity, jmxPollPeriodMs).build();
        } else {
            return null;
        }
    }

    /**
     * @param entity The entity at which to poll
     * @param jmxPollPeriod How often to poll
     * @return A {@link JmxFeed} configured to poll the given entity at the given period,
     *         or null if the entity is not configured for MXBEAN_STATS
     * @see brooklyn.entity.java.UsesJavaMXBeans#MXBEAN_STATS_ENABLED
     */
    @Nullable
    public static JmxFeed connectMXBeanSensors(EntityLocal entity, Duration jmxPollPeriod) {
        if (isEntityMxBeanStatsEnabled(entity)) {
            return getMxBeanSensorsBuilder(entity, jmxPollPeriod).build();
        } else {
            return null;
        }
    }
    
    public static void connectJavaAppServerPolicies(EntityLocal entity) {
        connectJavaAppServerPolicies(entity, Duration.TEN_SECONDS);
    }

    public static void connectJavaAppServerPolicies(EntityLocal entity, Duration windowPeriod) {
        entity.addEnricher(new TimeFractionDeltaEnricher<Double>(entity, UsesJavaMXBeans.PROCESS_CPU_TIME, 
                UsesJavaMXBeans.PROCESS_CPU_TIME_FRACTION_LAST, TimeUnit.MILLISECONDS));

        entity.addEnricher(new RollingTimeWindowMeanEnricher<Double>(entity,
                UsesJavaMXBeans.PROCESS_CPU_TIME_FRACTION_LAST, UsesJavaMXBeans.PROCESS_CPU_TIME_FRACTION_IN_WINDOW,
                windowPeriod));
    }

    /**
     * @param entity The entity at which to poll
     * @return A {@link JmxFeed.Builder} configured to poll entity every ten seconds
     * @see #getMxBeanSensorsBuilder(EntityLocal, Duration)
     */
    @Nonnull
    public static JmxFeed.Builder getMxBeanSensorsBuilder(EntityLocal entity) {
        return getMxBeanSensorsBuilder(entity, Duration.TEN_SECONDS);
    }

    /** @see #getMxBeanSensorsBuilder(EntityLocal, Duration) */
    @Nonnull
    public static JmxFeed.Builder getMxBeanSensorsBuilder(EntityLocal entity, long jmxPollPeriod) {
        return getMxBeanSensorsBuilder(entity, Duration.millis(jmxPollPeriod));
    }

    /**
     * @param entity The entity at which to poll
     * @param jmxPollPeriod How often to poll
     * @return A {@link JmxFeed.Builder} configured to poll many interesting MXBeans
     *         at the given entity and to repeat according to the given poll period.
     *         <p/>
     *         If an entity does not have MXBean stats enabled (i.e. {@link UsesJavaMXBeans#MXBEAN_STATS_ENABLED} is
     *         configured to false) then returns a builder configured with entity and duration but no polls.
     *         <p/>
     *         Use {@link #connectMXBeanSensors(EntityLocal, Duration)} to create and build in one step.
     */
    @Nonnull
    @SuppressWarnings({"unchecked"})
    public static JmxFeed.Builder getMxBeanSensorsBuilder(EntityLocal entity, Duration jmxPollPeriod) {
        JmxFeed.Builder builder = JmxFeed.builder()
                .entity(entity)
                .period(jmxPollPeriod);
        if (isEntityMxBeanStatsEnabled(entity)) {
            // TODO Could we reuse the result of compositeDataToMemoryUsage?
            builder
                .pollAttribute(new JmxAttributePollConfig<Long>(UsesJavaMXBeans.USED_HEAP_MEMORY)
                        .objectName(ManagementFactory.MEMORY_MXBEAN_NAME)
                        .attributeName("HeapMemoryUsage")
                        .onSuccess((Function) HttpValueFunctions.chain(compositeDataToMemoryUsage(), new Function<MemoryUsage, Long>() {
                            @Override public Long apply(MemoryUsage input) {
                                return (input == null) ? null : input.getUsed();
                            }})))
                .pollAttribute(new JmxAttributePollConfig<Long>(UsesJavaMXBeans.INIT_HEAP_MEMORY)
                        .objectName(ManagementFactory.MEMORY_MXBEAN_NAME)
                        .attributeName("HeapMemoryUsage")
                        .onSuccess((Function) HttpValueFunctions.chain(compositeDataToMemoryUsage(), new Function<MemoryUsage, Long>() {
                            @Override public Long apply(MemoryUsage input) {
                                return (input == null) ? null : input.getInit();
                            }})))
                .pollAttribute(new JmxAttributePollConfig<Long>(UsesJavaMXBeans.COMMITTED_HEAP_MEMORY)
                        .objectName(ManagementFactory.MEMORY_MXBEAN_NAME)
                        .attributeName("HeapMemoryUsage")
                        .onSuccess((Function) HttpValueFunctions.chain(compositeDataToMemoryUsage(), new Function<MemoryUsage, Long>() {
                            @Override public Long apply(MemoryUsage input) {
                                return (input == null) ? null : input.getCommitted();
                            }})))
                .pollAttribute(new JmxAttributePollConfig<Long>(UsesJavaMXBeans.MAX_HEAP_MEMORY)
                        .objectName(ManagementFactory.MEMORY_MXBEAN_NAME)
                        .attributeName("HeapMemoryUsage")
                        .onSuccess((Function) HttpValueFunctions.chain(compositeDataToMemoryUsage(), new Function<MemoryUsage, Long>() {
                            @Override public Long apply(MemoryUsage input) {
                                return (input == null) ? null : input.getMax();
                            }})))
                .pollAttribute(new JmxAttributePollConfig<Long>(UsesJavaMXBeans.NON_HEAP_MEMORY_USAGE)
                        .objectName(ManagementFactory.MEMORY_MXBEAN_NAME)
                        .attributeName("NonHeapMemoryUsage")
                        .onSuccess((Function) HttpValueFunctions.chain(compositeDataToMemoryUsage(), new Function<MemoryUsage, Long>() {
                            @Override public Long apply(MemoryUsage input) {
                                return (input == null) ? null : input.getUsed();
                            }})))

                .pollAttribute(new JmxAttributePollConfig<Integer>(UsesJavaMXBeans.CURRENT_THREAD_COUNT)
                        .objectName(ManagementFactory.THREAD_MXBEAN_NAME)
                        .attributeName("ThreadCount"))
                .pollAttribute(new JmxAttributePollConfig<Integer>(UsesJavaMXBeans.PEAK_THREAD_COUNT)
                        .objectName(ManagementFactory.THREAD_MXBEAN_NAME)
                        .attributeName("PeakThreadCount"))

                .pollAttribute(new JmxAttributePollConfig<Long>(UsesJavaMXBeans.START_TIME)
                        .objectName(ManagementFactory.RUNTIME_MXBEAN_NAME)
                        .period(60, TimeUnit.SECONDS)
                        .attributeName("StartTime"))
                .pollAttribute(new JmxAttributePollConfig<Long>(UsesJavaMXBeans.UP_TIME)
                        .objectName(ManagementFactory.RUNTIME_MXBEAN_NAME)
                        .period(60, TimeUnit.SECONDS)
                        .attributeName("Uptime"))

                .pollAttribute(new JmxAttributePollConfig<Double>(UsesJavaMXBeans.PROCESS_CPU_TIME)
                        .objectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME)
                        .attributeName("ProcessCpuTime")
                        .onSuccess((Function) MathFunctions.times(0.001*0.001)))   // nanos to millis
                .pollAttribute(new JmxAttributePollConfig<Double>(UsesJavaMXBeans.SYSTEM_LOAD_AVERAGE)
                        .objectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME)
                        .attributeName("SystemLoadAverage"))
                .pollAttribute(new JmxAttributePollConfig<Integer>(UsesJavaMXBeans.AVAILABLE_PROCESSORS)
                        .objectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME)
                        .period(60, TimeUnit.SECONDS)
                        .attributeName("AvailableProcessors"))
                .pollAttribute(new JmxAttributePollConfig<Long>(UsesJavaMXBeans.TOTAL_PHYSICAL_MEMORY_SIZE)
                        .objectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME)
                        .period(60, TimeUnit.SECONDS)
                        .attributeName("TotalPhysicalMemorySize"))
                .pollAttribute(new JmxAttributePollConfig<Long>(UsesJavaMXBeans.FREE_PHYSICAL_MEMORY_SIZE)
                        .objectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME)
                        .period(60, TimeUnit.SECONDS)
                        .attributeName("FreePhysicalMemorySize"));
         //FIXME: need a new type of adapter that maps multiple objectNames to a mapping
         // jmxAdapter.objectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*").with {
         //     attribute("SystemLoadAverage").subscribe(UsesJavaMXBeans.GARBAGE_COLLECTION_TIME, { def m -> log.info("XXXXXXX $m") });
         // }
        }
        return builder;
    }

    /** @deprecated Since 0.7.0. Use {@link brooklyn.util.math.MathFunctions#times(double)} instead */
    @Deprecated
    public static Function<Number, Double> times(final double x) {
        return MathFunctions.times(x);
    }

    public static Function<CompositeData, MemoryUsage> compositeDataToMemoryUsage() {
        return new Function<CompositeData, MemoryUsage>() {
            @Override public MemoryUsage apply(CompositeData input) {
                return (input == null) ? null : MemoryUsage.from(input);
            }
        };
    }

    private static AtomicBoolean initialized = new AtomicBoolean(false);

    /** Setup renderer hints for the MXBean attributes. */
    @SuppressWarnings("rawtypes")
    public static void init() {
        if (initialized.get()) return;
        synchronized (initialized) {
            if (initialized.get()) return;

            RendererHints.register(UsesJavaMXBeans.USED_HEAP_MEMORY, new RendererHints.DisplayValue(ByteSizeStrings.metric()));
            RendererHints.register(UsesJavaMXBeans.INIT_HEAP_MEMORY, new RendererHints.DisplayValue(ByteSizeStrings.metric()));
            RendererHints.register(UsesJavaMXBeans.MAX_HEAP_MEMORY, new RendererHints.DisplayValue(ByteSizeStrings.metric()));
            RendererHints.register(UsesJavaMXBeans.COMMITTED_HEAP_MEMORY, new RendererHints.DisplayValue(ByteSizeStrings.metric()));
            RendererHints.register(UsesJavaMXBeans.NON_HEAP_MEMORY_USAGE, new RendererHints.DisplayValue(ByteSizeStrings.metric()));
            RendererHints.register(UsesJavaMXBeans.TOTAL_PHYSICAL_MEMORY_SIZE, new RendererHints.DisplayValue(ByteSizeStrings.metric()));
            RendererHints.register(UsesJavaMXBeans.FREE_PHYSICAL_MEMORY_SIZE, new RendererHints.DisplayValue(ByteSizeStrings.metric()));

            RendererHints.register(UsesJavaMXBeans.START_TIME, new RendererHints.DisplayValue(Time.toDateString()));
            RendererHints.register(UsesJavaMXBeans.UP_TIME, new RendererHints.DisplayValue(Duration.millisToStringRounded()));
            RendererHints.register(UsesJavaMXBeans.PROCESS_CPU_TIME, new RendererHints.DisplayValue(Duration.millisToStringRounded()));
            RendererHints.register(UsesJavaMXBeans.PROCESS_CPU_TIME_FRACTION_LAST, new RendererHints.DisplayValue(Duration.millisToStringRounded()));
            RendererHints.register(UsesJavaMXBeans.PROCESS_CPU_TIME_FRACTION_IN_WINDOW, new RendererHints.DisplayValue(Duration.millisToStringRounded()));

            initialized.set(true);
        }
    }

    static {
        init();
    }
}
