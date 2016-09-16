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
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;
import java.util.function.*;
import java.awt.image.*;

import javax.imageio.*;
import javax.script.*;

import jdk.nashorn.api.scripting.*;

import jjsp.http.*;
import jjsp.http.filters.*;
import jjsp.util.*;
import jjsp.data.*;

public class JJSPRuntime extends Environment
{
    public static final String JJSP_NAME = "jjsp";
    public static final String TOP_LEVEL_SOURCE_PATH = "<<JJSP MAIN>>";

    public static final int DEFAULT_PORT_BASE = 2016;

    private boolean initComplete;
    private volatile boolean stopRequested;

    private HashSet includedFiles;
    private ArrayList serverSockets;
    private Function shutdownHook;
    private ArrayList closeOnExit;
    private HTTPServerLogger httpLogger;
    private HTTPRequestFilter mainFilter;

    private Logger logger;
    private StringWriter outputWriter;
    private ScriptEngine scriptEngine;

    private static ScriptEngineManager engineManager = new ScriptEngineManager();

    public JJSPRuntime(URI rootURI, File localCacheDir, Map args) throws IOException
    {
        super(rootURI, localCacheDir, args);

        initComplete = false;
        stopRequested = false;
        shutdownHook = null;
        closeOnExit = new ArrayList();
        includedFiles = new HashSet();

        serverSockets = new ArrayList();
        outputWriter = new StringWriter();
    }

    public synchronized void registerNamedLogger(String name) throws IOException
    {
        class LL extends Logger
        {
            LL(String name)
            {
                super(name, null);
            }

            @Override
            public void log(LogRecord lr)
            {
                if (logger != null)
                    logger.log(lr);
                else
                    System.out.println(lr);
            }
        }

        LogManager.getLogManager().addLogger(new LL(name));
    }

    public synchronized void setLogger(Logger log)
    {
        logger = log;
    }

    public synchronized boolean initialised()
    {
        return initComplete;
    }

    @Override
    protected void checkModifyResourcePaths()
    {
        if (initialised())
            throw new IllegalStateException("Cannot modify JJSP Runtime resource paths after initialisation");
    }

    public void init(URI srcURI) throws Exception
    {
        String jsSource = Utils.loadText(srcURI);
        addResourcePathRoot(srcURI);
        init(jsSource);
    }

    public void init(String jsSource) throws Exception
    {
        if (initialised())
            throw new IllegalStateException("JJSP Runtime already initialised");

        ClassLoader currentLoader = Thread.currentThread().getContextClassLoader();
        try
        {
            Thread.currentThread().setContextClassLoader(getLibraryLoader());

            synchronized (engineManager)
            {
                try
                {
                    scriptEngine = engineManager.getEngineByName("nashorn");
                }
                catch (Exception e) {}

                if (scriptEngine == null)
                    scriptEngine = engineManager.getEngineByName("javascript");
            }

            ScriptContext jsContext = scriptEngine.getContext();
            jsContext.setAttribute(JJSP_NAME, this, ScriptContext.ENGINE_SCOPE);

            jsContext.setWriter(outputWriter);
            jsContext.setErrorWriter(outputWriter);
            jsContext.setReader(null);

            scriptEngine.eval(wrapFunctionScript(ImageGenerator.class, JJSP_NAME));
            scriptEngine.eval(wrapFunctionScript(Environment.class, JJSP_NAME));
            scriptEngine.eval(wrapFunctionScript(JJSPRuntime.class, JJSP_NAME));

            scriptEngine.eval("delete exit");
            scriptEngine.eval("delete quit");
            scriptEngine.eval("$p=function(arg){jjsp.print(arg);};");
            scriptEngine.eval("$log=function(arg){jjsp.println(new Date()+':  '+arg);};");
            scriptEngine.eval("console=$log");
            scriptEngine.eval("console.log=$log");
            scriptEngine.eval("output=function(n, f){if (typeof f == 'function') return jjsp.putLocal(n, f()); else return jjsp.putLocal(n, f);}");

            scriptEngine.put(ScriptEngine.FILENAME, TOP_LEVEL_SOURCE_PATH);
            scriptEngine.eval(jsSource);

            synchronized (this)
            {
                initComplete = true;
            }
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(currentLoader);
        }
    }

    public synchronized ServerSocketInfo[] getServerSockets()
    {
        ServerSocketInfo[] result = new ServerSocketInfo[serverSockets.size()];
        serverSockets.toArray(result);
        return result;
    }

    public synchronized boolean addServerSocket(int port, boolean isSecure, InetAddress addr)
    {
        if (initialised())
            throw new IllegalStateException("Cannot add server socket after initialisation");

        ServerSocketInfo newSS = new ServerSocketInfo(port, isSecure, addr);
        for (int i=0; i<serverSockets.size(); i++)
        {
            ServerSocketInfo ss = (ServerSocketInfo) serverSockets.get(i);
            if (ss.port == port)
            {
                if (ss.equals(newSS))
                    return false;
                throw new IllegalStateException("Port "+port+" already registered: "+ss);
            }
        }

        serverSockets.add(newSS);
        return true;
    }

