
package jjsp.http;

import java.io.*;
import java.net.*;

public interface HTTPRequestHandler
{
    public boolean handleRequest(HTTPInputStream request, HTTPOutputStream response, ConnectionState state, HTTPFilterChain chain) throws IOException;
}
    
