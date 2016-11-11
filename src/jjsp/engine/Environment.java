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

package jjsp.engine;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;
import java.lang.reflect.*;

import java.awt.image.*;
import javax.imageio.*;
import java.nio.file.attribute.*;

import jjsp.util.*;
import jjsp.http.*;
import jjsp.data.*;

public class Environment extends ImageGenerator
{
    public static final String LIB = "lib/";
    public static final String SERVICES = "services/";
    public static final String CACHE_DIR = "jjspcache/";
    
    private static final String LIB_PATH = LIB;
    private static final String SERVICES_PATH = LIB+SERVICES;

    private final URI rootURI;

    private Map localContent, args;
    private ArrayList resourcePathRoots;
    private File localCacheDir, servicesCacheDir;

    private final HashMap serviceLoaders;
    private URLClassLoader libraryLoader;
    private TreeMap registeredDataInfoIndices;
    private final Set ourClassLoaders;

    public Environment() throws IOException
    {
        this(new HashMap());
    }

    public Environment(Map args) throws IOException
    {
        this(new File(System.getProperty("user.dir")).toURI(), new File(new File(System.getProperty("user.dir")), CACHE_DIR), args);
    }

    public Environment(File rootDir, Map args) throws IOException
    {
        this(rootDir.toURI(), new File(new File(System.getProperty("user.dir")), CACHE_DIR), args);
    }

    public Environment(URI rootURI, Map args) throws IOException
    {
        this(rootURI, new File(new File(System.getProperty("user.dir")), CACHE_DIR), args);
    }

    public Environment(File rootDir, File localCacheDir, Map args) throws IOException
    {
        this(rootDir.toURI(), localCacheDir, args);
    }

    public Environment(URI rootURI, File localCacheDir, Map args) throws IOException
    {
        this.rootURI = rootURI;

        try
        {
            File rootDir = new File(rootURI);
            File libDir = new File(rootDir, LIB);
            File svcLib = new File(libDir, SERVICES);
            svcLib.mkdirs();
        }
        catch (Exception e) {}

        this.localCacheDir = localCacheDir;
        localCacheDir.mkdirs();
        servicesCacheDir = new File(localCacheDir, SERVICES);
        servicesCacheDir.mkdir();

        this.args = new LinkedHashMap();
        if (args != null)
            this.args.putAll(args);

        localContent = new TreeMap();
        resourcePathRoots = new ArrayList();
        addResourcePathRoot(rootURI);

        ourClassLoaders = new HashSet();
        serviceLoaders = new HashMap();
        registeredDataInfoIndices = new TreeMap();
        libraryLoader = createLibraryLoader();
        clearSunJarFileFactoryCache();

    }

    private static void clearSunJarFileFactoryCache() 
    {
        try 
        {
            Class jarFileFactory = Class.forName("sun.net.www.protocol.jar.JarFileFactory");

            Field fileCacheField = jarFileFactory.getDeclaredField("fileCache");
            fileCacheField.setAccessible(true);
            Map fileCache = (Map) fileCacheField.get(null);
            fileCache.clear();

            Field urlCacheField = jarFileFactory.getDeclaredField("urlCache");
            urlCacheField.setAccessible(true);
            Map urlCache = (Map) urlCacheField.get(null);
            urlCache.clear();
        }
        catch (Exception e) { }
        // Unable to clear the cache, but we shouldn't have been messing with it anyway. Maybe we're on OpenJDK
    }

    public Map getArgs()
    {
        Map result = new LinkedHashMap();
        result.putAll(args);
        return result;
    }

    public String[] getArgNames()
    {
        String[] result = new String[args.size()];
        args.keySet().toArray(result);
        return result;
    }

    public String getArg(String name, String defaultValue)
    {
        String result = getArg(name);
        if (result != null)
            return result;
        return defaultValue;
    }

    public boolean hasArg(String name)
    {
        String val = getArg(name, "false");
        return val.equalsIgnoreCase("true");
    }

    public String getArg(String name)
    {
        if (name == null)
            return null;
        return (String) args.get(name.toLowerCase());
    }

    public String getSystemProperty(String name)
    {
        return System.getProperty(name);
    }

    public URI getRootURI()
    {
        return rootURI;
    }

    public URI getLibraryURI()
    {
        return getRootURI().resolve(LIB);
    }
    
    public URI getServicesURI()
    {
        return getRootURI().resolve(LIB).resolve(SERVICES);
    }

    public URI getServiceURI(String serviceName)
    {
        if (!serviceName.endsWith("/"))
            serviceName = serviceName+"/";
        return getServicesURI().resolve(serviceName);
    }

