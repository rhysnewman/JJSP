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
import java.util.*;
import java.net.*;

import jjsp.util.*;

public class HTTPOutputStream extends OutputStream
{   
    private int serverPort;
    private OutputStream contentStream;
    private MeasurableOutputStream dest;
    private HTTPResponseHeaders headers;
    private InetSocketAddress clientAddress;
    private boolean isSecure, isResponseToHeadRequest, outputSent, legacyHTTP, isDisposed;

    public HTTPOutputStream(int serverPort, boolean isSecure, InetSocketAddress address, OutputStream dest)
    {
        this.dest = new MeasurableOutputStream(dest);
        this.isSecure = isSecure;
        this.serverPort = serverPort;
        this.clientAddress = address;
        
        outputSent = false;
        isDisposed = false;
        legacyHTTP = false;
        isResponseToHeadRequest = false;
        headers = new HTTPResponseHeaders();
        contentStream = null; 
    }

    class MeasurableOutputStream extends OutputStream
    {
        boolean registerNextWriteTime;
        long writeTime, totalWritten;

        private final OutputStream dest;

        MeasurableOutputStream(OutputStream out)
        {
            dest = out;
            registerNextWriteTime = false;
            writeTime = totalWritten = 0;
        }

        public void close() throws IOException
        {
            dest.close();
        }

        public void flush() throws IOException
        {
            dest.flush();
        }

        private void bytesWritten(int w)
        {
            totalWritten += w;

            if (registerNextWriteTime)
            {
                registerNextWriteTime = false;
                writeTime = System.currentTimeMillis();
            }
        }

        public void write(byte[] b) throws IOException
        {
            dest.write(b);
            bytesWritten(b.length);
        }
        
        public void write(byte[] b, int off, int len) throws IOException
        {
            dest.write(b, off, len);
            bytesWritten(len);
        }
         
        public void write(int b) throws IOException
        {
            dest.write(b);
            bytesWritten(1);
        }
    }

    public void resetWriteTime()
    {
        dest.registerNextWriteTime = true;
    }

    public long getWriteTime()
    {
        return dest.writeTime;
    }

    public long getBytesWritten()
    {
        return dest.totalWritten;
    }

    public boolean isDisposed()
    {
        return isDisposed;
    }

    public int getServerPort()
    {
        return serverPort;
    }

    public boolean isSecure()
    {
        return isSecure;
    }

    public InetSocketAddress getClientAddress()
    {
        return clientAddress;
    }

    public boolean isResponseToHeadRequest()
    {
        return isResponseToHeadRequest;
    }

    public void setResponseToHeadRequest(boolean value)
    {
        isResponseToHeadRequest = value;
    }
    
    public void setToLegacyHTTP()
    {
        legacyHTTP = true;
    }

    public HTTPResponseHeaders getHeaders()
    {
        return headers;
    }
    
    public boolean outputSent()
    {
        return outputSent;
    }

    public void resetForNextResponse()
    {
        outputSent = false;
        isResponseToHeadRequest = false;
        legacyHTTP = false;
        headers.clear();

        try
        {
            if (contentStream != null)
                contentStream.close();
            contentStream = null;
        }
        catch (Exception e) {}
    }
    
    public void sendServerErrorMessage(String headerMessage, Throwable t) throws IOException
    {
        sendServerErrorMessage(headerMessage, Utils.stackTraceString(t));
    }
    
    public void sendServerErrorMessage(String headerMessage, String bodyContent) throws IOException
    {
        headers.configureAsServerError("Internal Server Error "+ headerMessage);
        sendContent(bodyContent, "text/plain");
    }

    public void sendHTML(String content) throws IOException
    {
        sendContent(content, "text/html; charset=utf-8");
    }

    public void sendJSON(String content) throws IOException
    {
        sendContent(content, "application/json; charset=utf-8");
    }

    public void sendJavscript(String content) throws IOException
    {
        sendContent(content, "application/javascript; charset=utf-8");
    }

    public void gzipAndSendJSON(String uncompressedJSON) throws IOException
    {
        gzipAndSend(Utils.getAsciiBytes(uncompressedJSON), "application/json; charset=utf-8");
    }

