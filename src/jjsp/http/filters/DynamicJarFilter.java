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
