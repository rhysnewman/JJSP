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

public class PortFilter implements HTTPRequestFilter
{
    public static final String ACCEPTED = "ACCEPTED";
    public static final String PASSED_ON = "PASSED_ON";
    
    private final String name;
    private final HTTPRequestFilter onwardChain;

    private volatile HTTPRequestFilter[] registeredFilters;

    public PortFilter(String name, HTTPRequestFilter onwardChain)
    {
        this.name = name;
        this.onwardChain = onwardChain;

        registeredFilters = new HTTPRequestFilter[0xFFFF]; //Pre-allocate slots for all 65k ports 
    }

    public void registerFilter(int port, HTTPRequestFilter filter)
    {
        HTTPRequestFilter[] ff = new HTTPRequestFilter[0xFFFF]; 
        System.arraycopy(registeredFilters, 0, ff, 0, registeredFilters.length);
        ff[port] = filter;
        
        registeredFilters = ff;
    }

    public void deregisterFilter(int port)
    {
        registerFilter(port, null);
    }

    public void close() 
    {
        HTTPRequestFilter[] ff = registeredFilters;
        for (int i=0; i<ff.length; i++)
            if (ff[i] != null)
                ff[i].close();

        if (onwardChain != null)
            onwardChain.close();
    }

    public HTTPFilterChain filterRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state)
    {
        HTTPFilterChain myChain = new HTTPFilterChain(name, chain);

        int incomingPort = request.getServerPort();
        HTTPRequestFilter filter = registeredFilters[incomingPort];
        if (filter != null)
        {
            myChain.report = ACCEPTED;
            return filter.filterRequest(myChain, request, response, state);
        }
        
        if (onwardChain == null)
        {
            try
            {
                myChain.report = AbstractRequestFilter.ERROR;
                response.getHeaders().configureAsServerError("No onward chain filter defined for Port Filter "+getName());
                response.sendHeaders();
            }
            catch (Exception e)
            {
                myChain.error = e;
            }
            return myChain;
        }
        
        myChain.report = PASSED_ON;
        return onwardChain.filterRequest(myChain, request, response, state);
    }
}
