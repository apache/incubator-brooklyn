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

import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.api.typereg.RegisteredTypeLoadingContext;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.util.core.xstream.XmlSerializer;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.stream.ReaderInputStream;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/** Example implementation of {@link BrooklynTypePlanTransformer} showing 
 * how implementations are meant to be written. */
public class ExampleXmlTypePlanTransformer extends AbstractTypePlanTransformer {

    protected ExampleXmlTypePlanTransformer() {
        super("example-xml", "Example XML", "Illustration of writing a transformer");
    }

    @Override
    protected double scoreForNullFormat(Object planData, RegisteredType type, RegisteredTypeLoadingContext context) {
        if (!(planData instanceof String)) return 0;
        try {
            // if it's XML, accept it
            parseXml((String)planData);
            return 0.3;
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            return 0;
        }
    }

    @Override
    protected double scoreForNonmatchingNonnullFormat(String planFormat, Object planData, RegisteredType type, RegisteredTypeLoadingContext context) {
        // only null and xml supported
        return 0;
    }

    @Override
    protected AbstractBrooklynObjectSpec<?, ?> createSpec(RegisteredType type, RegisteredTypeLoadingContext context) throws Exception {
        return toEntitySpec(parseXml((String)type.getPlan().getPlanData()), 
            isApplicationExpected(type, context) ? 0 : 1);
    }

    private static boolean isApplicationExpected(RegisteredType type, RegisteredTypeLoadingContext context) {
        return RegisteredTypes.isSubtypeOf(type, Application.class) ||
            (context.getExpectedJavaSuperType()!=null && context.getExpectedJavaSuperType().isAssignableFrom(Application.class));
    }

    @Override
    protected Object createBean(RegisteredType type, RegisteredTypeLoadingContext context) throws Exception {
        return new XmlSerializer<Object>().fromString((String)type.getPlan().getPlanData());
    }


    @Override
    public double scoreForTypeDefinition(String formatCode, Object catalogData) {
        // defining types not supported
        return 0;
    }

    @Override
    public List<RegisteredType> createFromTypeDefinition(String formatCode, Object catalogData) {
        // defining types not supported
        return null;
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
            throw new UnsupportedTypePlanException(e);
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
