
package jjsp.http.filters;

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import java.util.function.*;

import jjsp.http.*;
import jjsp.util.*;

public class DynamicJarFilter extends StaticDataFilter
{
    public DynamicJarFilter(String name, String vendor, String title, String version, String mainClass, Predicate resourcesToInclude) throws IOException
    {
        this(JarUtils.createManifest(name, vendor, title, version, mainClass), resourcesToInclude);
    }

    public DynamicJarFilter(Manifest mf, Predicate resourcesToInclude) throws IOException
    {
        this(mf, resourcesToInclude, (URLClassLoader) DynamicJarFilter.class.getClassLoader());
    }

    public DynamicJarFilter(Manifest mf, Predicate resourcesToInclude, File source) throws IOException
    {
        this(mf, resourcesToInclude, new URLClassLoader(new URL[]{source.toURI().toURL()}));
    }

    public DynamicJarFilter(Manifest mf, Predicate resourcesToInclude, URLClassLoader source) throws IOException
    {
        super(mf.getMainAttributes().getValue("Name"), null, JarUtils.createJar(mf, resourcesToInclude, source), -1, "application/java-archive", null);
    }
}
