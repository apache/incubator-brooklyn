package brooklyn.entity.java;

import java.util.Map;

import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface UsesJavaMXBeans {

    @SetFromFlag("mxbeanStatsEnabled")
    public static final BasicConfigKey<Boolean> MXBEAN_STATS_ENABLED =
            new BasicConfigKey<Boolean>(Boolean.class, "java.metrics.mxbeanStatsEnabled", "Enables collection of JVM stats from the MXBeans, such as memory and thread usage (default is true)", true);

    public static final BasicAttributeSensor<Long> USED_HEAP_MEMORY =
            new BasicAttributeSensor<Long>(Long.class, "java.metrics.heap.used", "current heap size in bytes");
    public static final BasicAttributeSensor<Long> INIT_HEAP_MEMORY =
            new BasicAttributeSensor<Long>(Long.class, "java.metrics.heap.init", "initial heap size in bytes");
    public static final BasicAttributeSensor<Long> COMMITTED_HEAP_MEMORY =
            new BasicAttributeSensor<Long>(Long.class, "java.metrics.heap.committed", "commited heap size in bytes");
    public static final BasicAttributeSensor<Long> MAX_HEAP_MEMORY =
            new BasicAttributeSensor<Long>(Long.class, "java.metrics.heap.max", "max heap size in bytes");
    public static final BasicAttributeSensor<Long> NON_HEAP_MEMORY_USAGE =
            new BasicAttributeSensor<Long>(Long.class, "java.metrics.nonheap.used", "current non-heap size in bytes");
    public static final BasicAttributeSensor<Integer> CURRENT_THREAD_COUNT =
            new BasicAttributeSensor<Integer>(Integer.class, "java.metrics.threads.current", "current number of threads");
    public static final BasicAttributeSensor<Integer> PEAK_THREAD_COUNT =
            new BasicAttributeSensor<Integer>(Integer.class, "java.metrics.threads.max", "peak number of threads");

    // runtime system attributes
    public static final BasicAttributeSensor<Long> START_TIME =
            new BasicAttributeSensor<Long>(Long.class, "java.metrics.starttime", "start time");
    public static final BasicAttributeSensor<Long> UP_TIME =
            new BasicAttributeSensor<Long>(Long.class, "java.metrics.uptime", "the uptime");
    
    //operating system attributes
    public static final BasicAttributeSensor<Long> PROCESS_CPU_TIME =
            new BasicAttributeSensor<Long>(Long.class, "java.metrics.processCpuTime", "the process cpu time (in 100 nanosecond blocks; use the difference between samples)");
    public static final BasicAttributeSensor<Double> PROCESS_CPU_TIME_FRACTION =
            new BasicAttributeSensor<Double>(Double.class, "java.metrics.processCpuTime.fraction", "the fraction of time (since the last event) consumed as cpu time");

    public static final Integer AVG_PROCESS_CPU_TIME_FRACTION_PERIOD = 10 * 1000;
    public static final BasicAttributeSensor<Double> AVG_PROCESS_CPU_TIME_FRACTION = new BasicAttributeSensor<Double>(
            Double.class, String.format("java.metrics.processCpuTime.fraction.avg.%s", AVG_PROCESS_CPU_TIME_FRACTION_PERIOD),
            String.format("Average Reqs/Sec (over the last %sms)", AVG_PROCESS_CPU_TIME_FRACTION_PERIOD));

    public static final BasicAttributeSensor<Integer> AVAILABLE_PROCESSORS =
            new BasicAttributeSensor<Integer>(Integer.class, "java.metrics.processors.available", "number of processors available to the Java virtual machine");
    public static final BasicAttributeSensor<Double> SYSTEM_LOAD_AVERAGE
            = new BasicAttributeSensor<Double>(Double.class, "java.metrics.systemload.average", "average system load");
    public static final BasicAttributeSensor<Long> TOTAL_PHYSICAL_MEMORY_SIZE =
            new BasicAttributeSensor<Long>(Long.class, "java.metrics.physicalmemory.total", "The physical memory available to the operating system");
    public static final BasicAttributeSensor<Long> FREE_PHYSICAL_MEMORY_SIZE =
            new BasicAttributeSensor<Long>(Long.class, "java.metrics.physicalmemory.free", "The free memory available to the operating system");

    // GC attributes
    public static final BasicAttributeSensor<Map> GARBAGE_COLLECTION_TIME = new BasicAttributeSensor<Map>(Map.class, "java.metrics.gc.time", "garbage collection time");

}