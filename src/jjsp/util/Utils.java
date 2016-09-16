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
package jjsp.util;

import java.io.*;
import java.nio.charset.*;
import java.net.*;
import java.security.*;
import java.util.*;
import java.util.function.*;
import java.util.zip.*;
import java.util.jar.*;

public class Utils
{
    public static final String DEFAULT_VERSION_STRING = "5.04";
    public static final Charset ASCII = Charset.forName("US-ASCII");

    public static String toAsciiString(byte[] rawText)
    {
        return toString(rawText);
    }

    public static String toString(byte[] rawText)
    {
        if (rawText == null)
            return null;
        return toString(rawText, 0, rawText.length);
    }

    public static byte[] getAsciiBytes(String src)
    {
        if (src == null)
            return null;
        return src.getBytes(ASCII);
    }

    public static String toAsciiString(byte[] rawText, int offset, int length)
    {
        return toString(rawText, offset, length);
    }

    public static URI toURI(String s)
    {
        try
        {
            return new URI(s);
        }
        catch (Exception e) {}
        return null;
    }

    public static String toString(byte[] rawText, int offset, int length)
    {
        if (rawText == null)
            return null;
        return new String(rawText, offset, length, ASCII);
    }

    public static byte[] load(File src) throws IOException
    {
        int len = (int) src.length();
        byte[] result = new byte[len];
        DataInputStream din = new DataInputStream(new FileInputStream(src));
        try
        {
            din.readFully(result);
            return result;
        }
        finally
        {
            din.close();
        }
    }

    public static byte[] load(URL url) throws IOException
    {
        return load(url.openStream());
    }

    public static byte[] load(URI uri) throws Exception
    {
        return load(uri.toURL());
    }

    public static URLClassLoader getClassLoaderFor(Object ref)
    {
        if (ref == null)
            return (URLClassLoader) Utils.class.getClassLoader();
        if (ref instanceof URLClassLoader)
            return (URLClassLoader) ref;

        try
        {
            Class cls = null;
            if (ref instanceof Class)
                cls = (Class) ref;
            else
                cls = ref.getClass();
            return (URLClassLoader) cls.getClassLoader();
        }
        catch (Exception e) 
        {
            return (URLClassLoader) Utils.class.getClassLoader();
        }
    }

    public static byte[] load(String resourceName) throws IOException
    {
        return load(resourceName, null);
    }

    public static byte[] load(String resourceName, Object ref) throws IOException
    {
        return load(getClassLoaderFor(ref).getResourceAsStream(resourceName));
    }

    public static byte[] load(InputStream in) throws IOException
    {
        return load(in, -1);
    }

    public static byte[] load(InputStream in, int dataLimit) throws IOException
    {
        return load(in, dataLimit, true);
    }

    public static byte[] load(InputStream in, boolean closeOnComplete) throws IOException
    {
        return load(in, -1, closeOnComplete);
    }

    public static byte[] load(InputStream in, int dataLimit, boolean closeOnComplete) throws IOException
    {
        if (in == null)
            return null;

        int total = 0;
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        while (true)
        {
            int r = in.read(buffer);
            if (r < 0)
                break;
            total += r;
            if ((dataLimit > 0) && (total > dataLimit))
                throw new IOException("Maximum data length from input exceeded ("+dataLimit+")");
            bout.write(buffer, 0, r);
        }
        
        if (closeOnComplete)
            in.close();
        return bout.toByteArray();
    }

    public static String removeUnprintableChars(CharSequence src)
    {
        StringBuffer buf = new StringBuffer();
        for (int i=0; i<src.length(); i++)
        {
            char ch = src.charAt(i);
            if ((ch == '\n') || (ch >= 32) && (ch <= 126))
                buf.append(ch);
        }

        return buf.toString();
    }

    public static String loadText(File src) throws IOException
    {
        return new String(load(src), ASCII);
    }

    public static String loadText(URI uri) throws IOException
    {
        return loadText(uri.toURL());
    }

    public static String loadText(URL url) throws IOException
    {
        return new String(load(url.openStream()), ASCII);
    }

    public static String loadText(String resourceName) throws IOException
    {
        return loadText(resourceName, null);
    }

    public static String loadText(String resourceName, Object reference) throws IOException
    {
        return toString(load(resourceName, reference));
    }

    public static String loadText(InputStream in) throws IOException
    {
        return toString(load(in));
    }

