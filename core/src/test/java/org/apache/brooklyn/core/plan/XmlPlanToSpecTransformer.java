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
package org.apache.brooklyn.core.plan;

import java.io.StringReader;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogItemType;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.stream.ReaderInputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Example implementation of {@link PlanToSpecTransformer} showing 
 * how implementations are meant to be written. */
public class XmlPlanToSpecTransformer implements PlanToSpecTransformer {
    
    // this is REPLACED by ExampleXmlTypePlanTransformer
    // TODO remove when PlanToSpecTransformer is removed
    
    @SuppressWarnings("unused")
    private ManagementContext mgmt;

    @Override
    public void injectManagementContext(ManagementContext managementContext) {
        mgmt = managementContext;
    }

    @Override
    public String getShortDescription() {
        return "Dummy app structure created from the XML tree";
    }

    @Override
    public boolean accepts(String mime) {
        if ("test-xml".equals(mime)) return true;
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public EntitySpec<? extends Application> createApplicationSpec(String plan) {
        Document dom = parseXml(plan);
        EntitySpec<?> result = toEntitySpec(dom, 0);
        if (Application.class.isAssignableFrom(result.getType())) {
            return (EntitySpec<Application>) result;
        } else {
            return EntityManagementUtils.newWrapperApp().child(result);
        }
    }

    @SuppressWarnings({ "unchecked" })
    @Override
    public <T, SpecT extends AbstractBrooklynObjectSpec<? extends T, SpecT>> SpecT createCatalogSpec(CatalogItem<T, SpecT> item, Set<String> encounteredTypes) {
        if (item.getPlanYaml()==null) throw new PlanNotRecognizedException("Plan is null");
        if (item.getCatalogItemType()==CatalogItemType.ENTITY) {
            return (SpecT)toEntitySpec(parseXml(item.getPlanYaml()), 1);
        }
        if (item.getCatalogItemType()==CatalogItemType.TEMPLATE) {
            return (SpecT)toEntitySpec(parseXml(item.getPlanYaml()), 0);
        }
        throw new PlanNotRecognizedException("Type "+item.getCatalogItemType()+" not supported");
    }

    private Document parseXml(String plan) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom;
        
        try {
            //Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();

            //parse using builder to get DOM representation of the XML file
            dom = db.parse(new ReaderInputStream(new StringReader(plan)));
            
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            throw new PlanNotRecognizedException(e);
        }
        return dom;
    }

    private EntitySpec<?> toEntitySpec(Node dom, int depth) {
        if (dom.getNodeType()==Node.DOCUMENT_NODE) {
            if (dom.getChildNodes().getLength()!=1) {
                // NB: <?...?>  entity preamble might break this
                throw new IllegalStateException("Document for "+dom+" has "+dom.getChildNodes().getLength()+" nodes; 1 expected.");
            }
            return toEntitySpec(dom.getChildNodes().item(0), depth);
        }
        
        EntitySpec<?> result = depth == 0 ? EntitySpec.create(BasicApplication.class) : EntitySpec.create(BasicEntity.class);
        result.displayName(dom.getNodeName());
        if (dom.getAttributes()!=null) {
            for (int i=0; i<dom.getAttributes().getLength(); i++)
                result.configure(dom.getAttributes().item(i).getNodeName(), dom.getAttributes().item(i).getTextContent());
        }
        if (dom.getChildNodes()!=null) {
            for (int i=0; i<dom.getChildNodes().getLength(); i++) {
                Node item = dom.getChildNodes().item(i);
                if (item.getNodeType()==Node.ELEMENT_NODE) {
                    result.child(toEntitySpec(item, depth+1));
                }
            }
        }
        return result;
    }

}