    public synchronized boolean addServerSocket(Object portObj, Object isSecureObj)
    {
        if (initialised())
            throw new IllegalStateException("Cannot add server socket after initialisation");

        int port = Integer.parseInt(portObj.toString().trim());
        boolean isSecure = Boolean.parseBoolean(isSecureObj.toString().trim());
        return addServerSocket(port, isSecure, null);
    }

    public synchronized boolean closeOnExit(Object obj)
    {
        if (obj == null)
            return false;

        closeOnExit.add(obj);

        Class cls = obj.getClass();

        try
        {
            Method m = cls.getMethod("stop", new Class[0]);
            if (m != null)
                return true;
        }
        catch (Throwable e) {}

        try
        {
            Method m = cls.getMethod("close", new Class[0]);
            if (m != null)
                return true;
        }
        catch (Throwable e) {}

        log("warn", "Failed to find suitable stop/close method on object "+cls);
        return false;
    }

    public synchronized void engineStopped() throws Exception
    {
        for (int i=0; i<closeOnExit.size(); i++)
        {
            boolean stopped = false;
            Object obj = closeOnExit.get(i);
            Class cls = obj.getClass();

            try
            {
                Method m = cls.getMethod("stop", new Class[0]);
                m.invoke(obj);

                println("Stopping instance of "+cls.toString()+" as registered with 'closeOnExit'");
                log("Stopping: " + cls.toString());
                stopped = true;
            }
            catch (Throwable e) {}

            try
            {
                Method m = cls.getMethod("close", new Class[0]);
                m.invoke(obj);

                println("Closing instance of "+cls.toString()+" as registered with 'closeOnExit'");
                log("Closing: " + cls.toString());
                stopped = true;
            }
            catch (Throwable e) {}

            if (!stopped)
                log("warn", "Failed to find suitable stop/close method on object "+cls);
        }

        closeOnExit = new ArrayList();
        if (shutdownHook != null)
        {
            log("Calling registered JJSPRuntime shutdown hook");
            try
            {
                shutdownHook.apply(null);
                log("JJSPRuntime shutdown hook applied");
            }
            catch (Exception e)
            {
                log("warn", "Exception in registered shutdown hook "+e);
            }
        }
        closeEnvironment();
    }

    public synchronized String printStackTrace(Throwable t)
    {
        if (t == null)
            return "";
        String s = toString(t);
        outputWriter.write(s);
        return s;
    }

    public synchronized String print(Object obj)
    {
        if (obj == null)
            return "";

        if (obj instanceof Number)
        {
            Number nn = (Number) obj;
            long longVal = Math.round(nn.doubleValue());

            if (Math.abs(longVal - nn.doubleValue()) < 1e-8)
            {
                String roundedNumericString = String.valueOf(longVal);
                outputWriter.write(roundedNumericString);
                return roundedNumericString;
            }
        }

        String s = obj.toString();
        outputWriter.write(s);
        return s;
    }

    public synchronized String println()
    {
        return println(null);
    }

    public synchronized String println(Object obj)
    {
        if (obj == null)
            return print("\n");
        else
            return print(obj) + print("\n");
    }

    public synchronized void log(Object obj)
    {
        log(null, obj);
    }

    public synchronized void log(Object level, Object obj)
    {
        log(level, obj, null);
    }

    public synchronized void log(Object level, Object obj, Throwable t)
    {
        Level l = Level.INFO;
        if (level != null)
        {
            String ls = String.valueOf(level);
            if (ls.equalsIgnoreCase("warn") || ls.equalsIgnoreCase("warning"))
                l = Level.WARNING;
            else if (ls.equalsIgnoreCase("severe") || ls.equalsIgnoreCase("error"))
                l = Level.SEVERE;
        }

        int lineNumber = -1;
        String sourceFile = "<Unknown Source>";
        String methodName = "";

        if (t != null)
        {
            StackTraceElement[] ss = t.getStackTrace();
            if (ss.length > 0)
            {
                lineNumber = ss[0].getLineNumber();
                sourceFile = ss[0].getFileName();
                methodName = ss[0].getMethodName();
            }

            if (logger == null)
            {
                print(new Date()+"  "+sourceFile+" "+methodName+" ["+lineNumber+"]  "+obj+"\n");
                print(toString(t)+"\n");
            }
            else
                logger.logp(l, sourceFile, methodName+" ("+lineNumber+")", String.valueOf(obj), t);
        }
        else
        {
            StackTraceElement[] ss = new Exception().getStackTrace();
            for (int i=0; i<ss.length; i++)
            {
                StackTraceElement s = ss[i];
                if (!s.getClassName().startsWith("jdk.nashorn.internal.scripts.Script"))
                    continue;

                sourceFile = s.getFileName();
                lineNumber = s.getLineNumber();
                methodName = s.getMethodName();
            }

            if (logger == null)
                print(new Date()+"  "+sourceFile+" "+methodName+" ["+lineNumber+"]  "+obj+"\n");
            else
                logger.logp(l, sourceFile, methodName+" ("+lineNumber+")", String.valueOf(obj));
        }
    }