    public File getLocalCacheDir()
    {
        return localCacheDir;
    }

    public File getServiceCacheDir(String serviceName) throws IOException
    {
        File cacheDir = getLocalCacheDir();
        cacheDir.mkdirs();

        File result = new File(cacheDir, serviceName);
        result.mkdirs();
        return result;
    }

    protected void checkModifyResourcePaths()
    {
    }

    public synchronized URI[] getResourcePathRoots()
    {
        URI[] result = new URI[resourcePathRoots.size()];
        resourcePathRoots.toArray(result);
        return result;
    }

    public synchronized void addResourcePathRoot(String resourceRoot)
    {      
        addResourcePathRoot(getRootURI().resolve(resourceRoot));
    }

    public synchronized void addResourcePathRoot(URI resourceRoot)
    {        
        // Note: deliberately allow duplicates!
        // When roots are added by an include/parse statement,you want to be able to "pop" 
        // them off with removeResourcePathRoot and not inadvertently delete a version that needs to be there (from before your call).
        checkModifyResourcePaths();

        ArrayList newVersion = new ArrayList();
        newVersion.addAll(resourcePathRoots);
        newVersion.add(resourceRoot);

        resourcePathRoots = newVersion;
    }

    public synchronized boolean removeResourcePathRoot(URI absoluteURI)
    {        
        checkModifyResourcePaths();
        if (absoluteURI == null)
            return false;
        if (!resourcePathRoots.contains(absoluteURI))
            return false;
        if (absoluteURI.equals(getRootURI()))
            return false;

        ArrayList newVersion = new ArrayList();
        newVersion.addAll(resourcePathRoots);
        // Now remove the last instance of the absoluteURI
        for (int i=newVersion.size()-1; i>=0; i--)
            if (absoluteURI.equals(newVersion.get(i)))
            {
                newVersion.remove(i);
                break;
            }

        resourcePathRoots = newVersion;
        return true;
    }

    public void addService(String name) throws IOException
    {
        File cacheDir = getServiceCacheDir(name);
        if (cacheDir == null)
            throw new IOException("Cannot create new service in store cache dir: '"+servicesCacheDir+"'");

        File libDir = new File(getLibraryURI());
        libDir.mkdirs();
        File svcDir = new File(libDir, SERVICES);
        svcDir.mkdirs();

        File sDir = new File(svcDir, name);
        sDir.mkdirs();
    }

    public void addLibrary(String name, byte[] jarBytes) throws IOException
    {
        File libDir = new File(getLibraryURI());
        libDir.mkdirs();

        File libFile = new File(libDir, name);
        libFile.createNewFile();
        
        FileOutputStream fout = new FileOutputStream(libFile);
        fout.write(jarBytes);
        fout.close();
    }

    public void addServiceFile(String serviceName, String fileName, byte[] data) throws IOException
    {
        URI svcDirURI = getServiceURI(serviceName); 
        if (svcDirURI == null)
            throw new IOException("Service '"+serviceName+"' not found");

        File svcFile = new File(new File(svcDirURI), fileName);
        svcFile.createNewFile();
        
        FileOutputStream fout = new FileOutputStream(svcFile);
        fout.write(data);
        fout.close();
    }

    public URLClassLoader getLibraryLoader()
    {
        return libraryLoader;
    }

    public URLClassLoader createJarLoaderFromRootURI() throws IOException
    {
        HashSet jjspLoaderUrls = new HashSet();
        try
        {
            URL[] urls = ((URLClassLoader) getClass().getClassLoader()).getURLs(); 
            for (int i=0; i<urls.length; i++)
                jjspLoaderUrls.add(urls[i]);
        }
        catch (Exception e) {}

        ArrayList ll = new ArrayList();
        URL[] rootJarURLs = getJarURLsFromDirectoryURI(getRootURI());
        for (int i=0; i<rootJarURLs.length; i++)
            if (!jjspLoaderUrls.contains(rootJarURLs[i]))
                ll.add(rootJarURLs[i]);

        //Make the root and library Loaders the same to make it easy to code Java code loading other resources
        URL[] libJarURIs = getJarURLsFromDirectoryURI(getLibraryURI());
        for (int i=0; i<libJarURIs.length; i++)
            if (!jjspLoaderUrls.contains(libJarURIs[i]))
                ll.add(libJarURIs[i]);

        URL[] clUrls = new URL[ll.size()];
        ll.toArray(clUrls);
        return recordClassLoader(new URLClassLoader(clUrls, getClass().getClassLoader()));
    }

    public URLClassLoader createLibraryLoader() throws IOException
    {
        //return createClassLoaderFromDirURI(getLibraryURI(), createJarLoaderFromRootURI());
        return createJarLoaderFromRootURI();
    }

