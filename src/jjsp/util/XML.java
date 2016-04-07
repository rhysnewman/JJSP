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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.util.*;

public class XML extends DefaultHandler
{
    private Stack<Map> stack = new Stack<>();
    private Map root;

    public static Object toJSON(String xml) throws SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            XML handler = new XML();
            SAXParser parser = factory.newSAXParser();
            parser.parse(new ByteArrayInputStream(xml.getBytes()), handler);
            return handler.getJSON();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public Object getJSON() {
        return root;
    }

    public void startDocument() throws SAXException {
    }

    public void endDocument() throws SAXException {

    }

    Comparator<String> comp = (String o1, String o2) -> o2.compareTo(o1);

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        Map element = new TreeMap<>(comp);
        element.put("tag", qName);
        if (attributes.getLength() > 0) {
            Map attr = new HashMap<>();
            for (int i = 0; i < attributes.getLength(); i++)
                attr.put(attributes.getLocalName(i), attributes.getValue(i));
            element.put("props", attr);
        }

        if (stack.size() == 0) {
            root = element;
            stack.push(element);
        } else {
            Map current = stack.peek();
            List children = (List) current.get("children");
            if (children == null) {
                children = new ArrayList<>();
                current.put("children", children);
            }
            children.add(element);
            stack.push(element);
        }
    }

    public void characters (char ch[], int start, int length) throws SAXException
    {
        char[] in = new char[length];
        System.arraycopy(ch, start, in, 0, length);
        String s = new String(in);
        if (stack.size() > 0 && (s.trim().length() > 0 || stack.peek().containsKey("atxt"))) {
            Map current = stack.peek();
            if (!current.containsKey("atxt"))
                current.put("atxt", s);
            else
                current.put("atxt", current.get("atxt") + s);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        stack.pop();
    }
}
