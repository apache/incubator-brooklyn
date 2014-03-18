package brooklyn.location.basic;

import javax.annotation.concurrent.Immutable;

import com.google.common.base.Objects;

import brooklyn.location.HardwareDetails;

@Immutable
public class BasicHardwareDetails implements HardwareDetails {

    private final Integer cpuCount;
    private final Integer ram;

    public BasicHardwareDetails(Integer cpuCount, Integer ram) {
        this.cpuCount = cpuCount;
        this.ram = ram;
    }

    @Override
    public Integer getCpuCount() {
        return cpuCount;
    }

    @Override
    public Integer getRam() {
        return ram;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper("hardware")
                .omitNullValues()
                .add("cpuCount", cpuCount)
                .add("ram", ram)
                .toString();
    }
}