    public Class getLibraryClass(String className) throws Exception
    {
        URLClassLoader loader = getLibraryLoader();
        return loader.loadClass(className);
    }

    public byte[] getLibraryResource(String resourceName) throws IOException
    {
        URLClassLoader loader = getLibraryLoader();
        return Utils.load(loader.getResourceAsStream(resourceName));
    }

    public String getLibraryResourceText(String resourceName) throws IOException
    {
        return Utils.toString(getLibraryResource(resourceName));
    }

    public static String getClassloaderDetails(ClassLoader cl)
    {
        String indent = "    ";
        StringBuffer buffer = new StringBuffer();
        
        while (cl != null)
        {
            buffer.append(indent+"ClassLoader: "+cl+"\n");
            try
            {
                URL[] urls = ((URLClassLoader) cl).getURLs();
                for (int i=0; i<urls.length; i++)
                    buffer.append(indent+" URL "+i+": "+urls[i]+"\n");
            }
            catch (Exception e) {}
            indent += "    ";
            cl = cl.getParent();
        }

        return buffer.toString();
    }

    public URLClassLoader getStandAloneServiceLoader(String serviceName) throws IOException
    {
        URLClassLoader svcLoader = getServiceLoader(serviceName);
        URLClassLoader libLoader = getLibraryLoader();

        URL[] jarList1 = svcLoader.getURLs();
        URL[] jarList2 = libLoader.getURLs();
        LinkedHashSet ll = new LinkedHashSet();
        
        for (int i=0; i<jarList2.length; i++)
            ll.add(jarList2[i]);
        for (int i=0; i<jarList1.length; i++)
            ll.add(jarList1[i]);

        URL[] fullList = new URL[ll.size()];
        ll.toArray(fullList);
        
        return recordClassLoader(new URLClassLoader(fullList, getClass().getClassLoader()));
    }

    public URLClassLoader getServiceLoader(String serviceName) throws IOException
    {
        if (serviceName == null)
            throw new NullPointerException("NULL Service name");

        URLClassLoader result = null;
        synchronized (serviceLoaders)
        {
            result = (URLClassLoader) serviceLoaders.get(serviceName);
            if (result != null)
                return result;
        }

        if (!serviceExists(serviceName))
            throw new IOException("Service '"+serviceName+"' not found");

        URI svcURI = getServiceURI(serviceName); 
        result = createClassLoaderFromDirURI(svcURI, getLibraryLoader());

        URLClassLoader ll;
        synchronized (serviceLoaders)
        {
            ll = (URLClassLoader) serviceLoaders.get(serviceName);
            if (ll == null)
            {
                serviceLoaders.put(serviceName, result);
                return result;
            }
        }
        // We don't need result now, and it wasn't recorded as createClassLoaderFromDirURI is static, so close it.
        result.close();
        return ll;
    }
    
    public URLClassLoader createServiceLoader(String serviceName) throws IOException
    {
        if (!serviceExists(serviceName))
            throw new IOException("Service '"+serviceName+"' not found");

        URI svcURI = getServiceURI(serviceName); 
        return recordClassLoader(createClassLoaderFromDirURI(svcURI, getLibraryLoader()));
    }

    public Class getServiceClass(String serviceName, String className) throws Exception
    {
        URLClassLoader loader = getServiceLoader(serviceName);
        return loader.loadClass(className);
    }

    public byte[] getServiceResource(String resourceName, String serviceName) throws IOException
    {
        URLClassLoader loader = getServiceLoader(serviceName);
        return Utils.load(loader.getResourceAsStream(resourceName));
    }

    public String getServiceResourceText(String resourceName, String serviceName) throws IOException
    {
        return Utils.toString(getServiceResource(resourceName, serviceName));
    }

    public class URIContent
    {
        public final byte[] data;
        public final long loadTime;
        public final URI resolvedURI;
        
        public URIContent(URI uri, byte[] raw)
        {
            data = raw;
            resolvedURI = uri;
            loadTime = System.currentTimeMillis();
        }

        public int writeTo(OutputStream out) throws IOException
        {
            out.write(data);
            return data.length;
        }

        public String asString()
        {
            return toAsciiString(data);
        }
    }

