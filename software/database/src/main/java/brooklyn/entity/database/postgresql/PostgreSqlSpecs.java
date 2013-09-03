package brooklyn.entity.database.postgresql;

import brooklyn.entity.chef.ChefConfig;
import brooklyn.entity.chef.ChefConfig.ChefModes;
import brooklyn.entity.proxying.EntitySpec;

/** utiltiy for creating specs for PostgreSql instances */
public class PostgreSqlSpecs {

    private PostgreSqlSpecs() {}
    
    public static EntitySpec<PostgreSqlNode> spec() {
        return EntitySpec.create(PostgreSqlNode.class);
    }
    
    /** requires knife */
    public static EntitySpec<PostgreSqlNode> specChef() {
        EntitySpec<PostgreSqlNode> spec = EntitySpec.create(PostgreSqlNode.class, PostgreSqlNodeChefImpl.class);
        spec.configure(ChefConfig.CHEF_MODE, ChefModes.KNIFE);
        return spec;
    }

}
