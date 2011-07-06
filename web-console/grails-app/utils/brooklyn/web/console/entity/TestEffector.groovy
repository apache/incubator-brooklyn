package brooklyn.web.console.entity

import brooklyn.entity.Entity
import brooklyn.entity.ParameterType
import brooklyn.entity.basic.AbstractEffector
import javax.naming.OperationNotSupportedException

public class TestEffector extends AbstractEffector{

    TestEffector(String name, String description, List<ParameterType<?>> parameters){
        super(name, Void.class, parameters, description)
    }

    @Override
    Object call(Entity entity, Map parameters) {
        throw new OperationNotSupportedException("Please refrain from pressing that button")
    }
}