    public synchronized String getAndClearJJSPOutput()
    {
        return getAndClearJJSPOutput(true);
    }

    public synchronized String getAndClearJJSPOutput(boolean stripMultipleBlankLines)
    {
        if (outputWriter.getBuffer().length() == 0)
            return "";

        outputWriter.flush();
        String result = outputWriter.toString();
        if (stripMultipleBlankLines)
            result = stripMultipleBlankLines(result);

        outputWriter.getBuffer().setLength(0);
        return result;
    }

    public synchronized String getJJSPOutput()
    {
        outputWriter.flush();
        return outputWriter.toString();
    }

    public synchronized boolean isTopLevelSource()
    {
        return isMainJJSPFile();
    }

    public synchronized boolean isMainJJSPFile()
    {
        return TOP_LEVEL_SOURCE_PATH.equals(getCurrentSourcePath());
    }

    public synchronized String getCurrentSourcePath()
    {
        if (scriptEngine == null)
            return "<Unknown JJSP Source>";
        return (String) scriptEngine.get(ScriptEngine.FILENAME);
    }

    public String include(String sourcePath) throws Exception
    {
        return include(sourcePath, false);
    }

    public String cinclude(String sourcePath) throws Exception
    {
        return conditionalInclude(sourcePath);
    }

    public String conditionalInclude(String sourcePath) throws Exception
    {
        return include(sourcePath, true);
    }

    public String include(String sourcePath, boolean onlyIfNotAlreadyIncluded) throws Exception
    {
        synchronized (this)
        {
            if (initialised())
                throw new IllegalStateException("Cannot include files after initialisation is complete");
            if (onlyIfNotAlreadyIncluded && includedFiles.contains(sourcePath))
                return "\n  [Already included '"+sourcePath+"' - not including again]";
        }

        URI srcURI = null;
        String description = "external";
        String originalSourcePath = null;

        try
        {
            URIContent uriContent = loadFromResourcePath(sourcePath);
            srcURI = uriContent.resolvedURI;
            addResourcePathRoot(srcURI);

            String jsSource = uriContent.asString();

            String lower = sourcePath.toLowerCase();
            if (lower.endsWith(".jjsp") || lower.endsWith(".jet"))
            {
                ScriptParser jjspParser = new ScriptParser(jsSource);
                jsSource = jjspParser.translateToJavascript();
                description = "JJSP Script";
            }
            else if (lower.endsWith(".jf"))
                description = "JJSP Javascript";

            synchronized (this)
            {
                originalSourcePath = (String) scriptEngine.get(ScriptEngine.FILENAME);
                scriptEngine.put(ScriptEngine.FILENAME, srcURI.toString());

                outputWriter.write("\nIncluding JJSP resource: "+srcURI+"   ");
            }

            scriptEngine.eval(jsSource);

            synchronized (this)
            {
                includedFiles.add(sourcePath);
                return "\nIncluded "+description+" resource: "+sourcePath+"\n";
            }
        }
        finally
        {
            synchronized (this)
            {
                if (originalSourcePath != null)
                    scriptEngine.put(ScriptEngine.FILENAME, originalSourcePath);
                removeResourcePathRoot(srcURI);
            }
        }
    }

    public String parse(String sourcePath) throws Exception
    {
        URI srcURI = null;
        String originalSourcePath = null;

        try
        {
            URIContent uriContent = loadFromResourcePath(sourcePath);
            srcURI = uriContent.resolvedURI;
            addResourcePathRoot(srcURI);
            String jsSource = uriContent.asString();

            String lower = sourcePath.toLowerCase();
            if (lower.endsWith(".jjsp") || lower.endsWith(".jet"))
            {
                ScriptParser jjspParser = new ScriptParser(jsSource);
                jsSource = jjspParser.translateToJavascript();
            }
            else if (!lower.endsWith(".js"))
                throw new IllegalStateException("Can only use the 'parse' command with JJSP and JS source files");

            originalSourcePath = (String) scriptEngine.get(ScriptEngine.FILENAME);
            scriptEngine.put(ScriptEngine.FILENAME, srcURI.toString());

            StringBuffer buf = new StringBuffer();
            buf.append("(function (){var macroResult = \"\"; var $p = function(arg) { if (!(typeof arg == 'undefined')) macroResult += arg;}; ");
            buf.append(jsSource);
            buf.append("return macroResult;}());");

            return (String) scriptEngine.eval(buf.toString());
        }
        finally
        {
            if (originalSourcePath != null)
                scriptEngine.put(ScriptEngine.FILENAME, originalSourcePath);
            removeResourcePathRoot(srcURI);
        }
    }

