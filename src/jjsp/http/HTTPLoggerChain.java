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
