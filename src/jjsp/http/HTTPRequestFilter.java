package jjsp.http;

import java.io.*;
import java.net.*;

public interface HTTPRequestFilter
{
    public HTTPFilterChain filterRequest(HTTPFilterChain chain, HTTPInputStream input, HTTPOutputStream output, ConnectionState state);
    
    default public void close() {}

    default public String getName() 
    {
        return getClass().getName();
    }
}
