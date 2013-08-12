package brooklyn.entity.group;

import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(DynamicRegionsFabricImpl.class)
public interface DynamicRegionsFabric extends DynamicFabric {

    MethodEffector<String> ADD_REGION = new MethodEffector<String>(DynamicRegionsFabric.class, "addRegion");
    MethodEffector<String> REMOVE_REGION = new MethodEffector<String>(DynamicRegionsFabric.class, "removeRegion");

    @Effector(description="Extends the fabric with a new instance of the fabric's underlying blueprint in a new region, "+
            "returning the id of the new entity")
    public String addRegion(
            @EffectorParam(name="location", description="Location spec string "
                    + "(e.g. aws-ec2:us-west-1 - and note you may have to surround this with double quotes)") String location);

    @Effector(description="Stops and removes a region")
    public void removeRegion(
            @EffectorParam(name="id", description="ID of the child entity to stop and remove "
                    + "(note you may have to surround this with double quotes)") String id);
}