    public void gzipAndSendHTML(String uncompressedHTML) throws IOException
    {
        gzipAndSend(Utils.getAsciiBytes(uncompressedHTML), "text/html; charset=utf-8");
    }

    public void gzipAndSend(byte[] uncompressedContent, String contentType) throws IOException
    {
        byte[] zippedContent = Utils.gzip(uncompressedContent);
        sendContent(zippedContent, contentType, "gzip");
    }

    public void sendContent(String content) throws IOException
    {
        sendContent(content, null);
    }

    public void sendContent(byte[] content) throws IOException
    {
        sendContent(content, null);
    }

    public void sendContent(String content, String contentType) throws IOException
    {
        sendContent(Utils.getAsciiBytes(content), contentType);
    }

    public void sendContent(byte[] content, String contentType) throws IOException
    {
        sendContent(content, contentType, null);
    }

    public void sendGZippedContent(byte[] content, String contentType) throws IOException
    {
        sendContent(content, contentType, "gzip");
    }

    public void sendContent(byte[] content, String contentType, String contentEncoding) throws IOException
    {
        if (content != null)
            sendContent(content, 0, content.length, contentType, contentEncoding);
        else
            sendContent(null, 0, 0, contentType, contentEncoding);
    }

    public void sendContent(byte[] content, int off, int len) throws IOException
    {
        sendContent(content, off, len, null);
    }

    public void sendContent(byte[] content, int off, int len, String contentType) throws IOException
    {
        sendContent(content, off, len, contentType, null);
    }

    public void sendContent(byte[] content, int off, int len, String contentType, String contentEncoding) throws IOException
    {
        if (contentType != null)
            headers.setContentType(contentType);
        else if (!headers.contentTypeConfigured())
            headers.setContentType("text/html; charset=utf-8");

        if (contentEncoding != null)
            headers.setContentEncoding(contentEncoding);
            
        if (content == null)
        {
            prepareToSendContent(0, false);
            close();
        }
        else
        {
            len = Math.min(content.length - off, len);
            prepareToSendContent(len, false);
            write(content, off, len);
            close();
        }
    }

    public void sendHeaders() throws IOException
    {
        if (!headers.responseCodeConfigured())
            headers.configureAsOK();
        if (!headers.contentTypeConfigured())
            headers.setContentType("text/html; charset=utf-8");

        if (contentStream != null)
            contentStream.close();
        contentStream = null;

        outputSent = true;
        if (legacyHTTP)
            headers.convertToHTTP10();
        if (!headers.hasHeader("Content-Length"))
            headers.setHeader("Content-Length", "0");

        if (!headers.cacheControlConfigured())
            headers.configureToPreventCaching();

        headers.printToStream(dest);
        dest.flush();
    }

    public void prepareToSendContent(long contentLength, boolean isChunked) throws IOException
    {
        if (outputSent)
            throw new IOException("Already sending output");

        if (!headers.responseCodeConfigured())
            headers.configureAsOK();
        if (!headers.cacheControlConfigured())
            headers.configureToPreventCaching();
        if (!headers.contentTypeConfigured())
            headers.setContentType("text/html");
        
        if (contentStream != null)
            contentStream.close();
        contentStream = null;

        if (isChunked)
        {
            headers.deleteHeader("Content-Length");
            if (legacyHTTP)
                headers.setConnectionClose();
            else
                headers.setHeader("Transfer-Encoding", "Chunked");
        }
        else if (contentLength >= 0)
        {
            headers.setHeader("Content-Length", String.valueOf(contentLength));
            headers.deleteHeader("Transfer-Encoding");
        }

        outputSent = true;
        if (legacyHTTP)
            headers.convertToHTTP10();
        headers.printToStream(dest);

        if (isResponseToHeadRequest)
            contentStream = new DummyOutputStream();
        else if (isChunked)
        {
            if (legacyHTTP)
                contentStream = dest;
            else
                contentStream = new ChunkedOutputStream();
        }
        else
            contentStream = new FixedLengthOutputStream(contentLength);
    }

