package brooklyn.entity.proxying;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.NamedParameter;
import brooklyn.entity.proxying.MyEntity.MyEntityImpl;

@ImplementedBy(MyEntityImpl.class)
public interface MyEntity extends Entity {
    public static class Spec<T extends MyEntity> extends BasicEntitySpec<T> {
        public static Spec<MyEntity> newInstance() {
            return new Spec<MyEntity>(MyEntity.class);
        }
        protected Spec(Class<T> type) {
            super(type);
        }
    }
    
    Effector<String> MY_EFFECTOR = new MethodEffector<String>(MyEntity.class, "myeffector");

    Effector<Entity> CREATE_CHILD_EFFECTOR = new MethodEffector<Entity>(MyEntity.class, "createChild");

    @Description("My description")
    public String myeffector(@NamedParameter("in") String in);
    
    @Description("My description2")
    public Entity createChild();

    public static class MyEntityImpl extends AbstractEntity implements MyEntity {
        public MyEntityImpl() {
            super();
        }
    
        @Override
        public String myeffector(String in) {
            return in+"-out";
        }
        
        @Override
        public Entity createChild() {
            MyEntity child = getManagementContext().getEntityManager().createEntity(MyEntity.Spec.newInstance().parent(this));
            getManagementContext().getEntityManager().manage(child);
            return child;
        }
    }
}
