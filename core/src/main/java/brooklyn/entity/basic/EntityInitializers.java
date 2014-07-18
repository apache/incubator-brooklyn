package brooklyn.entity.basic;

import java.util.List;

import brooklyn.entity.proxying.EntityInitializer;

import com.google.common.collect.ImmutableList;

public class EntityInitializers {

    public static class AddTags implements EntityInitializer {
        public final List<Object> tags;
        
        public AddTags(Object... tags) {
            this.tags = ImmutableList.copyOf(tags);
        }
        
        @Override
        public void apply(EntityLocal entity) {
            for (Object tag: tags)
                entity.addTag(tag);
        }
    }

    
    public static EntityInitializer addingTags(Object... tags) {
        return new AddTags(tags);
    }
    
}
