package brooklyn.location;

import javax.annotation.Nullable;

/**
 * @since 0.7.0
 */
public interface HardwareDetails {

    /**
     * The number of CPUs on the machine
     */
    @Nullable
    Integer getCpuCount();

    /**
     * Amount of RAM in kilobytes
     */
    @Nullable
    Integer getRam();

}
