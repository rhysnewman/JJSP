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

public class HTTPLoggerChain implements HTTPServerLogger 
{
    private final HTTPServerLogger first, second;

    public HTTPLoggerChain(HTTPServerLogger first, HTTPServerLogger second)
    {
        this.first = first;
        this.second = second;
    }
    
    public void socketException(int clientPort, int serverPort, boolean isSecure, InetSocketAddress clientAddress, Throwable t)
    {
        if (first != null)
            first.socketException(clientPort, serverPort, isSecure, clientAddress, t);
        if (second != null)
            second.socketException(clientPort, serverPort, isSecure, clientAddress, t);
    }

    public void requestProcessed(HTTPLogEntry logEntry)
    {
        if (first != null)
            first.requestProcessed(logEntry);
        if (second != null)
            second.requestProcessed(logEntry);
    }
}
