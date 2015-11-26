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
package org.apache.brooklyn.core.typereg;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.core.catalog.internal.CatalogItemBuilder;
import org.apache.brooklyn.core.test.BrooklynMgmtUnitTestSupport;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;


public class RegisteredTypePredicatesTest extends BrooklynMgmtUnitTestSupport {

    @Test
    public void testDisplayName() {
        RegisteredType item = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
                .plan("services:\n- type: org.apache.brooklyn.entity.stock.BasicEntity")
                .displayName("myname")
                .build());

        assertTrue(RegisteredTypePredicates.displayName(Predicates.equalTo("myname")).apply(item));
        assertFalse(RegisteredTypePredicates.displayName(Predicates.equalTo("wrongname")).apply(item));
    }
    
    @Test
    public void testDeprecated() {
        RegisteredType item = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
                .plan("services:\n- type: org.apache.brooklyn.entity.stock.BasicEntity")
                .build());

        assertTrue(RegisteredTypePredicates.deprecated(false).apply(item));
        assertFalse(RegisteredTypePredicates.deprecated(true).apply(item));
        
        item = deprecateItem(item);
        
        assertFalse(RegisteredTypePredicates.deprecated(false).apply(item));
        assertTrue(RegisteredTypePredicates.deprecated(true).apply(item));
    }
    
    @Test
    public void testDisabled() {
        RegisteredType item = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
                .plan("services:\n- type: org.apache.brooklyn.entity.stock.BasicEntity")
                .build());

        assertTrue(RegisteredTypePredicates.disabled(false).apply(item));
        assertFalse(RegisteredTypePredicates.disabled(true).apply(item));
        
        item = disableItem(item);
        
        assertFalse(RegisteredTypePredicates.disabled(false).apply(item));
        assertTrue(RegisteredTypePredicates.disabled(true).apply(item));
    }
    
    @Test
    public void testIsCatalogItemType() {
        RegisteredType item = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
                .plan("services:\n- type: org.apache.brooklyn.entity.stock.BasicEntity")
                .build());

        assertTrue(RegisteredTypePredicates.IS_ENTITY.apply(item));
        assertFalse(RegisteredTypePredicates.IS_LOCATION.apply(item));
    }
    
    @Test
    public void testSymbolicName() {
        RegisteredType item = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
                .plan("services:\n- type: org.apache.brooklyn.entity.stock.BasicEntity")
                .build());

        assertTrue(RegisteredTypePredicates.symbolicName(Predicates.equalTo("foo")).apply(item));
        assertFalse(RegisteredTypePredicates.symbolicName(Predicates.equalTo("wrongname")).apply(item));
    }

    @Test
    public void testIsBestVersion() {
        RegisteredType itemV1 = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
                .plan("services:\n- type: org.apache.brooklyn.entity.stock.BasicEntity")
                .build());
        RegisteredType itemV2 = createItem(CatalogItemBuilder.newEntity("foo", "2.0")
                .plan("services:\n- type: org.apache.brooklyn.entity.stock.BasicEntity")
                .build());
        RegisteredType itemV3Disabled = createItem(CatalogItemBuilder.newEntity("foo", "3.0")
                .disabled(true)
                .plan("services:\n- type: org.apache.brooklyn.entity.stock.BasicEntity")
                .build());

        assertTrue(RegisteredTypePredicates.isBestVersion(mgmt).apply(itemV2));
        assertFalse(RegisteredTypePredicates.isBestVersion(mgmt).apply(itemV1));
        assertFalse(RegisteredTypePredicates.isBestVersion(mgmt).apply(itemV3Disabled));
    }

    @Test
    public void testEntitledToSee() {
        // TODO No entitlements configured, so everything allowed - therefore test not thorough enough!
        RegisteredType item = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
                .plan("services:\n- type: org.apache.brooklyn.entity.stock.BasicEntity")
                .build());

        assertTrue(RegisteredTypePredicates.entitledToSee(mgmt).apply(item));
    }

    // TODO do we need this predicate?
//    @SuppressWarnings("deprecation")
//    @Test
//    public void testJavaType() {
//        RegisteredType item = createItem(CatalogItemBuilder.newEntity("foo", "1.0")
//                .javaType("org.apache.brooklyn.entity.stock.BasicEntity")
//                .build());
//
//        assertTrue(RegisteredTypePredicates.javaType(Predicates.equalTo("org.apache.brooklyn.entity.stock.BasicEntity")).apply(item));
//        assertFalse(RegisteredTypePredicates.javaType(Predicates.equalTo("wrongtype")).apply(item));
//    }

    @SuppressWarnings("deprecation")
    protected RegisteredType createItem(CatalogItem<?,?> item) {
        mgmt.getCatalog().addItem(item);
        return RegisteredTypes.of(item);
    }
    
    @SuppressWarnings({ "deprecation" })
    protected <T, SpecT> RegisteredType deprecateItem(RegisteredType orig) {
        CatalogItem<?,?> item = (CatalogItem<?,?>) mgmt.getCatalog().getCatalogItem(orig.getSymbolicName(), orig.getVersion());
        item.setDeprecated(true);
        mgmt.getCatalog().persist(item);
        return RegisteredTypes.of(item);
    }
    
    @SuppressWarnings({ "deprecation" })
    protected RegisteredType disableItem(RegisteredType orig) {
        CatalogItem<?,?> item = (CatalogItem<?,?>) mgmt.getCatalog().getCatalogItem(orig.getSymbolicName(), orig.getVersion());
        item.setDisabled(true);
        mgmt.getCatalog().persist(item);
        return RegisteredTypes.of(item);
    }
}
