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
import jjsp.util.*;

public class SimpleFileFilter extends AbstractRequestFilter
{
    protected File src;
    protected String contentType;

    public SimpleFileFilter(String name, File src)
    {
        this(name, src, HTTPHeaders.guessMIMEType(src.getName()));
    }

    public SimpleFileFilter(String name, File src, String contentType)
    {
        super(name, null);

        this.src = src;
        this.contentType = contentType;
    }

    protected boolean handleRequestAndReport(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
    {
        response.getHeaders().configureAsOK();
        response.getHeaders().setLastModified(src.lastModified());
        response.getHeaders().configureToPreventCaching();

        if (request.getHeaders().isHead())
        {
            response.getHeaders().setContentLength(src.length());
            response.sendHeaders();
        }
        else
            response.sendContent(Utils.load(src));

        chain.report = src.getName();
        return true;
    }
}