    @Override
    public Class getServiceClass(String className, String serviceName) throws Exception
    {
        URLClassLoader loader = getServiceLoader(serviceName);
        return loader.loadClass(className);
    }

    public Object loadLibraryService(String mainClassName) throws Exception
    {
        return loadLibraryService(mainClassName, null);
    }

    public Object loadLibraryService(String mainClassName, ScriptObjectMirror props) throws Exception
    {
        return loadServiceFromClassLoader(mainClassName, getLibraryLoader(), props);
    }

    public Object loadLibraryService(String mainClassName, Map props) throws Exception
    {
        return loadServiceFromClassLoader(mainClassName, getLibraryLoader(), null, props);
    }

    public Object loadService(String mainClassName, String serviceName) throws Exception
    {
        return loadService(mainClassName, serviceName, null);
    }

    public Object loadService(String mainClassName, String serviceName, ScriptObjectMirror props) throws Exception
    {
        return loadServiceFromClassLoader(mainClassName, getServiceLoader(serviceName), props);
    }

    public Object loadService(String mainClassName, String serviceName, Map props) throws Exception
    {
        return loadServiceFromClassLoader(mainClassName, getServiceLoader(serviceName), null, props);
    }

    public Object loadServiceFromClassLoader(String mainClassName, ClassLoader cl) throws Exception
    {
        return loadServiceFromClassLoader(mainClassName, cl, null);
    }

    public Object loadServiceFromClassLoader(String mainClassName, ClassLoader cl, ScriptObjectMirror props) throws Exception
    {
        return loadServiceFromClassLoader(mainClassName, cl, props, null);
    }

    public Object loadServiceFromClassLoader(String mainClassName, ClassLoader cl, ScriptObjectMirror props, Map propMap) throws Exception
    {
        if (propMap == null)
            propMap = new HashMap();

        try
        {
            propMap.putAll(props);
        }
        catch (Exception e) {}

        Object instance = null;
        Class cls = cl.loadClass(mainClassName);
        Constructor[] cc = cls.getConstructors();

        for (int i=0; i<cc.length; i++)
        {
            Class[] argTypes = cc[i].getParameterTypes();
            if (argTypes.length != 2)
                continue;
            if (Map.class.isAssignableFrom(argTypes[0]) && ClassLoader.class.isAssignableFrom(argTypes[1]))
            {
                instance = cc[i].newInstance(new Object[]{propMap, cl});
                break;
            }
        }

        if (instance == null)
        {
            for (int i=0; i<cc.length; i++)
            {
                Class[] argTypes = cc[i].getParameterTypes();
                if (argTypes.length != 1)
                    continue;

                if (Map.class.isAssignableFrom(argTypes[0]))
                {
                    instance = cc[i].newInstance(new Object[]{propMap});
                    break;
                }
                else if (ClassLoader.class.isAssignableFrom(argTypes[0]))
                {
                    instance = cc[i].newInstance(new Object[]{cl});
                    break;
                }
            }
        }

        if (instance == null)
            instance = cls.newInstance();

        return instance;
    }

    public HTTPRequestHandlerAdapter createFilter(String functionName, HTTPRequestHandler jsHandlerFunction, HTTPRequestFilter chain) throws Exception
    {
        return new HTTPRequestHandlerAdapter(functionName, jsHandlerFunction, chain);
    }

    public PredicateFilter createPredicateFilter(String functionName, HTTPRequestPredicate predicate, HTTPRequestFilter yes, HTTPRequestFilter no) throws Exception
    {
        return new PredicateFilter(functionName, predicate, yes, no);
    }

    public PathMappedFilter createPathMappedFilter(String name, HTTPRequestFilter def)
    {
        return new PathMappedFilter(name, true, def);
    }

    public PathMappedFilter createPathMappedFilter(String name, boolean leadingMatchOnKeys, HTTPRequestFilter def)
    {
        return new PathMappedFilter(name, leadingMatchOnKeys, def);
    }

    public PathMappedFilter createPathMappedFilter(String name, String defaultMimeType, HTTPRequestFilter def)
    {
        PathMappedFilter result = createPathMappedFilter(name, def);
        if (defaultMimeType != null)
            result.setDefaultMimeType(defaultMimeType);
        return result;
    }

    public PathMappedFilter createPathMappedFilter(String name, String defaultMimeType, boolean leadingMatchOnKeys, HTTPRequestFilter def)
    {
        PathMappedFilter result = createPathMappedFilter(name, leadingMatchOnKeys, def);
        if (defaultMimeType != null)
            result.setDefaultMimeType(defaultMimeType);
        return result;
    }

    public HTTPMethodFilter createHTTPMethodFilter(String name, HTTPRequestFilter headFilter, HTTPRequestFilter getFilter, HTTPRequestFilter postFilter)
    {
        return new HTTPMethodFilter(name, headFilter, getFilter, postFilter);
    }

