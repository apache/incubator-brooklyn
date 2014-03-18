package brooklyn.location;

import javax.annotation.Nonnull;

/**
 * @since 0.7.0
 */
public interface MachineDetails {

    @Nonnull
    HardwareDetails getHardwareDetails();

    @Nonnull
    OsDetails getOsDetails();

}
