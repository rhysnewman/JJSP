/*
JJSP - Java and Javascript Server Pages 
Copyright (C) 2016 Global Travel Ventures Ltd

This program is free software: you can redistribute it and/or modify 
it under the terms of the GNU General Public License as published by 
the Free Software Foundation, either version 3 of the License, or 
(at your option) any later version.

This program is distributed in the hope that it will be useful, but 
WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY 
or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License 
for more details.

You should have received a copy of the GNU General Public License along with 
this program. If not, see http://www.gnu.org/licenses/.
*/
package jjsp.util;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;

import javax.xml.parsers.*;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.stream.Collectors;

public class XPathUtils {

    private static XPathFactory xPathFactory = XPathFactory.newInstance();
    private static final ThreadLocal<DocumentBuilder> builder =
            new ThreadLocal<DocumentBuilder>() {
        @Override protected DocumentBuilder initialValue() {
            try {
                return DocumentBuilderFactory.newInstance().newDocumentBuilder();
            } catch (ParserConfigurationException exc) {
                throw new IllegalArgumentException(exc);
            }
        }
    };

    public static Document toDocument(File f) throws Exception {
        return builder.get().parse(f);
    }

    public static Document toDocument(String s) throws Exception {
        s = s.replaceAll("&", "&amp;");
        return toDocument(new ByteArrayInputStream(s.getBytes()));
    }

    public static Document toDocument(InputStream in) throws Exception {
        try {
            return builder.get().parse(in);
        } finally {
            in.close();
        }
    }

    public static String getAttribute(String xPathExpression, String document) throws Exception{
        return getAttribute(xPathExpression, toDocument(document));
    }

    public static String getAttribute(String xPathExpression, InputStream in) throws Exception{
        Document document = toDocument(in);
        return getAttribute(xPathExpression, document);
    }

    public static String getAttribute(String xPathExpression, Document document) throws Exception{
        XPath xpath = xPathFactory.newXPath();
        return xpath.evaluate(xPathExpression, document);
    }

    public static String getText(String xPathExpression, Node node) throws Exception {
        XPath xpath = xPathFactory.newXPath();
        return xpath.evaluate(xPathExpression, node);
    }

    public static NodeList getNodes(String expression, Node root) {
        XPath xpath = xPathFactory.newXPath();
        try {
            NodeList list = (NodeList) xpath.evaluate(expression, root, XPathConstants.NODESET);
            return list;
        } catch (XPathExpressionException xpe) {
            throw new IllegalStateException(xpe);
        }
    }

    public static NodeList getNodes(String expression, Document document) {
        XPath xpath = xPathFactory.newXPath();
        try {
            NodeList list = (NodeList) xpath.evaluate(expression, document, XPathConstants.NODESET);
            return list;
        } catch (XPathExpressionException xpe) {
            throw new IllegalStateException(xpe);
        }
    }

    public static Double getNodesCount(String expression, Document document) {
        XPath xpath = xPathFactory.newXPath();
        try {
            Double count = (Double) xpath.evaluate("count(" + expression + ")", document, XPathConstants.NUMBER);
            return count;
        } catch (XPathExpressionException xpe) {
            throw new IllegalStateException(xpe);
        }
    }
}
