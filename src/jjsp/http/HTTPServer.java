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

import jjsp.util.*;
import jjsp.engine.*;
import jjsp.http.filters.*;

public class HTTPServer extends Server
{
    private volatile int timeout;

    private final HTTPServerLogger logger;
    private final HTTPRequestFilter mainFilter;

    public HTTPServer(HTTPRequestFilter filter, HTTPServerLogger logger)
    {
        this(filter, RECEIVE_BUFFER_SIZE, SEND_BUFFER_SIZE, logger);
    }

    public HTTPServer(HTTPRequestFilter filter, int recvBufferSize, int sendBufferSize, HTTPServerLogger logger)
    {
        super(recvBufferSize, sendBufferSize);
        this.mainFilter = filter;
        this.logger = logger;
    }

    public int getDefaultSocketTimeout()
    {
        return timeout; 
    }

    protected void setDefaultSocketTimeout(int timeout)
    {
        this.timeout = timeout;
    }

    protected void configureAcceptedSocket(Socket socket) throws SocketException
    {
        super.configureAcceptedSocket(socket);
        socket.setSoTimeout(timeout);
    }

    protected void errorOnSocketAccept(int serverPort, boolean isSecure, Throwable t)
    {
        if (logger != null)
            logger.socketException(-1, serverPort, isSecure, null, t);
    }

    protected void connectionError(InetSocketAddress clientAddress, int serverPort, boolean isSecure, Throwable t)
    {
        if (logger != null)
            logger.socketException(clientAddress.getPort(), serverPort, isSecure, clientAddress, t);
    }

    protected void configureResponseToInternalError(HTTPOutputStream requestOutput, Throwable t) throws IOException
    {
        requestOutput.getHeaders().configureAsNotFound();
    }

    protected void handleSocketStreams(InetSocketAddress clientAddress, int serverPort, boolean isSecure, InputStream input, OutputStream output) throws IOException
    {
        HTTPInputStream requestInput = new HTTPInputStream(serverPort, isSecure, clientAddress, input);
        HTTPOutputStream requestOutput = new HTTPOutputStream(serverPort, isSecure, clientAddress, output);

        ConnectionState state = new ConnectionState();
        try
        {
            while (true)
            {   
                String clientIP = "";
                long headersReadTime = 0;
                long readMark = requestInput.getBytesRead();
                long writeMark = requestOutput.getBytesWritten();
                
                requestInput.resetReadTime();
                requestOutput.resetWriteTime();

                try
                {
                    boolean headerTooLarge = !requestInput.readHeaders();
                    headersReadTime = System.currentTimeMillis();
                    clientIP = requestInput.getHeaders().getClientIPAddress();

                    if (headerTooLarge)
                    {
                        requestOutput.getHeaders().configureAsTooLarge();
                        requestOutput.sendHeaders();             
                        
                        if (logger != null)
                        {
                            long responseSent = System.currentTimeMillis();
                            long read = requestInput.getBytesRead() - readMark;
                            long written = requestOutput.getBytesWritten() - writeMark;
                            
                            HTTPLogEntry logEntry = new HTTPLogEntry(isSecure, clientIP, requestInput.getReadTime(), headersReadTime, requestOutput.getWriteTime(), responseSent, read, written, new HTTPFilterChain("HDRS_TOO_LARGE"), requestInput.getHeaders(), requestOutput.getHeaders());
                            logger.requestProcessed(logEntry);
                        }

                        throw new IOException("HTTP Header entity too large (HTTP 413)");
                    }
                }
                catch (EOFException e) 
                {
                    break;
                }

                HTTPRequestHeaders reqHdrs = requestInput.getHeaders();
                boolean isHTTP11 = reqHdrs.isHTTP11();

                if (isHTTP11 && reqHdrs.expectsContinueResponse())
                    HTTPResponseHeaders.sendContinueResponse(output);

                boolean closeConnection = !isHTTP11 || requestInput.getHeaders().closeConnection();
                if (closeConnection)
                    requestOutput.getHeaders().setConnectionClose();
                if (reqHdrs.isHead())
                    requestOutput.setResponseToHeadRequest(true);
                if (!isHTTP11)
                    requestOutput.setToLegacyHTTP();
                 
                HTTPFilterChain chain = mainFilter.filterRequest(null, requestInput, requestOutput, state);
                
                Throwable primaryError = chain.getPrimaryError();
                try
                {
                    if ((primaryError != null) && !requestOutput.outputSent())
                    {
                        requestOutput.resetForNextResponse();
                        configureResponseToInternalError(requestOutput, primaryError);
                        requestOutput.sendHeaders();
                    }
                    else if (!requestOutput.outputSent())
                    {
                        requestOutput.getHeaders().configureAsNotFound();
                        requestOutput.sendHeaders();
                    }
                }
                catch (Throwable e) { /* Else we can't do much cause there's already a better error to report! */}

                long responseSent = System.currentTimeMillis();
                long read = requestInput.getBytesRead() - readMark;
                long written = requestOutput.getBytesWritten() - writeMark;
                
                if (logger != null)
                {
                    HTTPLogEntry logEntry = new HTTPLogEntry(isSecure, clientIP, requestInput.getReadTime(), headersReadTime, requestOutput.getWriteTime(), responseSent, read, written, chain, requestInput.getHeaders(), requestOutput.getHeaders());
                    logger.requestProcessed(logEntry);
                }

                if (requestOutput.isDisposed())
                    break;

                requestInput.close();
                requestOutput.close();
                requestOutput.resetForNextResponse();
            
                if (closeConnection)
                    break;
            }
        }
        finally
        {
            try
            {
                state.close();
            }
            catch (Exception e) {}
        }
    }

