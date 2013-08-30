package brooklyn.entity.chef;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.MapConfigKey.MapModifications;
import brooklyn.event.basic.SetConfigKey.SetModifications;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/** Conveniences for configuring brooklyn Chef entities 
 * @since 0.6.0 */
@Beta
public class ChefConfigs {

    public static void addToRunList(EntitySpec<?> entity, String ...recipes) {
        for (String recipe: recipes)
            entity.configure(ChefSoloDriver.CHEF_RUN_LIST, SetModifications.addItem(recipe));
    }

    public static void addToRunList(EntityInternal entity, String ...recipes) {
        for (String recipe: recipes)
            entity.setConfig(ChefSoloDriver.CHEF_RUN_LIST, SetModifications.addItem(recipe));
    }

    public static void addToCookbooksFromGithub(EntitySpec<?> entity, String ...cookbookNames) {
        for (String cookbookName: cookbookNames)
            addToCookbooksFromGithub(entity, cookbookName, getGithubOpscodeRepo(cookbookName)); 
    }
    
    public static void addToCookbooksFromGithub(EntityInternal entity, String ...cookbookNames) {
        for (String cookbookName: cookbookNames)
            addToCookbooksFromGithub(entity, cookbookName, getGithubOpscodeRepo(cookbookName)); 
    }

    public static String getGithubOpscodeRepo(String cookbookName) {
        return "https://github.com/opscode-cookbooks/"+cookbookName+"/archive/master.tar.gz";
    }
    
    public static void addToCookbooksFromGithub(EntitySpec<?> entity, String cookbookName, String cookbookUrl) {
        entity.configure(ChefSoloDriver.CHEF_COOKBOOKS.subKey(cookbookName), cookbookUrl);
    }

    public static void addToCookbooksFromGithub(EntityInternal entity, String cookbookName, String cookbookUrl) {
        entity.setConfig(ChefSoloDriver.CHEF_COOKBOOKS.subKey(cookbookName), cookbookUrl);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void addLaunchAttributes(EntitySpec<?> entity, Map<? extends Object,? extends Object> attributesMap) {
        entity.configure(ChefSoloDriver.CHEF_LAUNCH_ATTRIBUTES, MapModifications.add((Map)attributesMap));
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void addLaunchAttributes(EntityInternal entity, Map<? extends Object,? extends Object> attributesMap) {
        entity.setConfig(ChefSoloDriver.CHEF_LAUNCH_ATTRIBUTES, MapModifications.add((Map)attributesMap));
    }
    
    /** replaces the attributes underneath the rootAttribute parameter with the given value;
     * see {@link #addLaunchAttributesMap(EntitySpec, Map)} for richer functionality */
    public static void setLaunchAttribute(EntitySpec<?> entity, String rootAttribute, Object value) {
        entity.configure(ChefSoloDriver.CHEF_LAUNCH_ATTRIBUTES.subKey(rootAttribute), value);
    }
    
    /** replaces the attributes underneath the rootAttribute parameter with the given value;
     * see {@link #addLaunchAttributesMap(EntitySpec, Map)} for richer functionality */
    public static void setLaunchAttribute(EntityInternal entity, String rootAttribute, Object value) {
        entity.setConfig(ChefSoloDriver.CHEF_LAUNCH_ATTRIBUTES.subKey(rootAttribute), value);
    }

    public static <T> T getRequiredConfig(Entity entity, ConfigKey<T> key) {
        return Preconditions.checkNotNull(
                Preconditions.checkNotNull(entity, "Entity must be supplied").getConfig(key), 
                "Key "+key+" is required on "+entity);
    }

}
