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
package jjsp.http.filters;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;

import jjsp.http.*;
import jjsp.util.*;

public class DirectoryFilter extends AbstractRequestFilter
{
    public static final int CACHE_SIZE_LIMIT = 8*1024;
    public static final int CACHEABLE_DATA_LIMIT = 8*1024*1024;

    protected File rootDirectory;
    protected boolean useCache;
    protected LinkedHashMap cache;
    protected int cacheTime;
    protected String pathPrefix;
    
    private volatile int cacheSize = 0;
    private volatile long lastFlush = 0;

    public DirectoryFilter(File directory, HTTPRequestFilter filterChain) throws IOException
    {
        this(directory, "", filterChain);
    }

    public DirectoryFilter(File directory, String pathPrefix, HTTPRequestFilter filterChain) throws IOException
    {
        super("DIR("+directory.getName()+")", filterChain);

        if (!directory.exists())
            throw new IOException(directory+" does not exist");
        if (!directory.isDirectory())
            throw new IOException(directory+" is not a directory");
        
        rootDirectory = directory;
        cacheTime = 3600;
        useCache = false;
        cache = new LinkedHashMap(1024, 0.7f, true);
        setPathPrefix(pathPrefix);
    }

    public void setPathPrefix(String prefix)
    {
        if ((prefix == null) || (prefix.length() == 0))
            pathPrefix = "";
        else if (!prefix.startsWith("/"))
            pathPrefix = "/"+prefix;
        else
            pathPrefix = prefix;
    }

    public void setUseCache(boolean value)
    {
        useCache = value;
    }

    /** Controls the value of the "Cache" header in the HTTP response. The client will cache the response for this many seconds, or if cacheTimeSeconds is -1 then clients will not cache at all. By default the cache time is 3600 seconds (1 hour)*/
    public void setCacheTime(int cacheTimeSeconds)
    {
        cacheTime = cacheTimeSeconds;
    }

    public static abstract class DataSource
    {
        public abstract long length();

        public abstract String getETag();
        
        public abstract long getLastModified();
        
        public void checkBounds(long startPos, long endPos) throws IOException
        {
            long len = length();

            if ((startPos < 0) || (startPos >= len))
                throw new IOException("Invalid start position "+startPos);
            if ((endPos < 0) || (endPos > len))
                throw new IOException("Invalid end position "+endPos);
            if (startPos > endPos)
                throw new IOException("Start after end");
        }

        public abstract void streamTo(long startPos, long endPos, OutputStream out) throws IOException;

        public void dispose() {}
    }

    public static class ByteArrayDataSource extends DataSource
    {
        String eTag;
        byte[] data;
        long created;

        public ByteArrayDataSource(byte[] data)
        {
            this.data = data;
            created = System.currentTimeMillis()/1000*1000;
            eTag = "ET"+created;
        }

        public long length()
        {
            return data.length;
        }

        public String getETag()
        {
            return eTag;
        }

        public long getLastModified()
        {
            return created;
        }

        public void streamTo(long startPos, long endPos, OutputStream out) throws IOException
        {
            checkBounds(startPos, endPos);
            out.write(data, (int) startPos, (int) (endPos - startPos));
        }
    }

    public class FileSource extends DataSource
    {
        final File f;

        public FileSource(File f) throws IOException
        {
            this.f = f;
        }

        public long getLastModified()
        {
            return f.lastModified();
        }

        public String getETag()
        {
            return "LF"+f.lastModified();
        }

        public long length()
        {
            return f.length();
        }

        public void streamTo(long startPos, long endPos, OutputStream out) throws IOException
        {
            checkBounds(startPos, endPos);
            
            RandomAccessFile raf = null;
            try
            {
                raf = new RandomAccessFile(f, "r");
            
                byte[] buffer = new byte[32*1024];
                raf.seek(startPos);
                
                while (true)
                {
                    int toRead = (int) Math.min(buffer.length, endPos - startPos);
                    if (toRead <= 0)
                        break;
                    raf.readFully(buffer, 0, toRead);
                    out.write(buffer, 0, toRead);
                    startPos += toRead;
                }
            }
            finally
            {
                try
                {
                    raf.close();
                }
                catch (Exception e) {}
            }
        }
    }

    protected boolean accessPermitted(File f)
    {
        boolean isChild = false;
        for (File parent = f; parent != null; parent = parent.getParentFile())
            if (rootDirectory.equals(parent))
                return true;
        return false;
    }