    public URIContent loadFromResourcePath(String path) throws IOException
    {
        StringBuffer buf = new StringBuffer();
        try
        {
            URI absoluteURI = new URI(path);
            if (absoluteURI.isAbsolute())
            {
                byte[] data = Utils.load(absoluteURI);
                return new URIContent(absoluteURI, data);
            }
        }
        catch (Exception e) {buf.append(path+"; ");}
                    
        Iterator itt = null;
        synchronized (this)
        {
            itt = resourcePathRoots.iterator();
        }

        String escapedPath = escapeResourcePath(path);
        while (itt.hasNext())
        {
            URI uri = (URI) itt.next();
            try
            {
                URI resolvedURI = uri.resolve(escapedPath);
                byte[] data = Utils.load(resolvedURI);
                return new URIContent(resolvedURI, data);
            }
            catch (Exception e) {}

            buf.append(uri.toString()+"; ");
        }

        throw new IOException("Failed to find resource '"+path+"' in path '"+buf+"'");
    }

    public byte[] load(String path) throws Exception
    {
        return loadFromResourcePath(path).data;
    }

    public String loadText(String path) throws Exception
    {
        return loadFromResourcePath(path).asString();
    }

    public BufferedImage loadImage(String uriPath) throws Exception
    {
        URIContent result = loadFromResourcePath(uriPath);
        return ImageIO.read(new ByteArrayInputStream(result.data));
    }

    public String putLocal(String path, Object contents) throws Exception
    {
        path = checkLocalResourcePath(path);
        if (contents == null)
            throw new IllegalArgumentException("Null contents for path '"+path+"'");
        
        String message = "";
        if (contents instanceof BufferedImage)
        {
            String ext = "PNG";
            int dot = path.lastIndexOf(".");
            if (dot > 0)
                ext = path.substring(dot+1).toUpperCase();
            
            contents = getImageBytes((BufferedImage) contents, ext);
            message = "Local image on: '"+path+"'";
        }
        else if (contents instanceof File)
        {
            contents = ((File) contents).toURI();
            message = "Local file '"+contents+"' loaded to local path '"+path+"'";
        }
        else if (contents instanceof URL)
        {
            contents = ((URL) contents).toURI();
            message = "Local URL (converted to URI): '"+path+"' -> '"+contents+"'";
        }
        else if (contents instanceof URI)
            message = "Local URI: "+path+" -> "+contents+"\n";
        else if (contents instanceof URIContent)
        {
            URIContent cc = (URIContent) contents;
            message = "Remote URI '"+cc.resolvedURI+"' loaded to local path '"+path+"'";
        }
        else if (contents instanceof byte[])
            message = "Local data ("+((byte[]) contents).length+" bytes) at path '"+path+"'";
        else
        {
            String ts = contents.toString();
            contents = Utils.getAsciiBytes(ts);
            message = "Local Path: '"+path+"' -> content length "+ts.length()+"";
        }

        if (contents instanceof URI)
        {
            URI uri = (URI) contents;
            if (uri.getScheme().equals("file"))
            {
                File f = new File(uri);
                if (f.isDirectory())
                    throw new IOException("Cannot register a local directory URI: '"+f+"'");
                else if (!f.exists())
                    throw new IOException("File '"+f+"' does not exist");
            }
            else if (!uri.getScheme().startsWith("http"))
                throw new IOException("Unknown URI scheme: '"+uri+"'");
        }
        
        synchronized (localContent)
        {
            localContent.put(path, contents);
        }
        return message;
    }

    public String[] listLocal()
    {
        synchronized (localContent)
        {
            String[] result = new String[localContent.size()];
            localContent.keySet().toArray(result);
            return result;
        }
    }

    public boolean deleteLocal(String name)
    {
        name = checkLocalResourcePath(name);
        synchronized (localContent)
        {
            return localContent.remove(name) != null;
        }
    }

    public byte[] getLocal(String name)
    {
        if ((name == null) || (name.length() == 0))
            return null;
        name = checkLocalResourcePath(name);
        
        Object obj = null;
        synchronized (localContent)
        {
            obj = localContent.get(name);
            if (obj == null)
                return null;
        }

        if (obj instanceof byte[])
            return (byte[]) obj;
        else if (obj instanceof URI)
        {
            URI uri = (URI) obj;
            try
            {
                byte[] result = Utils.load(uri);
                URIContent content = new URIContent(uri, result);
                putLocal(name, content);
                return content.data;
            }
            catch (Exception e) {}
            return null;
        }
        else if (obj instanceof URIContent)
            return ((URIContent) obj).data;

        throw new IllegalStateException("Unknown object type in local store "+obj);
    }

    public String getLocalString(String name)
    {
        return Utils.toString(getLocal(name));
    }

    public BufferedImage getLocalImage(String path) throws Exception
    {
        return imageFromBytes(getLocal(path));
    }

    public String loadFileToLocalStore(String srcPath) throws Exception
    {
        return loadFileToLocalStore(srcPath, srcPath);
    }