    public void write(int b) throws IOException
    {
        try
        {
            contentStream.write(b);
        }
        catch (NullPointerException e)
        {
            throw new IOException("HTTP Output stream not configured for streaming output");
        }
    }

    public void write(byte[] b) throws IOException
    {
        try
        {
            contentStream.write(b);
        }
        catch (NullPointerException e)
        {
            throw new IOException("HTTP Output stream not configured for streaming output");
        }
    }

    public void write(byte[] b, int off, int len) throws IOException 
    {
        try
        {
            contentStream.write(b, off, len);
        }
        catch (NullPointerException e)
        {
            throw new IOException("HTTP Output stream not configured for streaming output");
        }
    }

    public void flush() throws IOException
    {
        if (contentStream != null)
            contentStream.flush();
    }

    public void close() throws IOException
    {
        if (contentStream != null)
            contentStream.close();
        contentStream = null;
    }

    public boolean contentStreamClosed()
    {
        return contentStream == null;
    }

    public void dispose() throws IOException
    {
        isDisposed = true;
        close();
    }

    class FixedLengthOutputStream extends OutputStream
    {
        private boolean closed;
        private long pos, length;
        
        FixedLengthOutputStream(long length)
        {
            pos = 0;
            this.length = length;
        }

        public void close() throws IOException
        {
            if (closed)
                return;
            dest.flush();
            if (pos != length)
                throw new IOException("Premature close of output stream");
            closed = true;
        }

        public void flush() throws IOException
        {
            if (!closed)
                dest.flush();
        }

        public void write(byte[] b) throws IOException
        {
            write(b, 0, b.length);
        }

        public void write(byte[] b, int off, int len) throws IOException 
        {
            if (closed)
                throw new EOFException("Stream closed");
            int toWrite = Math.min(b.length - off, len);
            if (toWrite <= 0)
                return;
            toWrite = (int) Math.min(length - pos, toWrite);
            if (toWrite <= 0)
            {
                close();
                throw new EOFException("Length limit reached");
            }
            
            dest.write(b, off, toWrite);
            pos += toWrite;
        }
 
        public void write(int b) throws IOException
        {
            if (closed)
                throw new EOFException("Stream closed");
            if (pos >= length)
            {
                close();
                throw new EOFException("Length limit reached");
            }
            dest.write(b);
            pos++;
        }
    }

    class ChunkedOutputStream extends OutputStream
    {
        private boolean closed;

        public ChunkedOutputStream()
        {
            closed = false;
        }

        private void print(String s) throws IOException
        {
            int len = s.length();
            for (int i=0; i<len; i++)
                dest.write((byte) s.charAt(i));
        }

        public void close() throws IOException
        {
            if (closed)
                return;
            closed = true;
            print("0\r\n\r\n");
            dest.flush();
        }

        public void flush() throws IOException
        {
            if (!closed)
                dest.flush();
        }

        public void write(byte[] b) throws IOException
        {
            write(b, 0, b.length);
        }

        public void write(byte[] b, int off, int len) throws IOException 
        {
            if (closed)
                throw new EOFException("Chunked output stream closed");

            int toWrite = Math.min(b.length - off, len);
            if (toWrite <= 0)
                return;

            print(Integer.toHexString(toWrite));
            print("\r\n");
            dest.write(b, off, toWrite);
            print("\r\n");
        }
 
        public void write(int b) throws IOException
        {  
            if (closed)
                throw new EOFException("Chunked output stream closed");

            print("1\r\n");
            dest.write(b);
            print("\r\n");
        }
    }

    /** A empty stream implementation used when the HTTP request is a HEAD request */
    class DummyOutputStream extends OutputStream
    {
        private boolean closed;

        public DummyOutputStream()
        {
            closed = false;
        }

        public void close() throws IOException
        {
            closed = true;
        }

        public void flush() throws IOException
        {
        }

        public void write(byte[] b) throws IOException
        {
            if (closed)
                throw new EOFException();
        }

        public void write(byte[] b, int off, int len) throws IOException 
        {
            if (closed)
                throw new EOFException();
        }
 
        public void write(int b) throws IOException
        {  
            if (closed)
                throw new EOFException();
        }
    }
}
