package jjsp.http.filters;

import java.io.*;
import java.net.*;
import java.util.*;

import jjsp.http.*;

public class ProxyFilter extends AbstractRequestFilter
{
    protected String hostString;

    public ProxyFilter(String name, String targetHost, int targetPort, HTTPRequestFilter filterChain)
    {
        super(name, filterChain);
        hostString = targetHost+":"+targetPort;
    }

    protected boolean handleRequest(HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
    {
        HTTPRequestHeaders headers = request.getHeaders(); 
        String path = headers.getRequestURL();
        if (!path.startsWith("/"))
            path = "/"+path;
       
        if (!headers.isGet() && !headers.isHead() && !headers.isPost())
            return false;
        boolean isPost = headers.isPost();

        URL url = new URL("http://"+hostString+path);
        HttpURLConnection proxy = (HttpURLConnection) url.openConnection();

        String[] keys = headers.getHeaderKeys();
        for (int i=0; i<keys.length; i++)
        {
            String value = headers.getHeader(keys[i]);
            proxy.setRequestProperty(keys[i], value);
        }
        
        proxy.setUseCaches(false);
        if (isPost)
        {
            proxy.setDoOutput(true);
            byte[] raw = request.readRawContent();

            OutputStream postOut = proxy.getOutputStream();
            postOut.write(raw);
            postOut.flush();
            postOut.close();
        }
        
        int respCode = proxy.getResponseCode();
        String msg = proxy.getResponseMessage();
        long length = proxy.getContentLengthLong();
        boolean isChunked = false;

        if (respCode != 200)
            return false;

        HTTPResponseHeaders respHeaders = response.getHeaders();
        for (int i=0; true; i++)
        {
            String key = proxy.getHeaderFieldKey(i);
            if (key == null)
            {
                if (i == 0)
                    continue;
                else
                    break;
            }

            String value = proxy.getHeaderField(i);
            if (key.equalsIgnoreCase("set-cookie"))
            {
                HttpCookie setCookie = HTTPResponseHeaders.parseSetCookie(value);
                respHeaders.setCookie(setCookie);
            }
            else
                respHeaders.setHeader(key, value);

            isChunked = key.equalsIgnoreCase("transfer-encoding") && value.equalsIgnoreCase("chunked");
        }

        InputStream data = null;
        try
        {
            if ((length < 0) && isChunked)
            {
                respHeaders.configure(respCode, msg);
                response.sendHeaders();
            }
            else
            {
                data = proxy.getInputStream();
                respHeaders.configure(respCode, msg);
                
                if ((length >= 0) && !isChunked)
                    response.prepareToSendContent(length, false);
                else
                    response.prepareToSendContent(-1, true);
                
                byte[] buffer = new byte[4096];
                while (true)
                {
                    int r = data.read(buffer);
                    if (r < 0)
                        break;
                    response.write(buffer, 0, r);
                }

                response.close();
                data.close();
            }
        }
        catch (Exception e) 
        {
            return false;
        }
        finally
        {
            try
            {
                data.close();
            }
            catch (Exception e) {}
        }

        return true;
    }
}
