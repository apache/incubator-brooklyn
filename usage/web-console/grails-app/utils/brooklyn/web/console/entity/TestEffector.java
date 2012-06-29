package brooklyn.web.console.entity;

import java.util.List;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.AbstractEffector;

// TODO remove this test class as soon as the group agrees it's unnecessary!
public class TestEffector extends AbstractEffector<Void> {

    public TestEffector(String name, String description, List<ParameterType<?>> parameters){
        super(name, Void.class, parameters, description);
    }

    @Override
    public Void call(Entity entity, Map parameters) {
        throw new UnsupportedOperationException("Please refrain from pressing that button");
    }
}
