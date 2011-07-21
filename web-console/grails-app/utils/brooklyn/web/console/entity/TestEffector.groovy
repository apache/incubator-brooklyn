package brooklyn.web.console.entity

import javax.naming.OperationNotSupportedException

import brooklyn.entity.Entity
import brooklyn.entity.ParameterType
import brooklyn.entity.basic.AbstractEffector

// TODO remove this test class as soon as the group agrees it's unnecessary!
public class TestEffector extends AbstractEffector{

    TestEffector(String name, String description, List<ParameterType<?>> parameters){
        super(name, Void.class, parameters, description)
    }

    @Override
    Object call(Entity entity, Map parameters) {
        throw new OperationNotSupportedException("Please refrain from pressing that button")
    }
}
