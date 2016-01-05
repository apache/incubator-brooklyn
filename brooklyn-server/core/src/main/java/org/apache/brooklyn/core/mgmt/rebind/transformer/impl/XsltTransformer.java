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
package org.apache.brooklyn.core.mgmt.rebind.transformer.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.brooklyn.core.mgmt.rebind.transformer.RawDataTransformer;

import com.google.common.annotations.Beta;

@Beta
public class XsltTransformer implements RawDataTransformer {

    private final TransformerFactory factory;
    private final String xsltContent;

    public XsltTransformer(String xsltContent) {
        factory = TransformerFactory.newInstance();
        this.xsltContent = xsltContent;
    }
    
    public String transform(String input) throws IOException, URISyntaxException, TransformerException {
        // stream source is single-use
        StreamSource xslt = new StreamSource(new ByteArrayInputStream(xsltContent.getBytes()));
        Transformer transformer = factory.newTransformer(xslt);
        
        Source text = new StreamSource(new ByteArrayInputStream(input.getBytes()));
        ByteArrayOutputStream baos = new ByteArrayOutputStream(input.length());
        transformer.transform(text, new StreamResult(baos));
        
        return new String(baos.toByteArray());
    }
}
