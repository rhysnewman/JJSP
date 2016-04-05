package jjsp.util;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.zip.*;
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

    public static byte[] createJar(Manifest mf, Predicate acceptor) throws IOException
    {
        return createJar(mf, acceptor, (URLClassLoader) JarUtils.class.getClassLoader());
    }

    public static byte[] createJar(Manifest mf, Predicate acceptor, File f) throws IOException
    {
        return createJar(mf, acceptor, new URLClassLoader(new URL[]{f.toURI().toURL()}));
    }

    public static byte[] createJar(Manifest mf, Predicate acceptor, URLClassLoader source) throws IOException
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        JarOutputStream jout = new JarOutputStream(bout, mf);
        
        String[] resourceNames = Utils.find(acceptor, source);
        for (int j=0; j<resourceNames.length; j++)
        {
            byte[] rawResource = Utils.load(resourceNames[j], source);
            JarEntry entry = new JarEntry(resourceNames[j]);
            jout.putNextEntry(entry);
            jout.write(rawResource);
            jout.flush();
            jout.closeEntry();
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
