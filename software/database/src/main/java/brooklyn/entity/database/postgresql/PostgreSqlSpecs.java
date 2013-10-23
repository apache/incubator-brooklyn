package brooklyn.entity.database.postgresql;

import brooklyn.entity.chef.ChefConfig;
import brooklyn.entity.chef.ChefConfig.ChefModes;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.salt.SaltConfig;

/**
 * Utiltiy for creating specs for {@link PostgreSqlNode} instances.
 */
public class PostgreSqlSpecs {

    private PostgreSqlSpecs() {}

    public static EntitySpec<PostgreSqlNode> spec() {
        return EntitySpec.create(PostgreSqlNode.class);
    }

    /** Requires {@code knife}. */
    public static EntitySpec<PostgreSqlNode> specChef() {
        EntitySpec<PostgreSqlNode> spec = EntitySpec.create(PostgreSqlNode.class, PostgreSqlNodeChefImplFromScratch.class);
        spec.configure(ChefConfig.CHEF_MODE, ChefModes.KNIFE);
        return spec;
    }

    /** Uses Salt masterless mode. */
    public static EntitySpec<PostgreSqlNode> specSalt() {
        EntitySpec<PostgreSqlNode> spec = EntitySpec.create(PostgreSqlNode.class, PostgreSqlNodeSaltImpl.class);
        spec.configure(SaltConfig.MASTERLESS_MODE, true);
        return spec;
    }
}