    private static boolean scanJarFile(Set results, Predicate<String> acceptor, URI jarURI)
    {
        JarInputStream jarStream = null;
        try
        {
            try {
                jarStream = new JarInputStream(jarURI.toURL().openStream());
            }
            catch (FileNotFoundException e) {
                return false;
            }

            Manifest manifest = jarStream.getManifest();
            if (manifest != null) {
                Attributes attrs = manifest.getMainAttributes();
                if (attrs != null) {
                    String classPath = attrs.getValue(Attributes.Name.CLASS_PATH);
                    if (classPath != null) {
                        URL[] urls = parseCP(jarURI.toURL(), classPath);
                        scanURLs(acceptor, results, urls);
                    }
                 }
            }

            while (true)
            {
                JarEntry entry = jarStream.getNextJarEntry();
                if (entry == null)
                    break;
                if (entry.isDirectory())
                    continue;
            
                String path = entry.getName();
                if (acceptor.test(path))
                    results.add(path);
            }

            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return false;
        }
        finally
        {
            try
            {
                jarStream.close();
            }
            catch (Exception ee) {}
        }
    }

    private static URL[] parseCP(URL root, String classPath) throws MalformedURLException {
        StringTokenizer st = new StringTokenizer(classPath);
        URL[] urls = new URL[st.countTokens()];
        for(int index = 0; st.hasMoreTokens(); index++) {
            urls[index] = new URL(root, st.nextToken());
        }

        return urls;
    }

    private static boolean scanDirectory(Set results, Predicate<String> acceptor, URI dir, URI root)
    {
        try
        {
            File f = new File(dir);
            if (!f.exists() || !f.isDirectory())
                return false;

            File[] ff = f.listFiles();
            for (int i=0; i<ff.length; i++)
            {
                if (ff[i].isDirectory())
                    scanDirectory(results, acceptor, ff[i].toURI(), root);
                else
                {
                    String path = root.relativize(ff[i].toURI()).getPath();
                    if (acceptor.test(path))
                        results.add(path);
                }
            }

            return true;
        }
        catch (Exception e) 
        {
            return false;
        }
    }

    public static String[] find(Predicate<String> acceptor, File root)
    {
        TreeSet ts = new TreeSet();
        URI uri = root.toURI();
        scanDirectory(ts, acceptor, uri, uri);
        
        String[] result = new String[ts.size()];
        ts.toArray(result);
        return result;
    }

    public static String[] find(Predicate<String> acceptor, URI[] uris)
    {
        TreeSet ts = new TreeSet();
        for (URI uri : uris) {
            try {
                if (uri.getPath().endsWith(".jar"))
                    scanJarFile(ts, acceptor, uri);
                else if (uri.getScheme().equals("file"))
                    scanDirectory(ts, acceptor, uri, uri);
            } catch (Exception e) {
            }
        }
        
        String[] result = new String[ts.size()];
        ts.toArray(result);
        return result;
    }

    public static String[] find(Predicate<String> acceptor, URLClassLoader classLoader) {
        return find(acceptor, classLoader, false);
    }

    public static String[] find(Predicate<String> acceptor, URLClassLoader classLoader, boolean includeParentLoader)
    {
        TreeSet ts = new TreeSet();

        URL[] urls = classLoader.getURLs();
        scanURLs(acceptor, ts, urls);

        if (includeParentLoader) {
            ClassLoader parent = classLoader.getParent();
            if (parent instanceof URLClassLoader) {
                scanURLs(acceptor, ts, ((URLClassLoader)parent).getURLs());
            }
        }

        String[] result = new String[ts.size()];
        ts.toArray(result);
        return result;
    }

    private static void scanURLs(Predicate<String> acceptor, Set ts, URL[] urls) {
        for (URL url : urls) {
            try {
                URI uri = url.toURI();
                if (uri.getPath().endsWith(".jar"))
                    scanJarFile(ts, acceptor, uri);
                else if (uri.getScheme().equals("file"))
                    scanDirectory(ts, acceptor, uri, uri);
            } catch (Exception e) {
            }
        }
    }

    public static String[] find(Predicate<String> acceptor)
    {
        return find(acceptor, (URLClassLoader) Utils.class.getClassLoader());
    }

    public static int getFreeSocket(InetAddress bindAddress, int portRangeStart, int portRangeEnd) 
    {
        for (int port=portRangeStart; port<portRangeEnd; port++)
        {
            ServerSocket ssocket = null;
            try
            {
                ssocket = new ServerSocket();
                ssocket.setReuseAddress(false);
                ssocket.setSoTimeout(10);
                ssocket.bind(new InetSocketAddress(bindAddress, port), 10000);

                return port;
            }
            catch (Exception e) {}
            finally
            {
                try
                {
                    ssocket.close();
                }
                catch (Exception e) {}
            }
        }

        return -1;
    }

    private static MessageDigest md5;
    private static MessageDigest sha1;
    private static MessageDigest sha256;
    private static CRC32 crc32 = new CRC32();
    
