package jjsp.http;

import java.io.*;
import java.net.*;

public interface HTTPServerLogger 
{
    default public void socketException(int clientPort, int serverPort, boolean isSecure, InetSocketAddress clientAddress, Throwable t) {}

    public void requestProcessed(HTTPLogEntry logEntry); 
}
