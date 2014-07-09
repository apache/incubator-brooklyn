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
package brooklyn.util.xstream;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import brooklyn.util.exceptions.Exceptions;

public class XmlUtil {

    public static Object xpath(String xml, String xpath) {
        // TODO Could share factory/doc in thread-local storage; see http://stackoverflow.com/questions/9828254/is-documentbuilderfactory-thread-safe-in-java-5
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes()));
            XPathFactory xPathfactory = XPathFactory.newInstance();
            XPathExpression expr = xPathfactory.newXPath().compile(xpath);
            
            return expr.evaluate(doc);
            
        } catch (ParserConfigurationException e) {
            throw Exceptions.propagate(e);
        } catch (SAXException e) {
            throw Exceptions.propagate(e);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        } catch (XPathExpressionException e) {
            throw Exceptions.propagate(e);
        }
    }
}
