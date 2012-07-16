package brooklyn.entity.basic;

import brooklyn.entity.Entity;
import groovy.lang.Closure;

import java.util.HashMap;
import java.util.Map;

public class ClosureEntityFactory<T extends Entity> extends AbstractConfigurableEntityFactory<T> {
    private final Closure<T> closure;

    public ClosureEntityFactory(Closure<T> closure){
        this(new HashMap(),closure);
    }

    public ClosureEntityFactory(Map flags, Closure<T> closure) {
        super(flags);
        this.closure = closure;
    }

    public T newEntity2(Map flags, Entity owner) {
        if (closure.getMaximumNumberOfParameters()>1)
            return closure.call(flags, owner);
        else {
            //leaving out the owner is discouraged
            T entity = closure.call(flags);
            if(owner!=null && entity.getOwner()==null){
                entity.setOwner(owner);
            }

            return entity;
        }
    }
}