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

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;
import java.util.function.*;

public class JarUtils
{
    public static Manifest createManifest(String name, String vendor, String title, String version, String mainClass) throws IOException
    {
        return createManifest(name, new Date(), vendor, title, version, mainClass);
    }

    public static Manifest createManifest(String name, Date buildDate, String vendor, String title, String version, String mainClass) throws IOException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bout);
        ps.println("Manifest-Version: 1.0");
        ps.println("Name: "+name);
        ps.println("Build-Date: "+buildDate);
        ps.println("Specification-Title: "+title);
        ps.println("Specification-Vendor: "+vendor);
        ps.println("Implementation-Version: "+version);
        ps.println("Main-Class: "+mainClass);
        ps.println("");
        ps.flush();

        return new Manifest(new ByteArrayInputStream(bout.toByteArray()));
    }

    public static byte[] createJar(Manifest mf, Predicate<String> acceptor) throws IOException
    {
        return createJar(mf, acceptor, (URLClassLoader) JarUtils.class.getClassLoader());
    }

    public static byte[] createJar(Manifest mf, Predicate<String> acceptor, File f) throws IOException
    {
        return createJar(mf, acceptor, new URLClassLoader(new URL[]{f.toURI().toURL()}));
    }

    public static byte[] createJar(Manifest mf, Predicate<String> acceptor, URLClassLoader source) throws IOException {
        return createJar(mf, acceptor, source, false);
    }

    public static byte[] createJar(Manifest mf, Predicate<String> acceptor, URLClassLoader source, boolean includeParentLoader) throws IOException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        JarOutputStream jout = new JarOutputStream(bout, mf);
        
        String[] resourceNames = Utils.find(acceptor, source, includeParentLoader);
        for (String resourceName : resourceNames) {
            try {
                byte[] rawResource = Utils.load(resourceName, source);
                if (rawResource != null) {
                    JarEntry entry = new JarEntry(resourceName);
                    jout.putNextEntry(entry);
                    jout.write(rawResource);
                    jout.flush();
                    jout.closeEntry();
                }
            }
            catch (IOException e) {
                throw new IOException("Unable to load resource " + resourceName, e);
            }
        }
        jout.close();

        return bout.toByteArray();
    }

    public static byte[] addFile(Manifest mf, byte[] mainJarBytes, String extraFileName, byte[] extraFileData) throws IOException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        JarOutputStream jout = new JarOutputStream(bout, mf);
        
        JarEntry entry = new JarEntry(extraFileName);
        jout.putNextEntry(entry);
        jout.write(extraFileData);
        jout.flush();
        jout.closeEntry();
        jout.close();
        
        return mergeJars(mf, mainJarBytes, bout.toByteArray());
    }

    public static byte[] mergeJars(Manifest mf, byte[] primaryJar, byte[] secondaryJar) throws IOException
    {
        HashMap resourceMap = new HashMap();
        JarInputStream jin1 = new JarInputStream(new ByteArrayInputStream(secondaryJar));
        while (true)
        {
            JarEntry jen = jin1.getNextJarEntry();
            if (jen == null)
                break;
            String name = jen.getName();
            byte[] data = Utils.load(jin1, -1, false);
            resourceMap.put(name, data);
        }
        
        JarInputStream jin2 = new JarInputStream(new ByteArrayInputStream(primaryJar));
        while (true)
        {
            JarEntry jen = jin2.getNextJarEntry();
            if (jen == null)
                break;
            String name = jen.getName();
            byte[] data = Utils.load(jin2, -1, false);
            resourceMap.put(name, data);
        }
        
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        JarOutputStream jout = new JarOutputStream(bout, mf);
        
        Iterator itt = resourceMap.keySet().iterator();
        while (itt.hasNext())
        {
            String resourceName = (String) itt.next();
            byte[] data = (byte[]) resourceMap.get(resourceName);
            
            JarEntry entry = new JarEntry(resourceName);
            jout.putNextEntry(entry);
            jout.write(data);
            jout.flush();
            jout.closeEntry();
        }
        jout.close();
        
        return bout.toByteArray();
    }
}
