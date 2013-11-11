package brooklyn.location.basic;

import brooklyn.location.MachineManagementMixins.MachineMetadata;

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
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((isRunning == null) ? 0 : isRunning.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((originalMetadata == null) ? 0 : originalMetadata.hashCode());
        result = prime * result + ((primaryIp == null) ? 0 : primaryIp.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BasicMachineMetadata other = (BasicMachineMetadata) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (isRunning == null) {
            if (other.isRunning != null)
                return false;
        } else if (!isRunning.equals(other.isRunning))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (originalMetadata == null) {
            if (other.originalMetadata != null)
                return false;
        } else if (!originalMetadata.equals(other.originalMetadata))
            return false;
        if (primaryIp == null) {
            if (other.primaryIp != null)
                return false;
        } else if (!primaryIp.equals(other.primaryIp))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "MachineMetadata[id=" + id + ", name=" + name + ", primaryIp=" + primaryIp + ", isRunning=" + isRunning + ", originalMetadata=" + originalMetadata + "]";
    }

    
}
