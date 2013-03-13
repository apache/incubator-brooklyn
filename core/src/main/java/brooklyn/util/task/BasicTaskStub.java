package brooklyn.util.task;

import brooklyn.management.TaskStub;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Objects;

public class BasicTaskStub implements TaskStub {
    private String id = Identifiers.makeRandomId(8);
    public String getId() {
        return id;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TaskStub)
            return ((TaskStub)obj).getId().equals(getId());
        return false;
    }

    @Override
    public String toString() { return "Task["+getId()+"]"; }
    
}