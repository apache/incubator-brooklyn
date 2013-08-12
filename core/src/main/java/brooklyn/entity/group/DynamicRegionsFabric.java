package brooklyn.entity.group;

import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.NamedParameter;
import brooklyn.entity.group.DynamicFabric;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(DynamicRegionsFabricImpl.class)
public interface DynamicRegionsFabric extends DynamicFabric {

    MethodEffector<String> ADD_REGION = new MethodEffector<String>(DynamicRegionsFabric.class, "addRegion");
    MethodEffector<String> REMOVE_REGION = new MethodEffector<String>(DynamicRegionsFabric.class, "removeRegion");

    @Description("Extends the fabric with a new instance of the fabric's underlying blueprint in a new region, "+
            "returning the id of the new entity")
    public String addRegion(
            @NamedParameter("location") 
            @Description("Location spec string (e.g. aws-ec2:us-west-1 - and note you may have to surround this with double quotes)") 
            String location);

    @Description("Stops and removes a region")
    public void removeRegion(
            @NamedParameter("id") 
            @Description("ID of the child entity to stop and remove (note you may have to surround this with double quotes)") 
            String id);

}
