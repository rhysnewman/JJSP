
package jjsp.http;

import java.io.*;
import java.net.*;

public interface HTTPRequestPredicate
{
    public boolean test(HTTPInputStream request, HTTPOutputStream response, ConnectionState state, HTTPFilterChain chain) throws IOException;
}
    
