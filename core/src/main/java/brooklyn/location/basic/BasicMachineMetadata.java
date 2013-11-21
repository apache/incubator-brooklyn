package brooklyn.location.basic;

import brooklyn.location.MachineManagementMixins.MachineMetadata;

import com.google.common.base.Objects;

public class BasicMachineMetadata implements MachineMetadata {

    final String id, name, primaryIp;
    final Boolean isRunning;
    final Object originalMetadata;
    
    public BasicMachineMetadata(String id, String name, String primaryIp, Boolean isRunning, Object originalMetadata) {
        super();
        this.id = id;
        this.name = name;
        this.primaryIp = primaryIp;
        this.isRunning = isRunning;
        this.originalMetadata = originalMetadata;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPrimaryIp() {
        return primaryIp;
    }

    public Boolean isRunning() {
        return isRunning;
    }

    public Object getOriginalMetadata() {
        return originalMetadata;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id, isRunning, name, originalMetadata, primaryIp);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        BasicMachineMetadata other = (BasicMachineMetadata) obj;
        if (!Objects.equal(id, other.id)) return false;
        if (!Objects.equal(name, other.name)) return false;
        if (!Objects.equal(primaryIp, other.primaryIp)) return false;
        if (!Objects.equal(isRunning, other.isRunning)) return false;
        if (!Objects.equal(originalMetadata, other.originalMetadata)) return false;
        return true;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("id", id).add("name", name).add("originalMetadata", originalMetadata).toString();
    }
    
}