    public String loadFileToLocalStore(String srcPath, String dstPath) throws Exception
    {
        URIContent uc = loadFromResourcePath(srcPath);
        putLocal(dstPath, uc.data);
        return "Loaded '"+srcPath+"' (from '"+uc.resolvedURI+"') to local store at '"+dstPath+"'\n";
    }

    private void recursiveDirGlobMatch(int level, File f, String destPath, String startsWith, String endsWith, StringBuffer report)
    {
        if (f.isDirectory())
        {
            if (level < 0)
                return;
            File[] ff = f.listFiles();
            for (int i=0; i<ff.length; i++)
                recursiveDirGlobMatch(level-1, ff[i], destPath, startsWith, endsWith, report);
        }
        else
        {
            String absPath = f.getAbsolutePath().replace("\\", "/");
            if (absPath.startsWith(startsWith) && absPath.endsWith(endsWith))
            {
                String outputPath = destPath+absPath.substring(startsWith.length());
                try
                {
                    putLocal(outputPath, f);
                }
                catch (Exception e) {}
                report.append("   "+f+" == "+outputPath+"\n");
            }
        }
    }

    public String loadFilesToLocalStore(String globMatch, String destDirPath)
    {
        return loadFilesToLocalStore(globMatch, destDirPath, 10);
    }

    public String loadFilesToLocalStore(String globMatch, String destDirPath, int maxLevels)
    {
        maxLevels = Math.max(0, maxLevels);
        destDirPath = checkLocalResourcePath(destDirPath);
        if (!destDirPath.endsWith("/"))
            destDirPath = destDirPath+"/";

        globMatch = globMatch.replace("*.*", "*");
        globMatch = globMatch.replace("**", "*");
        globMatch = globMatch.replace("\\", "/");
            
        while (globMatch.startsWith("/"))
            globMatch = globMatch.substring(1);

        int star = globMatch.indexOf("*");
        if (star < 0)
        {
            int slash = globMatch.lastIndexOf("/");
            if (slash < 0)
                star = globMatch.length();
            else
                star = slash;
        }

        String prefix = globMatch.substring(0, star);
        String suffix = "";
        if (star < globMatch.length())
            suffix = globMatch.substring(star+1);

        StringBuffer buf = new StringBuffer("Copy from '"+globMatch+"' to '"+destDirPath+"'   [");
        StringBuffer pathBuffer = new StringBuffer();

        Iterator itt = null;
        synchronized (this)
        {
            itt = resourcePathRoots.iterator();
        }
        
        while (itt.hasNext())
        {
            URI uri = (URI) itt.next();
            pathBuffer.append(uri.toString()+";");

            try
            {
                File srcDir = new File(uri.resolve(prefix));
                if (!srcDir.exists() || !srcDir.isDirectory())
                    continue;
                
                String startsWith = srcDir.getAbsolutePath().replace("\\", "/");
                if (!startsWith.endsWith("/") && srcDir.isDirectory())
                    startsWith = startsWith+"/";
                buf.append("'"+srcDir+"' to '"+destDirPath+"' max subdirs "+maxLevels+"]\n");
                recursiveDirGlobMatch(maxLevels, srcDir, destDirPath, startsWith, suffix, buf);

                return buf.toString();
            }
            catch (Exception e) {}
        }

        return "No source directory '"+prefix+"' found in path '"+pathBuffer+"'";
    }

    public String loadZipToLocalStore(String remotePath, String localPathPrefix) throws Exception
    {
        return loadZipToLocalStore(remotePath, localPathPrefix, false);
    }

    public String loadZipToLocalStore(String remotePath, String localPathPrefix, boolean ignoreIfNotFound) throws Exception
    {
        StringWriter sw = new StringWriter();
        URIContent loadResult = null;
        try
        {
            loadResult = loadFromResourcePath(remotePath);
        }
        catch (Exception e) 
        {
            if (ignoreIfNotFound)
                return "Zip data '"+remotePath+"' not found";
            else
                throw e;
        }

        byte[] buffer = new byte[4096];
        if ((localPathPrefix.length() > 0) && !localPathPrefix.endsWith("/"))
            localPathPrefix = localPathPrefix+"/";

        sw.write("Loading "+remotePath+" as ZIP data ("+loadResult.data.length+" compressed)\n");
        ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(loadResult.data));
        while (true)
        {
            ZipEntry zen = zin.getNextEntry();
            if (zen == null)
                break;
            try
            {
                if (zen.isDirectory())
                    continue;

                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                while (true)
                {
                    int r = zin.read(buffer);
                    if (r < 0)
                        break;
                    bout.write(buffer, 0, r);
                }
                bout.close();
                byte[] content = bout.toByteArray();
                String path = localPathPrefix+zen.getName();

                path = putLocal(path, content);
                sw.write("Local resource '"+path+"' loaded from "+loadResult.resolvedURI+" with "+content.length+" bytes\n");
            }
            finally
            {
                zin.closeEntry();
            }
        }

