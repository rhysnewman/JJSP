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