    static
    {
        try
        {
            md5 = MessageDigest.getInstance("MD5");
        }
        catch (Exception e) 
        {
            throw new NullPointerException("No MD5 Implementation");
        }

        try
        {
            sha1 = MessageDigest.getInstance("SHA-1");
        }
        catch (Exception e)
        {
            throw new NullPointerException("No SHA1 Implementation");
        }

        try
        {
            sha256 = MessageDigest.getInstance("SHA-256");
        }
        catch (Exception e)
        {
            throw new NullPointerException("No SHA256 Implementation");
        }
    }

    public synchronized static byte[] SHA256(byte[] input)
    {
        return sha256.digest(input);
    }

    public static String SHA256(String in)
    {
        return toHexString(SHA256(getAsciiBytes(in)));
    }

    public synchronized static byte[] SHA1(byte[] input) throws Exception
    {
        return sha1.digest(input);
    }

    public static String toHexString(byte[] arr)
    {
        Formatter formatter = new Formatter();
        for (int i=0; i<arr.length; i++)
            formatter.format("%02x", arr[i]);
        return formatter.toString();
    }

    public static byte[] fromHexString(String in) 
    {
        byte[] res = new byte[in.length()/2];
        for (int i=0, j=0; i < res.length; i++, j+=2)
            res[i] = (byte)Integer.parseInt(in.substring(j, j+2), 16);
        return res;
    }

    public static synchronized int generate32bitChecksum(byte[] buffer)
    {
        return generate32bitChecksum(buffer, 0, buffer.length);
    }

    public static synchronized int generate32bitChecksum(byte[] buffer, int offset, int length)
    {
        crc32.reset();
        crc32.update(buffer, offset, length);
        return (int) crc32.getValue();
    }

    public static synchronized byte[] generateMD5Checksum(byte[] buffer)
    {
        return generateMD5Checksum(buffer, 0, buffer.length);
    }

    public static synchronized byte[] generateMD5Checksum(byte[] buffer, int offset, int length)
    {
        md5.reset();
        md5.update(buffer, offset, length);
        return md5.digest();
    }

    public static String URLEncode(String src)
    {
        try
        {
            return URLEncoder.encode(src, "UTF-8");
        }
        catch (Exception e) {}
        return null;
    }
       
    public static String URLDecode(String src)
    {
        try
        {
            return URLDecoder.decode(src, "UTF-8");
        }
        catch (Exception e) {}
        return null;
    } 

    public static String getJarVersion()
    {
        return getJarVersion(Utils.class.getClassLoader());
    }

    public static String getJarVersion(ClassLoader classLoader)
    {
        if (!(classLoader instanceof URLClassLoader))
            return DEFAULT_VERSION_STRING;

        URLClassLoader cl = (URLClassLoader) classLoader;
        URL[] urls = cl.getURLs();
        for (int i=0; i<urls.length; i++)
        {
            if (!urls[i].getPath().endsWith(".jar"))
                continue;
            
            try
            {
                URL url = cl.findResource("META-INF/MANIFEST.MF");
                Manifest mf = new Manifest(url.openStream());
                if (mf == null)
                    continue;
                
                Map entries = mf.getMainAttributes();
                Iterator itt = entries.keySet().iterator();

                String implVersion = null;
                String buildDate = null;
                while (itt.hasNext())
                {
                    Object key = itt.next();
                    if (key.toString().equalsIgnoreCase("Implementation-Version"))
                        implVersion = entries.get(key).toString();
                    else if (key.toString().equalsIgnoreCase("Build-Date"))
                        buildDate = entries.get(key).toString();
                }

                if ((implVersion == null) && (buildDate == null))
                    continue;
                return implVersion+" "+buildDate;
            }
            catch (Exception e) {}
        }
        
        return DEFAULT_VERSION_STRING;
    }

    public static byte[] gzip(byte[] src)
    {
        if (src == null)
            return null;

        try
        {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            GZIPOutputStream gz = new GZIPOutputStream(bout);
            gz.write(src);
            gz.close();
            return bout.toByteArray();
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Unable to GZIP bytes: "+e, e);
        }
    }

    public static byte[] unzip(byte[] src)
    {
        return fromGzip(src);
    }

    public static byte[] fromGzip(byte[] src)
    {
        if (src == null)
            return null;

        try
        {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(src));
            byte[] tmp = new byte[4096];
            int r;
            while ((r=gz.read(tmp)) >= 0)
                bout.write(tmp, 0, r);
            return bout.toByteArray();
        }
        catch (IOException e)
        {
            throw new IllegalStateException("Unable to UnGZIP bytes: "+e, e);
        }
    }
    
    public static String stackTraceString(Throwable t)
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bout);
        for (Throwable tt = t; tt != null; tt = tt.getCause())
            tt.printStackTrace(ps);
        ps.close();

        return Utils.toString(bout.toByteArray());
    }

    public static void main(String[] args) throws Exception
    {
        System.out.println(getJarVersion());
    }
}