        return sw.toString();
    }

    public byte[] toZIPArchive() throws Exception
    {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ZipOutputStream zout = new ZipOutputStream(bout);
        FileTime created = FileTime.fromMillis(System.currentTimeMillis());
        
        String[] paths = listLocal();
        for (int i=0; i<paths.length; i++)
        {
            byte[] content = getLocal(paths[i]);
            if (content == null)
                throw new IOException("Failed to get content for path "+paths[i]);
            
            String zipPath = paths[i];
            if (zipPath.startsWith("/"))
                zipPath = zipPath.substring(1);
            
            ZipEntry entry = new ZipEntry(zipPath);
            entry.setCreationTime(created);
            entry.setSize(content.length);
            entry.setLastModifiedTime(created);

            zout.putNextEntry(entry);
            zout.write(content);
            zout.closeEntry();
        }

        zout.close();
        return bout.toByteArray();
    }

    public String loadServiceToLocalStore(String serviceName) throws Exception
    {
        if (!serviceExists(serviceName))
            throw new IOException("No service '"+serviceName+"' found");
        
        String outputPath = SERVICES_PATH+serviceName+"/";
        outputPath = checkLocalResourcePath(outputPath);

        URI serviceURI = getServiceURI(serviceName);
        String[] paths = listServiceResourcePaths(serviceName);
        for (int i=0; i<paths.length; i++)
            putLocal(checkLocalResourcePath(outputPath+paths[i]), serviceURI.resolve(paths[i]));
        
        return "Local store loaded service '"+serviceName+"' to '"+outputPath+"'\n";
    }

    public String loadLibraryToLocalStore(String libName) throws Exception
    {
        String outputPath = LIB_PATH+libName;
        outputPath = checkLocalResourcePath(outputPath);

        if (!libName.endsWith(".jar"))
            libName = libName+".jar";

        URI libURI = getLibraryURI();
        String[] paths = listURIDirectory(libURI);
        for (int i=0; i<paths.length; i++)
        {
            if (!paths[i].equalsIgnoreCase(libName))
                continue;
            
            putLocal(checkLocalResourcePath(outputPath), libURI.resolve(paths[i]));
            return "Local store loaded library  '"+libName+"' to '"+outputPath+"'\n";
        }

        throw new IOException("Library '"+libName+"' not found");
    }

    public static String[] listURIDirectory(URI dirURI) throws IOException
    {
        ArrayList buf = new ArrayList();

        if (dirURI.getScheme().equals("file"))
        {
            File dir = new File(dirURI.normalize());
            if (!dir.exists() || !dir.isDirectory())
                return new String[0];

            File[] ff = new File(dirURI).listFiles();
            if (ff == null)
                return new String[0];
                
            for (int i=0; i<ff.length; i++)
                buf.add(ff[i].getName());
        }
        else
        {
            // Really only works for the HTTP result from a JJSP localStore handler, which lists contents by default 1 per line.
            InputStream in = dirURI.toURL().openStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            while (true)
            {
                String line = br.readLine();
                if (line == null)
                    break;
                line = line.trim();
                if (line.length() == 0)
                    continue;
                buf.add(line);
            }
            br.close();
        }

        String[] result = new String[buf.size()];
        buf.toArray(result);
        return result;
    }

    public String[] listDirectory(String dirPath) throws IOException
    {
        File root = new File(System.getProperty("user.dir"));
        try
        {
            root = new File(rootURI);
        }
        catch (Exception e) {}
        
        File dir = new File(root, dirPath);
        if (!dir.exists() || !dir.isDirectory())
            return new String[0];

        File[] ff = dir.listFiles();
        if (ff == null)
            return new String[0];
                
        String[] result = new String[ff.length];
        for (int i=0; i<ff.length; i++)
            result[i] = ff[i].getName();

        return result;
    }

    public String[] list(String dirPath) throws IOException
    {
        return listDirectory(dirPath);
    }

    public String[] getServiceNames() throws IOException
    {
        return listURIDirectory(getServicesURI());
    }

    public String[] listServiceResourcePaths(String serviceName) throws IOException
    {
        return listURIDirectory(getServiceURI(serviceName));
    }

    public boolean serviceExists(String name) throws IOException
    {
        String[] list = getServiceNames();
        for (int i=0; i<list.length; i++)
        {
            if (name.equals(list[i]))
                return true;
        }
        return false;
    }

    public static URL[] createURLListFromClassPath(URI srcURI, String classPath) throws Exception
    {
        if ((classPath == null) || (classPath.trim().length() == 0))
            return new URL[]{srcURI.toURL()};

        String[] pp = classPath.split(";");
        if (pp.length == 0)
            return new URL[]{srcURI.toURL()};

        ArrayList ll = new ArrayList();
        for (int i=0; i<pp.length; i++)
        {
            String ss = pp[i].toString();
            if (ss.length() > 0)
                ll.add(srcURI.resolve(ss).toURL());
        }

        URL[] urls = new URL[ll.size()];
        ll.toArray(urls);
        return urls;
    }

    /** Note - ClassLoaders loaded from this method (and other static methods) should be closed by the caller,
     * as they do not have an Environment to manage their lifecycle.
     */
    public static URLClassLoader createClassLoaderFromClassPath(URI srcURI, String classPath, ClassLoader parent) throws Exception
    {
        URL[] urls = createURLListFromClassPath(srcURI, classPath);
        return new URLClassLoader(urls, parent);
    }

    public static URL[] getJarURLsFromDirectoryURI(URI dirURI) throws IOException
    {
        String[] list = listURIDirectory(dirURI);

        ArrayList ll = new ArrayList();
        for (int i=0; i<list.length; i++)
        {
            try
            {
                if (list[i].toLowerCase().endsWith(".jar"))
                    ll.add(dirURI.resolve(list[i]).toURL());
            }
            catch (Exception e) {}
        }
        
        URL[] urls = new URL[ll.size()];
        ll.toArray(urls);
        return urls;
    }

    /** Note - ClassLoaders loaded from this method (and other static methods) should be closed by the caller,
     * as they do not have an Environment to manage their lifecycle.
     */
    public static URLClassLoader createClassLoaderFromDirURI(URI dirURI, ClassLoader parent) throws IOException
    {
        return new URLClassLoader(getJarURLsFromDirectoryURI(dirURI), parent);
    }

    public long parseLong(String s)
    {
        return Long.parseLong(s);
    }

    public long parseLong(String s, int base)
    {
        return Long.parseLong(s, base);
    }

    public String longAsString(long n)
    {
        return Long.toString(n);
    }

    public String longAsString(long n, int base)
    {
        return Long.toString(n, base);
    }

    public String roundString(Number n, int decimalPlaces)
    {
        return String.format("%."+decimalPlaces+"f", n.doubleValue());
    }

    public byte[] getAsciiBytes(String str)
    {
        return Utils.getAsciiBytes(str);
    }

    public String toAsciiString(byte[] rawText)
    {
        return Utils.toString(rawText);
    }

    public byte[] gzip(byte[] uncompressed)
    {
        return Utils.gzip(uncompressed);
    }
    
    public byte[] unzip(byte[] compressed)
    {
        return Utils.fromGzip(compressed);
    }

    public HTTPUtils getHTTPUtils()
    {
        return HTTPUtils.getUtils();
    }

    public String replaceValues(Map replacementMap, String stringTemplate)
    {
        StringBuilder sb = new StringBuilder(stringTemplate);
        Iterator keys = replacementMap.keySet().iterator();
        while (keys.hasNext())
        {
            String key = keys.next().toString();
            String newValue = "";

            try
            {
                newValue = replacementMap.get(key).toString();
            }
            catch (Exception e) {}
            
            int pos = 0;
            while (true)
            {
                int index = sb.indexOf(key, pos);
                if (index < 0)
                    break;

                sb.replace(index, index+key.length(), newValue);
                pos = index + newValue.length();
            }
        }

        return sb.toString();
    }

    public synchronized String[] getRemoteDataIndexURLStems()
    {
        String[] result = new String[registeredDataInfoIndices.size()];
        registeredDataInfoIndices.keySet().toArray(result);
        return result;
    }

    public synchronized DataInfoIndex getRemoteDataIndex(String urlStem, long staleReadTimeLimit, boolean allowStaleReadsWhenDisconnected) throws Exception
    {
        return (DataInfoIndex) registeredDataInfoIndices.get(urlStem);
    }

    public synchronized DataInfoIndex createRemoteDataIndex(String urlStem, long staleReadTimeLimit, boolean allowStaleReadsWhenDisconnected) throws Exception
    {
        DataInfoIndex result = (DataInfoIndex) registeredDataInfoIndices.get(urlStem);
        if (result != null)
            return result;

        result = new HTTPDataInfoIndex(urlStem);
        
        if ((staleReadTimeLimit > 0) || allowStaleReadsWhenDisconnected)
        {
            File tempDir = getLocalCacheDir();
            File cacheDir = new File(tempDir, SimpleDirectoryDataIndex.encodeToFilename(urlStem, null));
            cacheDir.mkdir();

            SimpleDirectoryDataIndex writable = new SimpleDirectoryDataIndex(cacheDir);
            result = new CachedDataIndex(result, writable, staleReadTimeLimit, allowStaleReadsWhenDisconnected);
        }

        registeredDataInfoIndices.put(urlStem, result);
        return result;
    }

    public String exceptionString(Object t)
    {
        if (t == null)
            return "<<NULL EXCEPTION OBJECT>>";
        
        if (t instanceof Throwable)
            return toString((Throwable) t);

        try
        {
            Class cls = t.getClass();
            Method m = cls.getDeclaredMethod("getMember", new Class[]{String.class});

            String line = String.valueOf(m.invoke(t, new Object[]{"lineNumber"}));
            String columnNumber = String.valueOf(m.invoke(t, new Object[]{"columnNumber"}));
            String fileName = String.valueOf(m.invoke(t, new Object[]{"fileName"}));
            String stack = String.valueOf(m.invoke(t, new Object[]{"stack"}));

            return "NativeJSError "+t+" line "+line+" (col "+columnNumber+") in "+fileName+"\n"+stack;
        }
        catch (Throwable tt) {}
        
        return "[Unknown Exception Class] "+t;
    }

    public SQLDriver createSQLDriver(String dbURL, String user, String password) throws Exception
    {
        return createSQLDriver(dbURL, user, password, null, getLibraryLoader());
    }

    public SQLDriver createSQLDriver(String dbURL, String user, String password, String driverClass) throws Exception
    {
        return createSQLDriver(dbURL, user, password, driverClass, getLibraryLoader());
    }

    public SQLDriver createSQLDriver(String dbURL, String user, String password, String driverClass, ClassLoader loader) throws Exception
    {
        HashMap m = new HashMap();
        m.put(SQLDriver.URL, dbURL);
        m.put(SQLDriver.USER, user);
        m.put(SQLDriver.PASSWORD, password);
        if (driverClass != null)
            m.put(SQLDriver.DRIVER, driverClass);

        return createSQLDriver(m);
    }

    public SQLDriver createSQLDriver(Map props) throws Exception
    {
        return createSQLDriver(props, getLibraryLoader());
    }

    public SQLDriver createSQLDriver(Map props, ClassLoader loader) throws Exception
    {
        return new SQLDriver(props, loader);
    }

    public String escapeResourcePath(String path)
    {
        return checkLocalResourcePath(path).substring(1);
    }

    public static String checkLocalResourcePath(String path)
    {
        if (path.startsWith("http://") || path.startsWith("file:") || path.startsWith("https//:"))
            throw new IllegalStateException("Local resource paths cannot be absolute");
        if (path.indexOf(":") >= 0)
            throw new IllegalStateException("Illegal colon in local resource path '"+path+"'");
        path = path.replace("\\", "/");
        if (!path.startsWith("/"))
            path = "/"+path;

        while(path.indexOf("//") >= 0)
            path = path.replace("//", "/");
        path = path.replace("%", "%25");
        path = path.replace(" ", "%20");
        path = path.replace("@", "%40");
        path = path.replace("[", "%5B");
        path = path.replace("]", "%5D");
        path = path.replace("$", "%24");
        path = path.replace("!", "%21");
        path = path.replace("#", "%23");
        path = path.replace("+", "%2B");
        path = path.replace("'", "%27");
        path = path.replace("(", "%28");
        path = path.replace(")", "%29");
            
        return path;
    }

    public static String stripMultipleBlankLines(String src)
    {
        StringBuffer buf = new StringBuffer();
        int consecutiveEOLs = 0;

        for (int i=0; i<src.length(); i++)
        {
            char ch = src.charAt(i);
            boolean isEOL = (ch == '\n');

            if (isEOL)
                consecutiveEOLs++;
            else
                consecutiveEOLs=0;

            if (consecutiveEOLs < 3)
                buf.append(ch);
        }

        return buf.toString();
    }

    private URLClassLoader recordClassLoader(URLClassLoader cl) {
        synchronized (ourClassLoaders) {
            ourClassLoaders.add(cl);
        }
        return cl;
    }

    void closeEnvironment() throws IOException {
        synchronized (ourClassLoaders) {
            for (Object ourClassLoaderObj : ourClassLoaders) {
                URLClassLoader cl = (URLClassLoader) ourClassLoaderObj;
                cl.close();
            }
        }
    }

    public static String toString(Throwable t)
    {
        return Utils.stackTraceString(t);
    }

}
