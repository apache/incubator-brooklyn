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

    public T newEntity2(Map flags, Entity parent) {
        if (closure.getMaximumNumberOfParameters()>1)
            return closure.call(flags, parent);
        else {
            //leaving out the parent is discouraged
            T entity = closure.call(flags);
            if(parent!=null && entity.getParent()==null){
                entity.setParent(parent);
            }

            return entity;
        }
    }
}