    protected DataSource getDataSource(String path) throws IOException
    {
        synchronized (cache)
        {
            DataSource ds = (DataSource) cache.get(path);
            if (ds != null)
                return ds;

            File f = new File(rootDirectory, path);
            if (!f.exists() || !f.isFile() || !accessPermitted(f))
                return null;

            if (!useCache || (f.length() > CACHEABLE_DATA_LIMIT))
                ds = new FileSource(f);
            else
                ds = new ByteArrayDataSource(Utils.load(f));

            if (cache.size() > CACHE_SIZE_LIMIT)
            {
                Iterator itt = cache.keySet().iterator();
                itt.next();
                for (int i=0; itt.hasNext() && (i<100); i++)
                {
                    DataSource dss = (DataSource) itt.next();
                    dss.dispose();
                    itt.remove();
                }
                lastFlush = 0;
            }

            cache.put(path, ds);             
            return ds;
        }
    }

    public static String formatLength(long length)
    {
        if (length < 1024)
            return String.valueOf(length);
        else if (length < 1024*1024)
            return (length/1024)+" k";
        else if (length < 1024l*1024*1024)
            return (length/(1024*1024))+" m";
        else
            return (length/(1024l*1024*1024))+" g";
    }

    protected DataSource getDirectoryHTMLListPage(File directory)
    {
        return getBasicDirectoryHTMLListPage(directory);
    }

    protected boolean includeFileInDirectoryList(File f)
    {
        return true;
    }

    protected String getResourcePathForRequestPath(String path)
    {
        if (!path.startsWith(pathPrefix))
            return null;
        return path.substring(pathPrefix.length());
    }

    protected File[] selectFilesFromDirectory(File directory)
    {
        File[] files = directory.listFiles();

        boolean subset = false;
        for (int i=0; i<files.length; i++)
        {
            if (!includeFileInDirectoryList(files[i]))
            {
                subset = true;
                break;
            }
        }

        if (!subset)
            return files;

        ArrayList ll = new ArrayList();
        for (int i=0; i<files.length; i++)
        {
            if (!includeFileInDirectoryList(files[i]))
                continue;
            ll.add(files[i]);
                
            files = new File[ll.size()];
            ll.toArray(files);
        }

        return files;
    }

    public DataSource getBasicDirectoryHTMLListPage(File directory)
    {
        byte[] directoryHTML = generateHTMLDirectoryListing(pathPrefix, rootDirectory, selectFilesFromDirectory(directory));
        return new ByteArrayDataSource(directoryHTML);
    }

    public static String relativePath(File dir, File file)
    {
        String p2 = file.getAbsolutePath().replace("\\", "/");
        if (file.isDirectory() && !p2.endsWith("/"))
            p2 = p2+"/";
        if (!p2.startsWith("/"))
            p2 = "/"+p2;

        if (dir != null)
        {
            String p1 = dir.getAbsolutePath().replace("\\", "/");
            if (dir.isDirectory() && !p1.endsWith("/"))
                p1 = p1+"/";
            if (!p1.startsWith("/"))
                p1 = "/"+p1;
            
            if (!p2.startsWith(p1))
                return p2;
            
            p2 = p2.substring(p1.length());
            if (!p2.startsWith("/"))
                p2 = "/"+p2;
        }

        return p2;
    }

    public static byte[] generateHTMLDirectoryListing(String pathPrefix, File root, File[] files)
    {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd    HH:mm:ss", DateFormatSymbols.getInstance(Locale.US));
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bout);
        
