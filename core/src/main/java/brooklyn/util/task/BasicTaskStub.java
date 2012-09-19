package brooklyn.util.task;

import brooklyn.management.TaskStub;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Objects;

public class BasicTaskStub implements TaskStub {
    private final long idCode  = Identifiers.randomLong();
    
    private transient String idCache = null;
    public String getId() {
        if (idCache!=null) return idCache;
        idCache = Identifiers.getBase64IdFromValue(idCode);
        return idCache;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(idCode);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BasicTaskStub)
            return ((BasicTaskStub)obj).idCache == idCache;
        if (obj instanceof TaskStub)
            return ((TaskStub)obj).getId().equals(getId());
        return false;
    }

    @Override
    public String toString() { return "Task["+getId()+"]"; }
    
}