    public FixedResponseFilter createFixedDataFilter(String name, String type)
    {
        return createFixedDataFilter(name, type, null);
    }

    public FixedResponseFilter createFixedDataFilter(String name, String type, String arg)
    {
        Class cls = FixedResponseFilter.class;
        Method[] mm = cls.getDeclaredMethods();

        Method matchingMethod = null;
        for (int i=0; i<mm.length; i++)
        {
            Method m = mm[i];
            if (!Modifier.isStatic(m.getModifiers()) || !Modifier.isPublic(m.getModifiers()))
                continue;

            m.getName().toLowerCase();
            if (m.getName().toLowerCase().indexOf(type.toLowerCase()) < 0)
                continue;

            if ((arg != null) && (m.getParameterCount() == 2) || (arg == null) && (m.getParameterCount() == 1))
            {
                if (matchingMethod != null)
                    throw new IllegalStateException("Failed to find unique filter for type description '"+type+"' - please examine method names in FixedResponseFilter and choose a unique substring and argument count for unambiguous identification");
                matchingMethod = m;
            }
        }

        if (matchingMethod == null)
            throw new IllegalStateException("Failed to find suitable filter for type description '"+type+"' - please examine method names in FixedResponseFilter and choose a unique substring for unambiguous identification");

        try
        {
            if (matchingMethod.getParameterCount() == 2)
                return (FixedResponseFilter) matchingMethod.invoke(null, new Object[]{name, arg});
            else if (matchingMethod.getParameterCount() == 1)
                return (FixedResponseFilter) matchingMethod.invoke(null, new Object[]{name});
            else
                throw new IllegalStateException("Method '"+matchingMethod+"' does not accept 1 or 2 parameters");
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Failed to create instance of FixedResponseFilter for type '"+type+"'", e);
        }
    }

    public StaticDataFilter createStaticDataFilter(String name, Object fixedData)
    {
        return createStaticDataFilter(name, null, "text/html; charset=utf-8", fixedData, null);
    }

    public StaticDataFilter createStaticDataFilter(String name, String contentType, Object fixedData)
    {
        if (contentType == null)
            contentType = "text/html; charset=utf-8";
        return createStaticDataFilter(name, null, contentType, fixedData, null);
    }

    public StaticDataFilter createStaticDataFilter(String name, String fixedURI, Object fixedData, HTTPRequestFilter chain)
    {
        String contentType = "text/html; charset=utf-8";
        if (fixedURI != null)
            contentType = HTTPHeaders.guessMIMEType(fixedURI);

        return createStaticDataFilter(name, fixedURI, contentType, fixedData, chain);
    }

    public StaticDataFilter createStaticDataFilter(String name, String fixedURI, String contentType, Object fixedData, HTTPRequestFilter chain)
    {
        byte[] rawData = new byte[0];
        if (fixedData instanceof byte[])
            rawData = (byte[]) fixedData;
        else
            rawData = Utils.getAsciiBytes(fixedData.toString());

        return new StaticDataFilter(name, fixedURI, rawData, 3600, contentType, chain);
    }

    public StaticDataFilter createStaticLocalFilter(String name, String localResourceName)
    {
        return createStaticLocalFilter(name, null, "text/html; charset=utf-8", localResourceName, null);
    }

    public StaticDataFilter createStaticLocalFilter(String name, String contentType, String localResourceName)
    {
        if (contentType == null)
            contentType = "text/html; charset=utf-8";
        return createStaticLocalFilter(name, null, contentType, localResourceName, null);
    }

    public StaticDataFilter createStaticLocalFilter(String name, String fixedURI, String contentType, String localResourceName, HTTPRequestFilter chain)
    {
        if (contentType == null)
            contentType = "text/html; charset=utf-8";
        return new StaticDataFilter(name, fixedURI, getLocal(localResourceName), 3600, contentType, chain);
    }

    public StaticDataFilter createStaticLocalFilter(String name, String fixedURI, String contentType, String localResourceName, int cacheTimeSecs, HTTPRequestFilter chain)
    {
        if (contentType == null)
            contentType = "text/html; charset=utf-8";
        return new StaticDataFilter(name, fixedURI, getLocal(localResourceName), cacheTimeSecs, contentType, chain);
    }

    public StaticDataFilter createCompressedStaticDataFilter(StaticDataFilter src)
    {
        byte[] raw = src.getRawBytes();
        byte[] compressed = Utils.gzip(raw);

        StaticDataFilter result = new StaticDataFilter(src.getName(), src.getFixedURLPath(), compressed, src.getCacheTimeSeconds(), src.getContentType(), src.getFilterChain());
        result.setContentEncoding("gzip");
        return result;
    }

    public StaticDataFilter createCompressedStaticDataFilter(String name, String fixedURI, Object fixedData, HTTPRequestFilter chain)
    {
        return createCompressedStaticDataFilter(createStaticDataFilter(name, fixedURI, fixedData, chain));
    }

