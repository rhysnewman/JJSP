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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class JAXBUtils {

    private static volatile boolean debug;

    public static void setDebug(boolean debug) {
        JAXBUtils.debug = debug;
    }

    public static boolean isDebug() {
        return debug;
    }

    private static JAXBContext getContext(Class _class) {
        try {
            return JAXBContext.newInstance(_class);
        } catch (JAXBException jaxbe) {
            throw new IllegalStateException("Could not create JAXB context for class " + _class.getName() , jaxbe);
        }
    }

    public static <T> T deserialize(Path input, Class<T> clazz) throws IOException {

        InputStream in = new FileInputStream(input.toFile());

        return deserialize(in, clazz);
    }

    public static <T> T deserialize(InputStream in , Class<T> clazz) {
        XMLInputFactory xif = XMLInputFactory.newFactory();

        StreamSource xml = new StreamSource(new InputStreamReader(in));

        String name = clazz.getSimpleName().trim();
        Set<String> names = new HashSet<>();

        try {
            XMLStreamReader xsr = xif.createXMLStreamReader(xml);

            while (xsr.hasNext()) {
                xsr.nextTag();
                String localName = xsr.getLocalName();
                names.add(localName);
                if (!localName.equals(name))
                    continue;
                return (T) getContext(clazz).createUnmarshaller().unmarshal(xsr);
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }

        throw new IllegalStateException("No elements with label "+ name);
    }


    public static <T> void serialize(JAXBElement emp, Class<T> clazz, OutputStream out) {

        JAXBContext context = getContext(clazz);

        try {
            Marshaller m = context.createMarshaller();

            // Write to System.out for debugging
            if (debug) {
                m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE); //for pretty-print XML in JAXB
                m.marshal(emp, System.out);
                m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.FALSE);
            }

            m.marshal(emp, out);
        } catch (JAXBException e) {
            throw new IllegalStateException(e);
        }
    }
}
