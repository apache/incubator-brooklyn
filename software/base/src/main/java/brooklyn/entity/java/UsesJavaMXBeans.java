package brooklyn.entity.java;

import java.util.Map;

import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor.DoubleAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor.IntegerAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor.LongAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface UsesJavaMXBeans {

    @SetFromFlag("mxbeanStatsEnabled")
    public static final BasicConfigKey<Boolean> MXBEAN_STATS_ENABLED =
            new BasicConfigKey<Boolean>(Boolean.class, "java.metrics.mxbeanStatsEnabled", "Enables collection of JVM stats from the MXBeans, such as memory and thread usage (default is true)", true);

    public static final AttributeSensor<Long> USED_HEAP_MEMORY =
            new LongAttributeSensor("java.metrics.heap.used", "Current heap size (bytes)");
    public static final AttributeSensor<Long> INIT_HEAP_MEMORY =
            new LongAttributeSensor("java.metrics.heap.init", "Initial heap size (bytes)");
    public static final AttributeSensor<Long> COMMITTED_HEAP_MEMORY =
            new LongAttributeSensor("java.metrics.heap.committed", "Commited heap size (bytes)");
    public static final AttributeSensor<Long> MAX_HEAP_MEMORY =
            new LongAttributeSensor("java.metrics.heap.max", "Max heap size (bytes)");
    public static final AttributeSensor<Long> NON_HEAP_MEMORY_USAGE =
            new LongAttributeSensor("java.metrics.nonheap.used", "Current non-heap size (bytes)");
    public static final AttributeSensor<Integer> CURRENT_THREAD_COUNT =
            new IntegerAttributeSensor( "java.metrics.threads.current", "Current number of threads");
    public static final AttributeSensor<Integer> PEAK_THREAD_COUNT =
            new IntegerAttributeSensor("java.metrics.threads.max", "Peak number of threads");

    // runtime system attributes
    public static final AttributeSensor<Long> START_TIME =
            new LongAttributeSensor("java.metrics.starttime", "Start time of Java process (UTC)");
    public static final AttributeSensor<Long> UP_TIME =
            new LongAttributeSensor("java.metrics.uptime", "Uptime of Java process (millis, elapsed since start)");
    
    //operating system attributes
    public static final AttributeSensor<Double> PROCESS_CPU_TIME = new DoubleAttributeSensor( 
            "java.metrics.processCpuTime.total", "Process CPU time (total millis since start)");
    public static final AttributeSensor<Double> PROCESS_CPU_TIME_FRACTION_LAST = new DoubleAttributeSensor( 
            "java.metrics.processCpuTime.fraction.last", "Fraction of CPU time used, reported by JVM (percentage, last datapoint)");
    public static final AttributeSensor<Double> PROCESS_CPU_TIME_FRACTION_IN_WINDOW = new DoubleAttributeSensor( 
            "java.metrics.processCpuTime.fraction.windowed", "Fraction of CPU time used, reported by JVM (percentage, over time window)");
    
    /** @deprecated since 0.6.0, use {@link #PROCESS_CPU_TIME_FRACTION_LAST} */
    public static final AttributeSensor<Double> PROCESS_CPU_TIME_FRACTION = PROCESS_CPU_TIME_FRACTION_LAST;
    
    /** @deprecated since 0.6.0 callers configure with
     *  {@link JavaAppUtils#connectJavaAppServerPolicies(brooklyn.entity.basic.EntityLocal)} */
    public static final Integer AVG_PROCESS_CPU_TIME_FRACTION_PERIOD = 10 * 1000;
    
    /** @deprecated since 0.6.0 use {@link #PROCESS_CPU_TIME_FRACTION_IN_WINDOW} */
    public static final AttributeSensor<Double> AVG_PROCESS_CPU_TIME_FRACTION = PROCESS_CPU_TIME_FRACTION_IN_WINDOW; 

    public static final AttributeSensor<Integer> AVAILABLE_PROCESSORS =
            new IntegerAttributeSensor("java.metrics.processors.available", "number of processors available to the Java virtual machine");
    public static final AttributeSensor<Double> SYSTEM_LOAD_AVERAGE
            = new DoubleAttributeSensor("java.metrics.systemload.average", "average system load");
    public static final AttributeSensor<Long> TOTAL_PHYSICAL_MEMORY_SIZE =
            new LongAttributeSensor("java.metrics.physicalmemory.total", "The physical memory available to the operating system");
    public static final AttributeSensor<Long> FREE_PHYSICAL_MEMORY_SIZE =
            new LongAttributeSensor("java.metrics.physicalmemory.free", "The free memory available to the operating system");

    // GC attributes
    public static final AttributeSensor<Map> GARBAGE_COLLECTION_TIME = new BasicAttributeSensor<Map>(Map.class, "java.metrics.gc.time", "garbage collection time");

}