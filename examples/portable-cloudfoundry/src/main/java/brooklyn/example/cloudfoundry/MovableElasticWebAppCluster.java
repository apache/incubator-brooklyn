package brooklyn.example.cloudfoundry;

import java.util.Collection;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.NamedParameter;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(MovableElasticWebAppClusterImpl.class)
public interface MovableElasticWebAppCluster extends Entity, Startable, MovableEntityTrait {

    // this advertises that this config key is easily available on this entity,
    // either by passing (war: "classpath://...") in the constructor or by setConfig(ROOT_WAR).
    // as a config variable, it will be inherited by children, so the children web app entities will pick it up.
    @SetFromFlag("war")
    public static final BasicConfigKey<String> ROOT_WAR = JavaWebAppService.ROOT_WAR;
    
    public static final BasicAttributeSensor<String> PRIMARY_SVC_ENTITY_ID = new BasicAttributeSensor<String>(
            String.class, "movable.primary.id", "Entity ID of primary web-app service");
    
    public static final BasicAttributeSensor<Collection<String>> SECONDARY_SVC_ENTITY_IDS = new BasicAttributeSensor( 
            Collection.class, "movable.secondary.ids", "Entity IDs of secondary web-app services");
    
    public static final Effector<String> CREATE_SECONDARY_IN_LOCATION = new MethodEffector<String>(MovableElasticWebAppCluster.class, "createSecondaryInLocation");
    public static final Effector<String> PROMOTE_SECONDARY = new MethodEffector<String>(MovableElasticWebAppCluster.class, "promoteSecondary");
    public static final Effector<String> DESTROY_SECONDARY = new MethodEffector<String>(MovableElasticWebAppCluster.class, "destroySecondary");
    
    /** creates a new secondary instance, in the given location, returning the ID of the secondary created and started */
    @Description("create a new secondary instance in the given location")
    public String createSecondaryInLocation(
            @NamedParameter("location") @Description("the location where to start the secondary") String l);

    /** promotes the indicated secondary,
     * returning the ID of the former-primary which has been demoted */
    @Description("promote the indicated secondary to primary (demoting the existing primary)")
    public String promoteSecondary(
            @NamedParameter("idOfSecondaryToPromote") @Description("ID of secondary entity to promote") 
            String idOfSecondaryToPromote);
    
    /** destroys the indicated secondary */
    @Description("destroy the indicated secondary")
    public void destroySecondary(
            @NamedParameter("idOfSecondaryToDestroy") @Description("ID of secondary entity to destroy")
            String idOfSecondaryToDestroy);
}
