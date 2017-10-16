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
import java.util.*;

import jjsp.util.*;

public class HTTPInputStream extends InputStream
{
    private static final int DEFAULT_MAX_TO_READ_ON_CLOSE = 4*1024;
    private static final int DEFAULT_MAX_POST_DATA_SIZE = 256*1024;

    private boolean isSecure;
    private int serverPort;
    private HTTPRequestHeaders headers;
    private InetSocketAddress clientAddress;
    private MeasurableInputStream src;
    private InputStream contentStream;

    public HTTPInputStream(int serverPort, boolean isSecure, InetSocketAddress address, InputStream src)
    {
        this.src = new MeasurableInputStream(src);
        this.isSecure = isSecure;
        this.serverPort = serverPort;
        this.clientAddress = address;

        headers = new HTTPRequestHeaders();
        contentStream = null;
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

    public HTTPRequestHeaders getHeaders()
    {
        return headers;
    }

    public long getReadTime()
    {
        return src.readTime;
    }

    public void resetReadTime()
    {
        src.measureTimeOfNextRead = true;
    }

    public long getBytesRead()
    {
        return src.totalBytesRead;
    }

    class MeasurableInputStream extends InputStream
    {
        boolean measureTimeOfNextRead;
        long readTime, totalBytesRead;

        private final InputStream src;

        MeasurableInputStream(InputStream src)
        {
            this.src = src;
            measureTimeOfNextRead = false;
            readTime = totalBytesRead = 0;
        }

        public int available() throws IOException
        {
            return src.available();
        }

        public void close() throws IOException
        {
            src.close();
        }

        private void bytesRead(long number)
        {
            if (measureTimeOfNextRead)
            {
                measureTimeOfNextRead = false;
                readTime = System.currentTimeMillis();
            }

            totalBytesRead += number;
        }

        public int read() throws IOException
        {
            int result = src.read();
            if (result < 0)
                return result;
            bytesRead(1);
            return result;
        }

        public int read(byte[] b) throws IOException
        {
            return read(b, 0, b.length);
        }

        public int read(byte[] b, int off, int len) throws IOException
        {
            int r = src.read(b, off, len);
            if (r <= 0)
                return r;
            bytesRead(r);
            return r;
        }

        public long skip(long toSkip) throws IOException
        {
            long result = src.skip(toSkip);
            if (result >= 0)
                bytesRead(result);
            return result;
        }
    }

    public boolean readHeaders() throws IOException
    {
        if (contentStream != null)
            contentStream.close();
        contentStream = null;

        if (!headers.readHeadersFromStream(src, clientAddress))
            return false;

        long len = headers.getContentLength();
        if (len >= 0)
            contentStream = new UnchunkedContentStream(len);
        else if (headers.getHeader("Content-Type", "").equals("chunked"))
            contentStream = new ChunkedContentStream();

        return true;
    }

    public void mark(int limit) {}

    public boolean markSupported()
    {
        return false;
    }

    public void reset() throws IOException
    {
        throw new IOException("Reset not supported");
    }

    public boolean contentAvailable()
    {
        return contentStream != null;
    }

    public int available() throws IOException
    {
        if (contentStream == null)
            return 0;
        return contentStream.available();
    }

    public int read() throws IOException
    {
        if (contentStream == null)
            return -1;
        return contentStream.read();
    }

    public int read(byte[] buffer) throws IOException
    {
        if (contentStream == null)
            return -1;
        return contentStream.read(buffer);
    }

    public int read(byte[] buffer, int off, int len) throws IOException
    {
        if (contentStream == null)
            return -1;
        return contentStream.read(buffer, off, len);
    }

    public long skip(long len) throws IOException
    {
        if (len < 0)
            return 0;
        if (contentStream == null)
            return -1;
        return contentStream.skip(len);
    }

    public void close() throws IOException
    {
        if (contentStream != null)
            contentStream.close();
        contentStream = null;
    }

    public void dispose() throws IOException
    {
        src.close();
    }

    public String readContent() throws IOException
    {
        return readContent(DEFAULT_MAX_POST_DATA_SIZE);
    }

    public String readContent(int maxLength) throws IOException
    {
        return Utils.toString(readRawContent(maxLength));
    }

    public byte[] readRawContent() throws IOException
    {
        return readRawContent(DEFAULT_MAX_POST_DATA_SIZE);
    }

    public byte[] readRawContent(int maxLength) throws IOException
    {
        if (contentStream == null)
            return new byte[0];

        try
        {
            if (contentStream instanceof UnchunkedContentStream)
            {
                int len = (int)((UnchunkedContentStream)contentStream).length;
                if (len >= maxLength)
                    throw new IOException("POST content too long ("+len+")");

                byte[] raw = new byte[len];
                int index = 0;
                while (index < raw.length)
                {
                    int read = contentStream.read(raw, index, raw.length - index);
                    if (read >= 0)
                        index += read;
                    else
                        break;
                }
                return raw;
            }
            else
            {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int read = 0;
                while ((read = contentStream.read(buf)) >= 0)
                {
                    bout.write(buf, 0, read);
                    if (bout.size() >= maxLength)
                        throw new IOException("POST content too long ("+bout.size()+")");
                }
                return bout.toByteArray();
            }
        }
        finally
        {
            try
            {
                close();
            }
            catch (Exception e) {}
        }
    }

    class ChunkedContentStream extends InputStream
    {
        private boolean eof;
        private byte[] lineBuffer;
        private int chunkLength, chunkPos;

        ChunkedContentStream() throws IOException
        {
            chunkPos = 0;
            chunkLength = 0;
            lineBuffer = new byte[512];
            eof = false;
            findNextChunk();
        }

        private boolean findNextChunk() throws IOException
        {
            if (eof)
                return false;

            int pos = HTTPRequestHeaders.readLine(src, lineBuffer);
            if (pos < 0)
                return false;
            if (pos == 2)
                pos = HTTPRequestHeaders.readLine(src, lineBuffer);

            chunkLength = -1;
            for (int i=0; i<pos; i++)
            {
                if (Character.digit((char) lineBuffer[i], 16) < 0)
                {
                    try
                    {
                        chunkLength = Integer.parseInt(new String(lineBuffer, 0, i), 16);
                        chunkPos = 0;
                        break;
                    }
                    catch (Exception e)
                    {
                        throw new IOException("Invalid chunk size");
                    }
                }
            }

            if (chunkLength < 0)
                throw new EOFException("Unexpected EOF reading chunk size");
            if (chunkLength > 0)
                return true;

            eof = true;
            if (!headers.readNextHeaderValuesFromStream(src))
                dispose();
            return false;
        }

        public int available() throws IOException
        {
            if (eof)
                return 0;
            return Math.min(chunkLength - chunkPos, src.available());
        }

        public int read() throws IOException
        {
            if ((chunkPos >= chunkLength) && !findNextChunk())
                return -1;
            int b = src.read();
            if (b >= 0)
                chunkPos++;
            return b;
        }

        public int read(byte[] b) throws IOException
        {
            return read(b, 0, b.length);
        }

        public int read(byte[] b, int off, int len) throws IOException
        {
            if (eof)
                return -1;
            int toRead = Math.min(b.length - off, len);
            if (toRead <= 0)
                return 0;

            int read = 0;
            while (true)
            {
                if (read >= toRead)
                    return read;

                if (eof || ((chunkPos >= chunkLength) && !findNextChunk()))
                {
                    if (read > 0)
                        return read;
                    close();
                    return -1;
                }

                int r = Math.min(toRead - read, chunkLength - chunkPos);
                r = src.read(b, off, r);
                if (r < 0)
                    throw new EOFException("EOF reading Chunk");

                read += r;
                off += r;
                chunkPos += r;
            }
        }

        public long skip(long s) throws IOException
        {
            if (eof || ((chunkPos >= chunkLength) && !findNextChunk()))
                return -1;

            long toSkip = Math.min(s, chunkLength - chunkPos);
            if (toSkip <= 0)
                return 0;
            long skipped = src.skip(toSkip);
            if (skipped > 0)
                chunkPos += (int) skipped;
            return skipped;
        }

        public void close() throws IOException
        {
            if (eof)
                return;

            long remain = DEFAULT_MAX_TO_READ_ON_CLOSE;
            for (int i=0; (remain>0) && (i<32); i++)
            {
                long s = skip(Integer.MAX_VALUE);
                if (s < 0)
                    break;
                remain -= s;
            }

            if (!eof)
            {
                dispose();
                throw new IOException("Failed to flush remaining chunked input on close");
            }
        }
    }

    class UnchunkedContentStream extends InputStream
    {
        private long length, pos;
        private boolean closed;

        public UnchunkedContentStream(long length)
        {
            pos = 0;
            closed = false;
            this.length = length;
        }

        public int available() throws IOException
        {
            if (closed)
                return 0;
            if (length < 0)
                return src.available();
            return (int) Math.min(length - pos, src.available());
        }

        public int read() throws IOException
        {
            if (closed)
                return -1;

            if (length >= 0)
            {
                if (pos >= length)
                {
                    close();
                    return -1;
                }
            }

            int b = src.read();
            if (b >= 0)
                pos++;
            else if (b < 0)
                close();
            return b;
        }

        public int read(byte[] b) throws IOException
        {
            return read(b, 0, b.length);
        }

        public int read(byte[] b, int off, int len) throws IOException
        {
            if (closed)
                return -1;

            int toRead = Math.min(b.length - off, len);
            if (length >= 0)
            {
                if (pos >= length)
                {
                    close();
                    return -1;
                }
                toRead = (int) Math.min(length - pos, toRead);
            }

            int read = src.read(b, off, toRead);
            if (read > 0)
                pos += read;
            else if (read < 0)
                close();
            return read;
        }

        public long skip(long s) throws IOException
        {
            if (closed)
                throw new EOFException();
            if (length < 0)
                return src.skip(s);

            s = src.skip(Math.min(s, length - pos));
            if (s > 0)
                pos += (int) s;
            if ((length > 0) && (pos >= length))
                close();
            return s;
        }

        public void close() throws IOException
        {
            if (closed)
                return;
            if (length < 0)
            {
                dispose();
                return;
            }

            long remain = Math.min(length - pos, DEFAULT_MAX_TO_READ_ON_CLOSE);
            while (remain > 0)
            {
                long s = src.skip(remain);
                if (s <= 0)
                    break;

                pos += s;
                remain -= s;
            }

            closed = true;
            if (pos < length)
            {
                dispose();
                throw new IOException("Failed to flush remaining input on close");
            }
        }
    }

    public Map getPostParameters() throws IOException
    {
        return getPostParameters(1024*1024);
    }

    /**
     * Extracts the parameters from a HTTP stream. It works for the following types of http parameters:
     * - get parameters
     * - standard post parameters
     * - multipart post parameters
     *
     * @return a map with paramName -&gt; paramValue
     * @throws IOException
     */
    public Map getPostParameters(int maxPostLength) throws IOException
    {
        HTTPRequestHeaders headers = getHeaders();
        String type = headers.getHeader("Content-Type", null);

        if(headers.isPost())
        {
            //load raw post data
            Map output = new HashMap();

            //multipart post
            if ((type != null) && type.contains("multipart/form-data"))
                output = getMultiPartFormData(maxPostLength);
            else //simple post
            {
                byte[] rawContent = readRawContent(maxPostLength);
                String data = Utils.toString(rawContent);
                if ( (type != null) && type.contains("application/x-www-form-urlencoded") )
                    data = URLDecoder.decode(data, "UTF-8");
                for(String param: data.split("&"))
                {
                    int equalPos = param.indexOf('=');
                    if(equalPos > -1)
                        output.put(param.substring(0,equalPos), param.substring(equalPos+1));
                }
            }

            return output;
        } //get
        else if (headers.isGet())
            return headers.parseHTTPQueryParameters(headers.getQueryString());
        else
            return new HashMap();
    }

    public Map getMultiPartFormData(int maxPostLength) throws IOException 
    {
        HTTPRequestHeaders headers = getHeaders();
        String type = headers.getHeader("Content-Type", null);
        if ( !headers.isPost() || type == null || !type.contains("multipart/form-data") )
            return new HashMap();
        
        Map results = new HashMap();
        byte[] rawContent = readRawContent(maxPostLength);

        HTMLFormPart[] formParts = HTMLFormPart.processPostedFormData(headers, rawContent);
        if ( formParts != null ) 
        {
            for ( HTMLFormPart formPart : formParts ) 
            {
                String name = formPart.getAttribute("name");

                if ( name.endsWith("[]") ) 
                {
                    HTMLFormPart[] array;
                    if ( results.containsKey(name) ) 
                    {
                        array = (HTMLFormPart[]) results.get(name);
                        array = Arrays.copyOf(array, array.length + 1);
                        array[array.length - 1] = formPart;
                    }
                    else
                        array = new HTMLFormPart[]{formPart};

                    results.put(name, array);
                }
                else
                    results.put(name, formPart);
            }
        }
        return results;
    }
}
