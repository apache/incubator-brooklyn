package brooklyn.entity.java;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.concurrent.TimeUnit;

import javax.management.openmbean.CompositeData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.RollingTimeWindowMeanEnricher;
import brooklyn.enricher.TimeFractionDeltaEnricher;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;

public class JavaAppUtils {

    private static final Logger log = LoggerFactory.getLogger(JavaAppUtils.class);
    
    @SuppressWarnings("unchecked")
    public static JmxFeed connectMXBeanSensors(EntityLocal entity) {
        return connectMXBeanSensors(entity, Duration.FIVE_SECONDS);
    }
    
    public static JmxFeed connectMXBeanSensors(EntityLocal entity, long jmxPollPeriodMs) {
        return connectMXBeanSensors(entity, Duration.millis(jmxPollPeriodMs));
    }
    
    public static JmxFeed connectMXBeanSensors(EntityLocal entity, Duration jmxPollPeriod) {
        if (Boolean.TRUE.equals(entity.getConfig(UsesJavaMXBeans.MXBEAN_STATS_ENABLED))) {
            // TODO Could we reuse the result of compositeDataToMemoryUsage?
            
            JmxFeed jmxFeed = JmxFeed.builder()
                    .entity(entity)
                    .period(jmxPollPeriod)
                    
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
                            .onSuccess((Function)times(0.001*0.001)))   // nanos to millis
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
                            .attributeName("FreePhysicalMemorySize"))
                            
                    .build();
        
            //FIXME: need a new type of adapter that maps multiple objectNames to a mapping
//            jmxAdapter.objectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*").with {
//                attribute("SystemLoadAverage").subscribe(UsesJavaMXBeans.GARBAGE_COLLECTION_TIME, { def m -> log.info("XXXXXXX $m") });
//            }
            
            return jmxFeed;
            
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
    
    private static Function<Number,Double> times(final double x) {
        return new Function<Number,Double>() {
            @Override public Double apply(Number input) {
                if (input==null) return null;
                return x*input.doubleValue();
            }
        };
    }
        
    private static Function<CompositeData, MemoryUsage> compositeDataToMemoryUsage() {
        return new Function<CompositeData, MemoryUsage>() {
            @Override public MemoryUsage apply(CompositeData input) {
                return (input == null) ? null : MemoryUsage.from(input);
            }
            
        };
    }
}
