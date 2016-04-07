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

public class ContentTypeFilter extends AbstractRequestFilter
{
    private String[] extensionList;
    private final HashMap extensionIndex;

    public ContentTypeFilter(String name, HTTPRequestFilter def) 
    {
        this(name, null, def);
    }

    public ContentTypeFilter(String name, Map initialFilters, HTTPRequestFilter def) 
    {
        super(name, def);
        extensionIndex = new HashMap();
        extensionList = new String[0];
        
        if (initialFilters != null)
        {
            Iterator itt = initialFilters.keySet().iterator();
            while (itt.hasNext())
            {
                String key = (String) itt.next();
                registerContentFilter(key, (HTTPRequestFilter) initialFilters.get(key));
            }
        }
    }

    public void registerContentFilter(HTTPRequestFilter filter)
    {
        registerContentFilter(filter.getName(), filter);
    }

    public void registerContentFilter(String pathExtension, HTTPRequestFilter filter)
    {
        pathExtension = pathExtension.replace("*", "");
        extensionIndex.put(pathExtension, filter);
        extensionList = new String[extensionIndex.size()];
        extensionIndex.keySet().toArray(extensionList);
        Arrays.sort(extensionList);
    }

    protected void closeFilter() throws Exception
    {
        for (int i=0; i<extensionList.length; i++)
            ((HTTPRequestFilter) extensionIndex.get(extensionList[i])).close();
    }

    public HTTPFilterChain filterRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state)
    {
        HTTPFilterChain myChain = new HTTPFilterChain(filterName, chain);
        String path = request.getHeaders().getPath(); 

        try
        {
            for (int i=extensionList.length-1; i>=0; i--)
            {
                if (path.endsWith(extensionList[i]))
                {
                    myChain.report = extensionList[i];
                    HTTPRequestFilter filter = (HTTPRequestFilter) extensionIndex.get(extensionList[i]);
                    return filter.filterRequest(myChain, request, response, state);
                }
            }
            
            if (filterChain != null)
            {
                myChain.report = DEFAULT_FILTER;
                return filterChain.filterRequest(myChain, request, response, state);
            }
            else
                myChain.report = UNHANDLED;
        }
        catch (Throwable t)
        {
            myChain.report = ERROR;
            myChain.error = t;
        }
        
        return myChain;
    }
}