    public StaticDataFilter createCompressedStaticDataFilter(String name, String fixedURI, String contentType, Object fixedData, HTTPRequestFilter chain)
    {
        return createCompressedStaticDataFilter(createStaticDataFilter(name, fixedURI, contentType, fixedData, chain));
    }

    public StaticDataFilter createCompressedStaticLocalFilter(String name, String fixedURI, String contentType, String localResourceName, HTTPRequestFilter chain)
    {
        return createCompressedStaticDataFilter(createStaticLocalFilter(name, fixedURI, contentType, localResourceName, chain));
    }

    public StaticDataFilter createCompressedStaticLocalFilter(String name, String fixedURI, String contentType, String localResourceName, int cacheTimeSecs, HTTPRequestFilter chain)
    {
        return createCompressedStaticDataFilter(createStaticLocalFilter(name, fixedURI, contentType, localResourceName, cacheTimeSecs, chain));
    }

    public ProxyFilter createProxyFilter(String name, String targetHost, int targetPort, HTTPRequestFilter chain)
    {
        return new ProxyFilter(name, targetHost, targetPort, chain);
    }

    public class LocalFilter extends AbstractRequestFilter
    {
        private int cacheTime;
        private HashSet validPaths;
        private String pathPrefix, defaultContentType, contentEncoding;

        public LocalFilter(String name, String[] paths, int cacheTime, HTTPRequestFilter chain)
        {
            super(name, chain);

            this.cacheTime = cacheTime;
            pathPrefix = "";
            defaultContentType = null;
            contentEncoding = null;

            validPaths = new HashSet();
            for (int i=0; i<paths.length; i++)
            {
                String p = paths[i];
                if (!p.startsWith("/"))
                    p = "/"+p;
                validPaths.add(p);
            }
        }

        public void setContentEncoding(String encoding)
        {
            contentEncoding = encoding;
        }

        public String getPathPrefix()
        {
            return pathPrefix;
        }

        public void setDefaultContentType(String type)
        {
            defaultContentType = type;
        }

        public void setPathPrefix(String prefix)
        {
            if ((prefix == null) || (prefix.length() == 0))
                pathPrefix = "";
            else if (!prefix.startsWith("/"))
                prefix = "/"+prefix;
            pathPrefix = prefix;
        }

        public void setCacheTimeSeconds(int seconds)
        {
            cacheTime = seconds;
        }

        protected byte[] getContentFor(String path)
        {
            return getLocal(path);
        }

        @Override
        protected boolean handleRequest(HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
        {
            String path = request.getHeaders().getPath();

            if (!path.startsWith("/"))
                return false;
            if (pathPrefix != null)
            {
                if (!path.startsWith(pathPrefix))
                    return false;
                path = path.substring(pathPrefix.length());
            }

            if (!validPaths.contains(path))
                return false;

            byte[] content = getContentFor(path);
            if (content == null)
                return false;

            response.getHeaders().configureCacheControl(cacheTime);
            response.getHeaders().configureAsOK();
            response.getHeaders().guessAndSetContentType(path, defaultContentType);
            if (contentEncoding != null)
                response.getHeaders().setContentEncoding(contentEncoding);

            if (request.getHeaders().isHead())
            {
                response.getHeaders().setContentLength(content.length);
                response.sendHeaders();
            }
            else
                response.sendContent(content);

            return true;
        }
    }

    public class LocalCompressedFilter extends LocalFilter
    {
        private HashMap compressedContentCache;

        public LocalCompressedFilter(String name, String[] paths, int cacheTime, HTTPRequestFilter chain)
        {
            super(name, paths, cacheTime, chain);

            compressedContentCache = new HashMap();
            setContentEncoding("gzip");
        }

        @Override
        protected byte[] getContentFor(String path)
        {
            byte[] compressed = null;
            synchronized (compressedContentCache)
            {
                compressed = (byte[]) compressedContentCache.get(path);
                if (compressed != null)
                    return compressed;
            }

            byte[] raw = super.getContentFor(path);
            if (raw == null)
                return null;

            compressed = Utils.gzip(raw);
            synchronized (compressedContentCache)
            {
                compressedContentCache.put(path, compressed);
                return compressed;
            }
        }
    }

    public LocalFilter createLocalStorageFilter(String name, HTTPRequestFilter chain)
    {
        return createLocalStorageFilter(name, null, true, 300, chain);
    }

    public LocalFilter createLocalStorageFilter(String name, String globMatch, HTTPRequestFilter chain)
    {
        return createLocalStorageFilter(name, globMatch, true, 300, chain);
    }

    public LocalFilter createLocalStorageFilter(String name, String globMatch, int cacheTimeSeconds, HTTPRequestFilter chain)
    {
        return createLocalStorageFilter(name, globMatch, true, cacheTimeSeconds, chain);
    }

    public String[] globMatchLocalStoragePaths(String globMatch)
    {
        return globMatchLocalStoragePaths(globMatch, true);
    }

