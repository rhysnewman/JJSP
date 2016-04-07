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

import jjsp.http.*;

public class HTTPMethodFilter implements HTTPRequestFilter
{
    private String name;

    protected HTTPRequestFilter postFilter, getFilter, headFilter;

    public HTTPMethodFilter(String name, HTTPRequestFilter headFilter, HTTPRequestFilter getFilter, HTTPRequestFilter postFilter)
    {
        this.name = name;

        this.postFilter = postFilter;
        this.getFilter = getFilter;
        this.headFilter = headFilter;
    }
    
    public void close() 
    {
        if (postFilter != null)
            postFilter.close();
        if (getFilter != null)
            getFilter.close();
        if (headFilter != null)
            headFilter.close();
    }

    public HTTPFilterChain filterRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state)
    {
        HTTPFilterChain myChain = new HTTPFilterChain(name, chain);
        HTTPRequestHeaders reqHeaders = request.getHeaders();

        if (reqHeaders.isGet())
            return getFilter.filterRequest(myChain, request, response, state);
        else if (reqHeaders.isPost())
            return postFilter.filterRequest(myChain, request, response, state);
        else if (reqHeaders.isHead())
            return headFilter.filterRequest(myChain, request, response, state);
        else
        {
            myChain.error = new IOException("Invalid HTTP method "+reqHeaders.getMainLine());
            return myChain;
        }
    }
}