    public static void main(String[] args) throws Exception
    {
        java.security.Security.setProperty("networkaddress.cache.ttl", "3600");
        Args.parse(args);

        int port = Args.getInt("port", 80);
        int sslPort = Args.getInt("sslport", 443);

        String webDir = System.getProperty("user.dir")+"/"+Args.getArg("webroot", "");
        webDir = webDir.replace("//", "/");
        webDir = webDir.replace("//", "/");

        int timeout = Args.getInt("timeout", DEFAULT_SOCKET_TIMEOUT);
        if (timeout < 1000)
            timeout *= 1000;

        int maxUpload = Args.getInt("maxUpload", 8);
        int cacheMinutes = Args.getInt("cacheFor", 1);
        boolean cacheContent = Args.getBoolean("useCache", false);
        boolean allowUpload = Args.getBoolean("allowUpload", false);
        boolean allowDeletion = Args.getBoolean("allowDeletion", false) || Args.getBoolean("allowDelete", false);
        boolean localhost = Args.getBoolean("localhost", false);
        String host = Args.getArg("host", null);
        boolean debugMode = Args.getBoolean("debug", false);
        boolean debugHTTP  = Args.getBoolean("debugHTTP", false);
        boolean printExceptions  = Args.getBoolean("exceptions", false);
        int maxExceptionLines  = Args.getInt("exceptionLines", 10);

        System.setProperty("javax.net.ssl.keyStore", Args.getArg("keyStore", "serverkeystore.jks"));
        System.setProperty("javax.net.ssl.keyStorePassword", Args.getArg("keyStorePassword", "GTVrocks!"));

        DirectoryFilter mainDir = null;
        if (allowUpload)
        {
            FileUploadFilter ff = new FileUploadFilter(new File(webDir), allowDeletion, null);
            if (maxUpload < 1024)
                maxUpload *= 1024*1024;
            ff.setMaxUploadSize(maxUpload);
            mainDir = ff;
        }
        else
            mainDir = new DirectoryFilter(new File(webDir), null);
        mainDir.setCacheTime(cacheMinutes * 60);
        mainDir.setUseCache(cacheContent);

        HTTPRequestFilter mainFilter = mainDir;
        String indexFileName = Args.getArg("index", null);
        if (indexFileName != null)
        {
            File indexFile = new File(webDir, indexFileName);
            byte[] indexBytes = Utils.load(new FileInputStream(indexFile));
            mainFilter = new StaticDataFilter("TopIndex", "/", indexBytes, mainDir);
        }

        PrintStreamLogger logger = new PrintStreamLogger(maxExceptionLines, debugMode || printExceptions, debugMode || debugHTTP);
        HTTPServer server = new HTTPServer(mainFilter, logger);
        server.setDefaultSocketTimeout(timeout);
        
        InetAddress addr = null;
        try
        {
            if (localhost)
                addr = MyIPAddress.getLocalhost();
            else if (host != null)
                addr = InetAddress.getByName(host);
        }
        catch (Exception e) {}

        server.listenOn(port, false, addr);
        System.out.println("Server listening on "+addr+":"+port+" with root at "+webDir);

        if (sslPort > 0)
        {
            try
            {
                server.listenOn(sslPort, true, addr);
                System.out.println("Server listening on "+addr+":"+port+" with root at "+webDir);
            }
            catch (Exception e)
            {
                System.out.println("Server not listening on SSL Port "+sslPort+": "+e);
            }
        }
    }
}
