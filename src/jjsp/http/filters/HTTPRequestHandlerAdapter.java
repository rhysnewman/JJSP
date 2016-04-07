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

import jjsp.http.*;

public class HTTPRequestHandlerAdapter extends AbstractRequestFilter
{
    protected HTTPRequestHandler handler;

    public HTTPRequestHandlerAdapter(String name, HTTPRequestHandler handler, HTTPRequestFilter filterChain)
    {
        super(name, filterChain);
        this.handler = handler;
    }

    protected boolean handleRequestAndReport(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
    {
        if (handler.handleRequest(request, response, state, chain))
            return true;

        return response.outputSent() && response.contentStreamClosed();
    }
}
