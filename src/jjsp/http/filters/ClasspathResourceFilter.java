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
import java.util.*;

import jjsp.util.*;
import jjsp.http.*;

public class ClasspathResourceFilter extends AbstractRequestFilter 
{
    private HashMap cache;
    private String pathHead, resourcePrefix;

    public ClasspathResourceFilter(String name, HTTPRequestFilter filterChain) throws IOException
    {
        this(name, "/", "", filterChain);
    }

    public ClasspathResourceFilter(String name, String pathHead, String resourcePrefix, HTTPRequestFilter filterChain) throws IOException
    {
        super(name, filterChain);

        this.pathHead = pathHead;
        this.resourcePrefix = resourcePrefix;
        cache = new HashMap();
    }
    
    protected boolean handleRequestAndReport(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
    {
        String resourcePath = request.getHeaders().getPath();
        if (resourcePath.startsWith(pathHead))
            resourcePath = resourcePath.substring(pathHead.length());
        else
            return false;
        
        resourcePath = resourcePrefix+resourcePath;
        chain.report = "CP{"+resourcePath+"}";

        byte[] data = null;
        synchronized (cache)
        {
            data = (byte[]) cache.get(resourcePath);
            if (data == null)
            {
                data = Utils.load(resourcePath);
                if (data != null)
                    cache.put(resourcePath, data);
            }
        }

        if (data == null)
        {
            response.getHeaders().configureAsNotFound();
            response.sendHeaders();
        }
        else
        {
            response.getHeaders().configureAsOK();
            response.getHeaders().configureCacheControl(data, 86400);
            response.getHeaders().guessAndSetContentType(resourcePath);

            if (request.getHeaders().isHead())
            {
                response.getHeaders().setContentLength(data.length);
                response.sendHeaders();
            }
            else
                response.sendContent(data);
        }

        return true;
    }
}
