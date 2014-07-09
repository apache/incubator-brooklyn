package brooklyn.entity.effector;

import java.util.List;

import brooklyn.entity.Effector;
import brooklyn.entity.ParameterType;
import brooklyn.entity.effector.EffectorTasks.EffectorTaskFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;

@Beta // added in 0.6.0
public class EffectorAndBody<T> extends EffectorBase<T> implements EffectorWithBody<T> {

    private static final long serialVersionUID = -6023389678748222968L;
    private final EffectorTaskFactory<T> body;

    public EffectorAndBody(Effector<T> original, EffectorTaskFactory<T> body) {
        this(original.getName(), original.getReturnType(), original.getParameters(), original.getDescription(), body);
    }
    
    public EffectorAndBody(String name, Class<T> returnType, List<ParameterType<?>> parameters, String description, EffectorTaskFactory<T> body) {
        super(name, returnType, parameters, description);
        this.body = body;
    }

    @Override
    public EffectorTaskFactory<T> getBody() {
        return body;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), getBody());
    }
    
    @Override
    public boolean equals(Object other) {
        return super.equals(other) && Objects.equal(getBody(), ((EffectorAndBody<?>)other).getBody());
    }

}
