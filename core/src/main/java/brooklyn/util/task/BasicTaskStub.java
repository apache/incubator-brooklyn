package brooklyn.util.task;

import brooklyn.management.TaskStub;
import brooklyn.util.internal.LanguageUtils;

import com.google.common.base.Objects;

public class BasicTaskStub implements TaskStub {
    final String id = LanguageUtils.newUid();

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public boolean equals(Object obj) {
        return LanguageUtils.equals(this, obj, "id");
    }

    @Override
    public String toString() { return "Task["+id+"]"; }
    
    @Override
    public String getId() {
        return id;
    }
}