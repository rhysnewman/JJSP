package jjsp.http.filters;

import jjsp.http.*;

import java.io.*;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Function;

public class ProxyFilter extends AbstractRequestFilter {
    // getFullForwardPath: a lambda to create a complete url to be forwarded, from the requested proxy path
    // htmlEditor: if not null, this lambda will edit all 'text/html' content before it's returned by the proxy
    private Function getFullForwardPath, htmlEditor;

    public ProxyFilter(String name, Function getFullForwardPath, Function htmlEditor, HTTPRequestFilter filterChain) {
        super(name, filterChain);
        this.getFullForwardPath = getFullForwardPath;
        this.htmlEditor = htmlEditor;
    }

    public ProxyFilter(String name, String host, HTTPRequestFilter filterChain) {
        super(name, filterChain);
        this.getFullForwardPath = (path) -> host + path;
        this.htmlEditor = null;
    }

    @Override
    protected boolean handleRequest(HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException {
        HTTPRequestHeaders headers = request.getHeaders();
        String path = headers.getRequestURL();
        if (!path.startsWith("/"))
            path = "/"+path;

        if (!headers.isGet() && !headers.isHead() && !headers.isPost())
            return false;
        boolean isPost = headers.isPost();

        String urlString = (String) getFullForwardPath.apply(path);
        if ( urlString == null ) // if no forward path is found, return false to continue with the filter chain
            return false;

        URL url = new URL(urlString);
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

        try {
            proxy.connect();
        }
        catch ( Exception ex ) {
            throw new IOException("Failed to forward traffic to " + urlString, ex);
        }

        int respCode = proxy.getResponseCode();
        String msg = proxy.getResponseMessage();
        boolean isChunked = false;

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

            if ( key.equalsIgnoreCase("transfer-encoding") ) {
                if ( value.equalsIgnoreCase("chunked") )
                    isChunked = true;
            }
        }

        InputStream dataStream = null;
        try
        {
            try {
                dataStream = proxy.getInputStream();
            }
            catch ( IOException ex ) {
                // HttpURLConnection.getInputStream will throw a FileNotFoundException if the response if not 200
                // use the error stream to read any useful returned content (e.g. 404 page or 500 stack track) and forward to the client
                dataStream = proxy.getErrorStream();
            }

            respHeaders.configure(respCode, msg);
            if ( dataStream == null ) {// send headers only if there is no content
                response.sendHeaders();
                return true;
            }

            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int nRead;
            while ( ( nRead = dataStream.read(buffer, 0, buffer.length) ) != -1 )
                bout.write(buffer, 0, nRead);
            bout.flush();

            byte[] data = bout.toByteArray();
            String contentType = proxy.getContentType();
            if ( htmlEditor != null && contentType.toLowerCase().contains("text/html") )
                data = editHTML(data);

            if ( data.length > 0 ) {
                response.prepareToSendContent(data.length, isChunked);
                response.write(data);
            }
            else
                response.sendHeaders();
        }
        finally
        {
            try
            {
                response.close();
                if ( dataStream != null )
                    dataStream.close();
            }
            catch (Exception e) {}
        }

        return true;
    }

    private byte[] editHTML(byte[] data) {
        String html = new String(data);
        html = (String) htmlEditor.apply(html);
        return html.getBytes();
    }

}
