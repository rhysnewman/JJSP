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

import jjsp.http.*;

public class PathMappedFilter extends AbstractRequestFilter
{
    private String[] pathList;
    private String defaultMimeType;

    private final boolean leadingMatchOnKeys;
    private final HashMap<String, HTTPRequestFilter> filterIndex;

    public PathMappedFilter(String name, HTTPRequestFilter def) 
    {
        this(name, null, true, def);
    }

    public PathMappedFilter(String name, boolean leadingMatchOnKeys, HTTPRequestFilter def) 
    {
        this(name, null, leadingMatchOnKeys, def);
    }
    
    public PathMappedFilter(String name, Map initialFilters, HTTPRequestFilter def) 
    {
        this(name, initialFilters, true, def);
    }

    public PathMappedFilter(String name, Map initialFilters, boolean leadingMatchOnKeys, HTTPRequestFilter def) 
    {
        super(name, def);
        this.leadingMatchOnKeys = leadingMatchOnKeys;
        
        filterIndex = new HashMap();
        pathList = new String[0];
        defaultMimeType = null;

        if (initialFilters != null)
        {
            Iterator itt = initialFilters.keySet().iterator();
            while (itt.hasNext())
            {
                String key = (String) itt.next();
                registerFilter(key, (HTTPRequestFilter) initialFilters.get(key));
            }
        }
    }

    public boolean isLeadingMatchOnKeys()
    {
        return leadingMatchOnKeys;
    }

    public void setDefaultMimeType(String type)
    {
        defaultMimeType = type;
    }

    public synchronized void registerFilter(HTTPRequestFilter filter)
    {
        registerFilter(filter.getName(), filter);
    }

    public synchronized void registerFilter(String path, HTTPRequestFilter filter)
    {
        registerFilter(path, filter, false);
    }

    public synchronized void registerFilter(String path, HTTPRequestFilter filter, boolean overwriteIfPresent)
    {
        if (!path.startsWith("/"))
            path = "/"+path;
        path = path.toLowerCase();

        if (!overwriteIfPresent && (filterIndex.get(path) != null))
            throw new IllegalStateException("Filter already registered on path "+path);
            
        filterIndex.put(path, filter);
        pathList = new String[filterIndex.size()];
        filterIndex.keySet().toArray(pathList);
        Arrays.sort(pathList);
    }

    protected synchronized void closeFilter() throws Exception
    {
        for (int i=0; i<pathList.length; i++)
            ((HTTPRequestFilter) filterIndex.get(pathList[i])).close();
    }

    public HTTPFilterChain filterRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state)
    {
        HTTPFilterChain myChain = new HTTPFilterChain(filterName, chain);
        String url = request.getHeaders().getPath(); 

        try
        {
            url = url.toLowerCase();

            if (leadingMatchOnKeys)
            {
                for (int i=pathList.length-1; i>=0; i--)
                {
                    if (url.startsWith(pathList[i]))
                    {
                        myChain.report = pathList[i];
                        if (defaultMimeType != null)
                            response.getHeaders().setContentType(defaultMimeType);
                    
                        HTTPRequestFilter filter = (HTTPRequestFilter) filterIndex.get(pathList[i]);
                        return filter.filterRequest(myChain, request, response, state);
                    }
                }
            }
            else
            {
                for (int i=pathList.length-1; i>=0; i--)
                {
                    if (url.equals(pathList[i]))
                    {
                        myChain.report = pathList[i];
                        if (defaultMimeType != null)
                            response.getHeaders().setContentType(defaultMimeType);
                    
                        HTTPRequestFilter filter = (HTTPRequestFilter) filterIndex.get(pathList[i]);
                        return filter.filterRequest(myChain, request, response, state);
                    }
                }
            }

            if (filterChain != null)
            {
                myChain.report = DEFAULT_FILTER;
                return filterChain.filterRequest(myChain, request, response, state);
            }
        }
        catch (Throwable t)
        {
            myChain.report = ERROR;
            myChain.error = t;
        }
        
        return myChain;
    }
}
