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
package brooklyn.basic;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.management.ManagementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.internal.ApiObjectsFactory;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.proxying.InternalFactory;
import brooklyn.entity.rebind.RebindManagerImpl;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.text.Identifiers;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public abstract class AbstractBrooklynObject implements BrooklynObjectInternal {

    private static final Logger log = LoggerFactory.getLogger(AbstractBrooklynObject.class);

    private boolean _legacyConstruction;
    private boolean hasWarnedOfNoManagementContextWhenPersistRequested;

    @SetFromFlag(value = "id")
    private String id = Identifiers.makeRandomId(8);

    private String catalogItemId;

    /** subclasses should synchronize on this for all access */
    @SetFromFlag(value = "tags")
    private final Set<Object> tags = Sets.newLinkedHashSet();

    private volatile ManagementContext managementContext;

    public abstract void setDisplayName(String newName);

    public AbstractBrooklynObject() {
        this(Maps.newLinkedHashMap());
    }

    public AbstractBrooklynObject(Map<?, ?> properties) {
        _legacyConstruction = !InternalFactory.FactoryConstructionTracker.isConstructing();

        if (!_legacyConstruction && properties != null && !properties.isEmpty()) {
            log.warn("Forcing use of deprecated old-style construction for {} because properties were " +
                            "specified ({}); instead use specs (e.g. LocationSpec, EntitySpec, etc)",
                    getClass().getName(), properties);
            if (log.isDebugEnabled())
                log.debug("Source of use of old-style construction", new Throwable("Source of use of old-style construction"));
            _legacyConstruction = true;
        }

        catalogItemId = ApiObjectsFactory.get().getCatalogItemIdFromContext();

        // rely on sub-class to call configure(properties), because otherwise its fields will not have been initialised
    }

    /**
     * See {@link #configure(Map)}
     *
     * @deprecated since 0.7.0; only used for legacy brooklyn types where constructor is called directly
     */
    @Deprecated
    protected AbstractBrooklynObject configure() {
        return configure(Collections.emptyMap());
    }

    /**
     * Will set fields from flags, and put the remaining ones into the 'leftovers' map.
     * For some types, you can find unused config via {@link ConfigBag#getUnusedConfig()}.
     * <p>
     * To be overridden by AbstractEntity, AbstractLoation, AbstractPolicy, AbstractEnricher, etc.
     * <p>
     * But should not be overridden by specific entity types. If you do, the entity may break in
     * subsequent releases. Also note that if you require fields to be initialized you must do that
     * in this method. You must *not* rely on field initializers because they may not run until *after*
     * this method (this method is invoked by the constructor in this class, so initializers
     * in subclasses will not have run when this overridden method is invoked.)
     *
     * @deprecated since 0.7.0; only used for legacy brooklyn types where constructor is called directly
     */
    @Deprecated
    protected abstract AbstractBrooklynObject configure(Map<?, ?> flags);

    protected boolean isLegacyConstruction() {
        return _legacyConstruction;
    }

    /**
     * Called by framework (in new-style instances where spec was used) after configuring etc,
     * but before a reference to this instance is shared.
     * <p>
     * To preserve backwards compatibility for if the instance is constructed directly, one
     * can call the code below, but that means it will be called after references to this
     * policy have been shared with other entities.
     * <pre>
     * {@code
     * if (isLegacyConstruction()) {
     *     init();
     * }
     * }
     * </pre>
     */
    public void init() {
        // no-op
    }

    /**
     * Called by framework on rebind (in new-style instances):
     * <ul>
     * <li> after configuring, but
     * <li> before the instance is managed, and
     * <li> before adjuncts are attached to entities, and
     * <li> before a reference to an object is shared.
     * </ul>
     * Note that {@link #init()} will not be called on rebind.
     * <p>
     * If you need to intercept behaviour <i>after</i> adjuncts are attached,
     * consider {@link AbstractEntity#onManagementStarting()} 
     * (but probably worth raising a discussion on the mailing list!)
     */
    public void rebind() {
        // no-op
    }

    public void setManagementContext(ManagementContextInternal managementContext) {
        this.managementContext = managementContext;
    }

    public ManagementContext getManagementContext() {
        return managementContext;
    }

    protected boolean isRebinding() {
        return RebindManagerImpl.RebindTracker.isRebinding();
    }

    protected void requestPersist() {
        if (getManagementContext() != null) {
            getManagementContext().getRebindManager().getChangeListener().onChanged(this);
        } else {
            // Might be nice to log this at debug but it gets hit a lot of times as locations
            // are created and destroyed for the API. It also might not be an error - the
            // management context might be null if the object is being recreated by persistence.
            if (log.isTraceEnabled() && !hasWarnedOfNoManagementContextWhenPersistRequested) {
                log.trace("Cannot fulfil request to persist {} because it has no management context. " +
                        "This warning will not be logged for this object again.", this);
                hasWarnedOfNoManagementContextWhenPersistRequested = true;
            }
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setCatalogItemId(String id) {
        this.catalogItemId = id;
    }

    @Override
    public String getCatalogItemId() {
        return catalogItemId;
    }

    protected void onTagsChanged() {
        requestPersist();
    }

    @Override
    public TagSupport tags() {
        return new BasicTagSupport();
    }

    protected class BasicTagSupport implements TagSupport {
        @Override
        public Set<Object> getTags() {
            synchronized (tags) {
                return ImmutableSet.copyOf(tags);
            }
        }

        @Override
        public boolean containsTag(Object tag) {
            synchronized (tags) {
                return tags.contains(tag);
            }
        }

        @Override
        public boolean addTag(Object tag) {
            boolean result;
            synchronized (tags) {
                result = tags.add(tag);
            }
            onTagsChanged();
            return result;
        }

        @Override
        public boolean addTags(Iterable<?> newTags) {
            boolean result;
            synchronized (tags) {
                result = Iterables.addAll(tags, newTags);
            }
            onTagsChanged();
            return result;
        }

        @Override
        public boolean removeTag(Object tag) {
            boolean result;
            synchronized (tags) {
                result = tags.remove(tag);
            }
            onTagsChanged();
            return result;
        }
    }

}
