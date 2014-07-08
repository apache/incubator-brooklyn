/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.basic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.collections.SetFromLiveMap;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;


/**
 * Represents a group of entities - sub-classes can support dynamically changing membership,
 * ad hoc groupings, etc.
 * <p>
 * Synchronization model. When changing and reading the group membership, this class uses internal
 * synchronization to ensure atomic operations and the "happens-before" relationship for reads/updates
 * from different threads. Sub-classes should not use this same synchronization mutex when doing
 * expensive operations - e.g. if resizing a cluster, don't block everyone else from asking for the
 * current number of members.
 */
public abstract class AbstractGroupImpl extends AbstractEntity implements AbstractGroup {
    private static final Logger log = LoggerFactory.getLogger(AbstractGroup.class);

    private Set<Entity> members = Sets.newLinkedHashSet();

    public AbstractGroupImpl() {
    }

    @Deprecated
    public AbstractGroupImpl(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public void setManagementContext(ManagementContextInternal managementContext) {
        super.setManagementContext(managementContext);

        Set<Entity> oldMembers = members;

        members = SetFromLiveMap.create(managementContext.getStorage().<Entity,Boolean>getMap(getId()+"-members"));

        // Only override stored defaults if we have actual values. We might be in setManagementContext
        // because we are reconstituting an existing entity in a new brooklyn management-node (in which
        // case believe what is already in the storage), or we might be in the middle of creating a new
        // entity. Normally for a new entity (using EntitySpec creation approach), this will get called
        // before setting the parent etc. However, for backwards compatibility we still support some
        // things calling the entity's constructor directly.
        if (oldMembers.size() > 0) members.addAll(oldMembers);
    }

    @Override
    public void init() {
        super.init();
        setAttribute(GROUP_SIZE, 0);
        setAttribute(GROUP_MEMBERS, ImmutableList.<Entity>of());
    }

    /**
     * Adds the given entity as a member of this group <em>and</em> this group as one of the groups of the child
     */
    @Override
    public boolean addMember(Entity member) {
        synchronized (members) {
            if (Entities.isNoLongerManaged(member)) {
                // Don't add dead entities, as they could never be removed (because addMember could be called in
                // concurrent thread as removeMember triggered by the unmanage).
                // Not using Entities.isManaged here, as could be called in entity.init()
                log.debug("Group {} ignoring new member {}, because it is no longer managed", this, member);
                return false;
            }
            member.addGroup((Group)getProxyIfAvailable());
            boolean changed = members.add(member);
            if (changed) {
                log.debug("Group {} got new member {}", this, member);
                emit(MEMBER_ADDED, member);
                setAttribute(GROUP_SIZE, getCurrentSize());
                setAttribute(GROUP_MEMBERS, getMembers());

                if (Boolean.TRUE.equals(getConfig(MEMBER_DELEGATE_CHILDREN))) {
                    Optional<Entity> result = Iterables.tryFind(getChildren(), Predicates.equalTo(member));
                    if (!result.isPresent()) {
                        String nameFormat = Optional.fromNullable(getConfig(MEMBER_DELEGATE_NAME_FORMAT)).or("%s");
                        DelegateEntity child = addChild(EntitySpec.create(DelegateEntity.class)
                                .configure(DelegateEntity.DELEGATE_ENTITY, member)
                                .displayName(String.format(nameFormat, member.getDisplayName())));
                        Entities.manage(child);
                    }
                }

                getManagementSupport().getEntityChangeListener().onMembersChanged();
            }
            return changed;
        }
    }

    /**
     * Returns {@code true} if the group was changed as a result of the call.
     */
    @Override
    public boolean removeMember(final Entity member) {
        synchronized (members) {
            member.removeGroup((Group)getProxyIfAvailable());
            boolean changed = (member != null && members.remove(member));
            if (changed) {
                log.debug("Group {} lost member {}", this, member);
                emit(MEMBER_REMOVED, member);
                setAttribute(GROUP_SIZE, getCurrentSize());
                setAttribute(GROUP_MEMBERS, getMembers());

                if (Boolean.TRUE.equals(getConfig(MEMBER_DELEGATE_CHILDREN))) {
                    Optional<Entity> result = Iterables.tryFind(getChildren(), new Predicate<Entity>() {
                        @Override
                        public boolean apply(Entity input) {
                            return input.getConfig(DelegateEntity.DELEGATE_ENTITY).equals(member);
                        }
                    });
                    if (result.isPresent()) {
                        Entity child = result.get();
                        removeChild(child);
                        Entities.unmanage(child);
                       }
                }

                getManagementSupport().getEntityChangeListener().onMembersChanged();
            }

            return changed;
        }
    }

    @Override
    public void setMembers(Collection<Entity> m) {
        setMembers(m, null);
    }

    @Override
    public void setMembers(Collection<Entity> mm, Predicate<Entity> filter) {
        synchronized (members) {
            log.debug("Group {} members set explicitly to {} (of which some possibly filtered)", this, members);
            List<Entity> mmo = new ArrayList<Entity>(getMembers());
            for (Entity m: mmo) {
                if (!(mm.contains(m) && (filter==null || filter.apply(m))))
                    // remove, unless already present, being set, and not filtered out
                    removeMember(m);
            }
            for (Entity m: mm) {
                if ((!mmo.contains(m)) && (filter==null || filter.apply(m))) {
                    // add if not alrady contained, and not filtered out
                    addMember(m);
                }
            }

            getManagementSupport().getEntityChangeListener().onMembersChanged();
        }
    }

    @Override
    public Collection<Entity> getMembers() {
        synchronized (members) {
            return ImmutableSet.<Entity>copyOf(members);
        }
    }

    @Override
    public boolean hasMember(Entity e) {
        synchronized (members) {
            return members.contains(e);
        }
    }

    @Override
    public Integer getCurrentSize() {
        synchronized (members) {
            return members.size();
        }
    }

    @Override
    public <T extends Entity> T addMemberChild(EntitySpec<T> spec) {
        T child = addChild(spec);
        addMember(child);
        return child;
    }

    @Override
    public <T extends Entity> T addMemberChild(T child) {
        child = addChild(child);
        addMember(child);
        return child;
    }

}
