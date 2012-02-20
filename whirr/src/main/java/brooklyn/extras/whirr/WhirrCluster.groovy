package brooklyn.extras.whirr

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;

public class WhirrCluster extends AbstractEntity {

    Map whirrProperties = [:]
    
    public WhirrCluster(Map flags=[:], Entity owner=null) {
        super(flags, owner)
    }

    public void start() {
//        #whirr.provider=aws-ec2
//        #whirr.identity=${env:AWS_ACCESS_KEY_ID}
//        #whirr.credential=${env:AWS_SECRET_ACCESS_KEY}
    }

}
