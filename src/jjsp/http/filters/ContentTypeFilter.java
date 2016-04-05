
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
