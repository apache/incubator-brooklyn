package brooklyn.entity.java

import java.lang.management.ManagementFactory
import java.lang.management.MemoryUsage

import javax.management.openmbean.CompositeData

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.UsesJavaMXBeans
import brooklyn.event.adapter.JmxSensorAdapter

class JavaAppUtils {

    private static final Logger log = LoggerFactory.getLogger(SoftwareProcessEntity.class)
    
    public static void connectMXBeanSensors(EntityLocal entity, JmxSensorAdapter jmxAdapter) {
        
        if (entity.getConfig(UsesJavaMXBeans.MXBEAN_STATS_ENABLED)) {
            jmxAdapter.objectName(ManagementFactory.MEMORY_MXBEAN_NAME).with {
                attribute("HeapMemoryUsage").subscribe(UsesJavaMXBeans.USED_HEAP_MEMORY, { CompositeData m -> MemoryUsage.from(m)?.getUsed() });
                attribute("HeapMemoryUsage").subscribe(UsesJavaMXBeans.INIT_HEAP_MEMORY, { CompositeData m -> MemoryUsage.from(m)?.getInit() });
                attribute("HeapMemoryUsage").subscribe(UsesJavaMXBeans.COMMITTED_HEAP_MEMORY, { CompositeData m -> MemoryUsage.from(m)?.getCommitted() });
                attribute("HeapMemoryUsage").subscribe(UsesJavaMXBeans.MAX_HEAP_MEMORY, { CompositeData m -> MemoryUsage.from(m)?.getMax() });
                attribute("NonHeapMemoryUsage").subscribe(UsesJavaMXBeans.NON_HEAP_MEMORY_USAGE, { CompositeData m -> MemoryUsage.from(m)?.getUsed() });
            }
            
            jmxAdapter.objectName(ManagementFactory.THREAD_MXBEAN_NAME).with {
                attribute("ThreadCount").subscribe(UsesJavaMXBeans.CURRENT_THREAD_COUNT);
                attribute("PeakThreadCount").subscribe(UsesJavaMXBeans.PEAK_THREAD_COUNT);
            }
            
            jmxAdapter.objectName(ManagementFactory.RUNTIME_MXBEAN_NAME).with {
                attribute("StartTime").subscribe(UsesJavaMXBeans.START_TIME);
                attribute("UpTime").subscribe(UsesJavaMXBeans.UP_TIME);
            }
            
            jmxAdapter.objectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME).with {
                attribute("SystemLoadAverage").subscribe(UsesJavaMXBeans.SYSTEM_LOAD_AVERAGE);
                attribute("AvailableProcessors").subscribe(UsesJavaMXBeans.AVAILABLE_PROCESSORS);
                attribute("TotalPhysicalMemorySize").subscribe(UsesJavaMXBeans.TOTAL_PHYSICAL_MEMORY_SIZE);
                attribute("FreePhysicalMemorySize").subscribe(UsesJavaMXBeans.FREE_PHYSICAL_MEMORY_SIZE);
            }

            
            //FIXME: need a new type of adapter that maps multiple objectNames to a mapping
//            jmxAdapter.objectName(ManagementFactory.GARBAGE_COLLECTOR_MXBEAN_DOMAIN_TYPE + ",*").with {
//                attribute("SystemLoadAverage").subscribe(UsesJavaMXBeans.GARBAGE_COLLECTION_TIME, { def m -> log.info("XXXXXXX $m") });
//            }
        }
    }
}