    public String[] globMatchLocalStoragePaths(String globMatch, boolean includeMatches)
    {
        String[] paths = listLocal();
        if (globMatch == null)
            return paths;

        globMatch = globMatch.replace("*.*", "*");
        int star = globMatch.indexOf("*");
        if (star < 0)
            star = globMatch.length();

        String prefix = globMatch.substring(0, star);
        if (!prefix.startsWith("/"))
            prefix = "/"+prefix;
        String suffix = "";
        if (star < globMatch.length())
            suffix = globMatch.substring(star+1);

        ArrayList buf = new ArrayList();
        for (int i=0; i<paths.length; i++)
        {
            String path = paths[i];
            boolean matches = path.startsWith(prefix) && ((suffix.length() == 0) || path.endsWith(suffix));
            if (!includeMatches)
                matches = !matches;

            if (matches)
                buf.add(path);
        }

        String[] result = new String[buf.size()];
        buf.toArray(result);
        return result;
    }

    public LocalFilter createLocalStorageFilter(String name, String globMatch, boolean includeMatches, int cacheTimeSeconds, HTTPRequestFilter chain)
    {
        String[] paths = null;
        if (globMatch != null)
            paths = globMatchLocalStoragePaths(globMatch, includeMatches);
        else
            paths = listLocal();

        return new LocalFilter(name, paths, cacheTimeSeconds, chain);
    }

    public LocalCompressedFilter createCompressedLocalFilter(String name, HTTPRequestFilter chain)
    {
        return createCompressedLocalFilter(name, null, true, 300, chain);
    }

    public LocalCompressedFilter createCompressedLocalFilter(String name, String globMatch, HTTPRequestFilter chain)
    {
        return createCompressedLocalFilter(name, globMatch, true, 300, chain);
    }

    public LocalCompressedFilter createCompressedLocalFilter(String name, String globMatch, boolean includeMatches, int cacheTimeSeconds, HTTPRequestFilter chain)
    {
        String[] paths = null;
        if (globMatch != null)
            paths = globMatchLocalStoragePaths(globMatch, includeMatches);
        else
            paths = listLocal();

        return new LocalCompressedFilter(name, paths, cacheTimeSeconds, chain);
    }

    public ContentTypeFilter createContentTypeFilter(String name, HTTPRequestFilter defaultFilter)
    {
        return new ContentTypeFilter(name, defaultFilter);
    }

    public SSLRedirectFilter createSSLRedirectFilter(String name, HTTPRequestFilter onwardChain)
    {
        return new SSLRedirectFilter(name, onwardChain);
    }

    public SSLRedirectFilter createSSLRedirectFilter(String name, String sslHostName, HTTPRequestFilter onwardChain)
    {
        return new SSLRedirectFilter(name, sslHostName, onwardChain);
    }

    public PortFilter createPortFilter(String name, HTTPRequestFilter onwardChain)
    {
        return new PortFilter(name, onwardChain);
    }

    public HTTPAuthFilter createAuthenticationFilter(String name, HTTPRequestFilter authFailFilter, HTTPRequestFilter authOKFilter)
    {
        return new HTTPAuthFilter(name, authFailFilter, authOKFilter);
    }

    public HTTPAuthFilter createAuthenticationFilter(String name, String realm, HTTPRequestFilter authFailFilter, HTTPRequestFilter authOKFilter)
    {
        return new HTTPAuthFilter(name, realm, authFailFilter, authOKFilter);
    }

    public ErrorFilter createErrorFilter(String name, HTTPRequestFilter mainFilter)
    {
        return new ErrorFilter(name, mainFilter);
    }

    public ErrorFilter createErrorFilter(String name, HTTPRequestFilter errorFilter, HTTPRequestFilter mainFilter)
    {
        return new ErrorFilter(name, errorFilter, mainFilter);
    }

    public DirectoryFilter createDirectoryFilter(String directoryName, HTTPRequestFilter chain) throws IOException
    {
        return createDirectoryFilter(directoryName, null, chain);
    }

    public DirectoryFilter createDirectoryFilter(String directoryName, String pathPrefix, HTTPRequestFilter chain) throws IOException
    {
        return new DirectoryFilter(new File(directoryName), pathPrefix, chain);
    }

    public ResponseHeadersFilter createResponseHeadersFilter(String name, Map headerKeyValues, HTTPRequestFilter mainFilter)
    {
        return new ResponseHeadersFilter(name, headerKeyValues, mainFilter);
    }

    public synchronized void registerShutdownHook(Function f)
    {
        shutdownHook = f;
    }

    public synchronized HTTPRequestFilter setMainRequestFilter(HTTPRequestFilter filter)
    {
        if (initialised())
            throw new IllegalStateException("Cannot set main request filter after JJSP has started");
        if (filter.getClass().toString().indexOf("$$NashornJavaAdapter") >= 0)
            throw new IllegalStateException("Invalid filter object in setMainRequestFilter - wrap javascript functions first with a call to 'createFilter'");

        mainFilter = filter;
        return filter;
    }