        ps.println("<HTML><HEAD><style>body {font-family:verdana, arial, helvetica, sans-serif; border:none}</style></head><BODY><TABLE>");
        ps.println("<TR><TH width=40></TH><TH width=500 align=left>File Name</TH><TH width=250 align=right>Modified</TH><TH width=100 align=right>Size</TH></TR>");
        for (int i=0; i<files.length; i++)
        {
            String relativePath = relativePath(root, files[i]);

            String lastModified = dateFormat.format(new Date(files[i].lastModified()));
            ps.print("<TR>");
            ps.print("<TD>");
            ps.print(String.valueOf(i+1));
            ps.print("</TD>");
            ps.print("<TD><a href=\""+pathPrefix+relativePath+"\">"+Utils.URLDecode(relativePath.substring(1))+"</a></TD>");
            ps.print("<TD align=right>");
            ps.print(lastModified);
            ps.print("</TD>");
            
            if (files[i].isDirectory())
                ps.print("<TD align=right><font color=green>DIR</font></TD>");
            else
            {
                ps.print("<TD align=right>");
                ps.print(formatLength(files[i].length()));
                ps.print(" </TD>");
            }
            ps.println("</TR>");
        }
        ps.println("</TABLE></BODY></HTML>");
        ps.flush();
        return bout.toByteArray();
    }

    protected String handleHeadRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException 
    {
        HTTPRequestHeaders reqHeaders = request.getHeaders();
        HTTPResponseHeaders respHeaders = response.getHeaders();

        String urlPath = reqHeaders.getPath();
        String pathString = getResourcePathForRequestPath(urlPath);
        if (pathString == null)
            return null;

        DataSource ds = getDataSource(pathString);
        if (ds != null)
        {
            long modified = ds.getLastModified();
            long ifModifiedSince = reqHeaders.getIfModifiedSinceTime();
            long[] limits = reqHeaders.extractByteRanges();

            respHeaders.configureCacheControl(ds.getETag(), modified, cacheTime);
            respHeaders.guessAndSetContentType(pathString);
            
            if ((ifModifiedSince > 0) && (modified <= ifModifiedSince) && (limits == null))
                respHeaders.configureAsNotModified();
            else
            {
                long start = 0;
                long end = ds.length();
                
                if (limits != null)
                {
                    if (limits[1] > 0)
                        end = Math.min(limits[1], end);
                    start = Math.max(0, Math.min(end, limits[0]));
                    respHeaders.configureAsPartialContent(start, end, ds.length());
                }
                else
                    respHeaders.configureAsOK();
                    
                response.prepareToSendContent(end-start, false);
            }
        }
        else
        {
            File f = new File(rootDirectory, pathString);
            if (f.exists() && f.isDirectory() && accessPermitted(f))
            {
                ds = getDirectoryHTMLListPage(f);
                respHeaders.configureAsOK();
                respHeaders.configureCacheControl(ds.getETag(), ds.getLastModified(), -1);
                respHeaders.setContentType("text/html");
            }
            else
                return null;
        }

        response.sendHeaders();
        return urlPath;
    }

    protected String handleGetRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException 
    {
        HTTPRequestHeaders reqHeaders = request.getHeaders();
        HTTPResponseHeaders respHeaders = response.getHeaders();
        
        String urlPath = reqHeaders.getPath();
        String pathString = getResourcePathForRequestPath(urlPath);
        if (pathString == null)
            return null;

        DataSource ds = getDataSource(pathString); 
        long start = 0, end = -1;

        if (ds != null)
        {
            long modified = ds.getLastModified();
            long ifModifiedSince = reqHeaders.getIfModifiedSinceTime();
            long[] limits = reqHeaders.extractByteRanges();
            respHeaders.configureCacheControl(ds.getETag(), modified, cacheTime);
            respHeaders.guessAndSetContentType(pathString);
            
            if ((ifModifiedSince > 0) && (modified <= ifModifiedSince) && (limits == null))
                respHeaders.configureAsNotModified();
            else
            {
                end = ds.length();
                
                if (limits != null)
                {
                    if (limits[1] > 0)
                        end = Math.min(limits[1], end);
                    start = Math.max(0, Math.min(end, limits[0]));
                    respHeaders.configureAsPartialContent(start, end, ds.length());
                }
                else
                    respHeaders.configureAsOK();
                    
                response.prepareToSendContent(end-start, false);
            }
        }
        else
        {
            File f = new File(rootDirectory, pathString);
            if (f.exists() && f.isDirectory() && accessPermitted(f))
            {
                ds = getDirectoryHTMLListPage(f);
                end = ds.length();

                respHeaders.configureAsOK();
                respHeaders.configureCacheControl(ds.getETag(), ds.getLastModified(), -1);
                respHeaders.setContentType("text/html");
                response.prepareToSendContent(end-start, false);
            }
        }
        
        if (ds == null)
            return null;

        if (end < 0)
            response.sendHeaders();
        else
            ds.streamTo(start, end, response);

        return urlPath;
    }

    protected String handlePostRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException 
    {
        return null;
    }

    protected boolean handleRequestAndReport(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
    {
        String report = null;
        HTTPRequestHeaders reqHeaders = request.getHeaders();

        if (reqHeaders.isGet())
            report = handleGetRequest(chain, request, response, state);
        else if (reqHeaders.isHead())
            report = handleHeadRequest(chain, request, response, state);
        else if (reqHeaders.isPost())
            report = handlePostRequest(chain, request, response, state);
        else
            throw new IllegalStateException("Unimplemented HTTP Method for "+reqHeaders.getMainLine());

        if (report != null)
        {
            chain.report = report;
            return true;
        }
        else
            return false;
    }
}
