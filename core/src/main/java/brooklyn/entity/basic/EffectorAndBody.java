package brooklyn.entity.basic;

import java.util.List;

import com.google.common.annotations.Beta;

import brooklyn.entity.Effector;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.EffectorTasks.EffectorTaskFactory;

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
    
}
