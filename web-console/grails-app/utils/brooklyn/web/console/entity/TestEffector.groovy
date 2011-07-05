package brooklyn.web.console.entity

import brooklyn.entity.ParameterType
import brooklyn.entity.basic.AbstractEffector
import brooklyn.entity.Entity

public class TestEffector extends AbstractEffector{

    TestEffector(String name, String description, List<ParameterType<?>> parameters){
        super(name, Void.class, parameters, description)
    }

    @Override
    Object call(Entity entity, Map parameters) {
        return null  //To change body of implemented methods use File | Settings | File Templates.
    }
}