    public synchronized HTTPRequestFilter getMainRequestFilter()
    {
        return mainFilter;
    }

    public Sitemap createSitemap(String urlStem)
    {
        return new Sitemap(urlStem);
    }

    public SitemapIndex createSitemapIndex(String urlStem)
    {
        return new SitemapIndex(urlStem);
    }

    public RobotsTxt createRobotsTxt(String urlStem)
    {
        return new RobotsTxt(urlStem);
    }

    public HTTPServerLogger createMemoryLogger(int logLength)
    {
        return new MemoryLog(logLength);
    }

    public synchronized HTTPServerLogger getHTTPLogger()
    {
        return httpLogger;
    }

    public synchronized HTTPServerLogger setHTTPLogDirectory(File logDir, boolean autoFlush) throws IOException
    {
        if (initialised())
            throw new IllegalStateException("Cannot set HTTP Log Directory after JJSP has started");
        this.httpLogger = new DirectoryFileLogger(10, DirectoryFileLogger.DAILY, logDir);
        return httpLogger;
    }

    public synchronized HTTPServerLogger setHTTPLogger(HTTPServerLogger httpLogger)
    {
        if (initialised())
            throw new IllegalStateException("Cannot set HTTP Logger after JJSP has started");
        this.httpLogger = httpLogger;
        return httpLogger;
    }

    public Object getJSONValue(Object json, String path)
    {
        return JSONParser.getValue(json, path);
    }

    public String toJSON(Object obj)
    {
        return toJSONString(obj);
    }

    public String toJSON(Object obj, boolean withNewlines)
    {
        return toJSONString(obj, withNewlines);
    }

    public String toJSONString(Object obj)
    {
        return JSONParser.toString(obj);
    }

    public String toJSONString(Object obj, boolean withNewlines)
    {
        return JSONParser.toString(obj, withNewlines);
    }
    
    public String prettyPrint(Object obj)
    {
        return JSONParser.prettyPrint(obj);
    }
    
    public String prettyPrintJSON(Object obj)
    {
        return JSONParser.prettyPrint(obj);
    }

    public Object parseJSON(Object s)
    {
        if (s == null)
            return null;
        return JSONParser.parse(s.toString());
    }

    public Object fromJSON(String className, String serviceName, Object jsonObject) throws Exception
    {
        Class cls = getServiceClass(serviceName, className);
        Method m = cls.getDeclaredMethod("fromJSON", new Class[]{Object.class});
        return m.invoke(null, new Object[]{jsonObject});
    }

    public Object fromJSONString(String className, String serviceName, String jsonString) throws Exception
    {
        return fromJSON(className, serviceName, parseJSON(jsonString));
    }

    public void stop()
    {
        stopRequested = true;
    }

    public boolean stopRequested()
    {
        return stopRequested;
    }

    private static StringBuffer getArgList(int paramCount)
    {
        StringBuffer argList = new StringBuffer();
        for (int j = 0; j < paramCount; j++)
        {
            if (j > 0)
                argList.append(",");
            argList.append("arg" + j);
        }
        return argList;
    }

    public static String wrapFunctionScript(Class target, String jsVariable)
    {
        Method[] mm = target.getDeclaredMethods();

        String result = "";
        Map<String, List<Method>> grouped = new HashMap<>();
        for (int i=0; i<mm.length; i++)
        {
            int mods = mm[i].getModifiers();
            if (!Modifier.isPublic(mods))
                continue;
            if (Modifier.isStatic(mods))
                continue;
            if (!grouped.containsKey(mm[i].getName()))
                grouped.put(mm[i].getName(), new ArrayList<>());
            grouped.get(mm[i].getName()).add(mm[i]);
        }

        for (String name: grouped.keySet())
        {
            List<Method> methods = grouped.get(name);
            if (methods.size() == 1)
            {
                int paramCount = methods.get(0).getParameterCount();
                StringBuffer argList = getArgList(paramCount);

                result += name + " = function(" + argList + "){return " + jsVariable + "." + name + "(" + argList + ");};\n";
            }
            else
            {
                int maxParamCount = 0;
                SortedSet<Integer> argCounts = new TreeSet<>();
                for (Method m: methods)
                {
                    maxParamCount = Math.max(maxParamCount, m.getParameterCount());
                    argCounts.add(m.getParameterCount());
                }

                result += name + " = function(" + getArgList(maxParamCount) + "){";
                for (int args : argCounts)
                    result += "if (typeof arg"+(args)+" == 'undefined')\n    return "+ jsVariable + "." + name + "(" + getArgList(args) + ");\n";
                result += "};\n";
            }
        }

        return result;
    }

    public static String toString(Throwable err)
    {
        if (err == null)
            return "";

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bout);
        for (Throwable tt = err; tt != null; tt = tt.getCause())
            tt.printStackTrace(ps);
        ps.close();

        return Utils.toString(bout.toByteArray());
    }
}
