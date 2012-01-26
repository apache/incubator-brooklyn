package brooklyn.policy.loadbalancing

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener


public class BalanceableWorkerPool extends AbstractEntity {
    private static final Logger logger = LoggerFactory.getLogger(BalanceableWorkerPool)
    
    // TODO: too-hot / too-cold sensors?
    
    private Group containerGroup
    private Group itemGroup
    
    
    public BalanceableWorkerPool(Map properties = [:], Entity owner = null) {
        super(properties, owner)
    }
    
    public void setContents(Group containerGroup, Group itemGroup) {
        this.containerGroup = containerGroup
        this.itemGroup = itemGroup
    }
    
    public Group getContainerGroup() { return containerGroup }
    public Group getItemGroup() { return itemGroup }
    
}
