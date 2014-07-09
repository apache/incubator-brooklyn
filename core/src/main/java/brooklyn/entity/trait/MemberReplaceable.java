package brooklyn.entity.trait;

import java.util.NoSuchElementException;

import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.group.StopFailedRuntimeException;

public interface MemberReplaceable {

    MethodEffector<String> REPLACE_MEMBER = new MethodEffector<String>(MemberReplaceable.class, "replaceMember");

    /**
     * Replaces the entity with the given ID, if it is a member.
     * <p>
     * First adds a new member, then removes this one. 
     *
     * @param memberId entity id of a member to be replaced
     * @return the id of the new entity
     * @throws NoSuchElementException If entity cannot be resolved, or it is not a member
     * @throws StopFailedRuntimeException If stop failed, after successfully starting replacement
     */
    @Effector(description="Replaces the entity with the given ID, if it is a member; first adds a new member, then removes this one. "+
            "Returns id of the new entity; or throws exception if couldn't be replaced.")
    String replaceMember(@EffectorParam(name="memberId", description="The entity id of a member to be replaced") String memberId);
}
