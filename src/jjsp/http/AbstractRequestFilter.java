
package jjsp.http;

import java.io.*;
import java.net.*;

public abstract class AbstractRequestFilter implements HTTPRequestFilter
{
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";
    public static final String NOT_FOUND = "NOT-FOUND";
    public static final String UNHANDLED = "UNHANDLED";
    public static final String DEFAULT_FILTER = "DEFAULT_FILTER";
    
    protected final String filterName;

    protected HTTPRequestFilter filterChain;

    public AbstractRequestFilter(String filterName, HTTPRequestFilter filterChain)
    {
        if ((filterName == null) || (filterName.trim().length() == 0))
            throw new IllegalStateException("Invalid/empty filter name");

        this.filterName = filterName;
        this.filterChain = filterChain;
    }
    
    public String getName()
    {
        return filterName;
    }

    public HTTPRequestFilter getFilterChain()
    {
        return filterChain;
    }

    protected boolean handleRequest(HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
    {
        return false;
    }
    
    protected boolean handleRequestAndReport(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
    {
        if (!handleRequest(request, response, state))
            return false;
        if (chain.report == null)
            chain.report = OK;
        return true;
    }

    public HTTPFilterChain filterRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state)
    {
        HTTPFilterChain myChain = new HTTPFilterChain(filterName, chain);
        try
        {
            if (!handleRequestAndReport(myChain, request, response, state))
            {
                if (filterChain != null)
                {
                    myChain.report = DEFAULT_FILTER;
                    return filterChain.filterRequest(myChain, request, response, state);
                }

                myChain.report = UNHANDLED;
            }
        }
        catch (Throwable t)
        {
            myChain.report = ERROR;
            myChain.error = t;
        }
        
        return myChain;
    }

    protected void closeFilter() throws Exception {}

    public void close()
    {
        if (filterChain != null)
            filterChain.close();

        try
        {
            closeFilter();
        }
        catch (Exception e) {}
    }
}
