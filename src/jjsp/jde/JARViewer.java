package jjsp.jde;

import java.net.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.util.jar.*;
import java.lang.reflect.*;

import javafx.scene.*;
import javafx.scene.paint.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.geometry.*;

import jjsp.engine.*;
import jjsp.util.*;
import jjsp.http.*;

public class JARViewer extends JDEComponent
{
    protected Map jarIndex, attrMap;
    protected HTTPServer archiveServer;
    protected ColouredTextArea details;
    protected String mainDocURL, mainIndexURL;
    protected URLClassLoader jarLoader;

    private SharedTextEditorState sharedState;

    public JARViewer(URI uri, SharedTextEditorState sharedState)
    {
        super(uri);

        this.sharedState = sharedState;
        jarIndex = new TreeMap();

        try
        {
            ClassLoader pcl = getClass().getClassLoader();
            try
            {
                Environment env = JDE.getEnvironment();
                pcl = env.createLibraryLoader();
                
                URL[] allJars = Environment.getJarURLsFromDirectoryURI(uri.resolve("."));
                jarLoader = new URLClassLoader(allJars, pcl);
            }
            catch (Exception e) {}

            if (jarLoader == null)
                jarLoader = new URLClassLoader(new URL[]{uri.toURL()}, pcl);
        }
        catch (Exception e) {}
        
        try
        {
            String key = uri.getPath().toLowerCase();
            if (key.endsWith(".jar"))
                initWithJarContent(uri);
            else
                initWithZipContent(uri);

            int freePort = Utils.getFreeSocket(null, 20000, 20100);
            archiveServer = new HTTPServer(new ArchiveContentsFilter(), null);
            archiveServer.listenOn(freePort);

            setCenter(new WebBrowser(new URI("http://localhost:"+freePort+"/")));
        }
        catch (Exception e)
        {
            loadAsPlainText(e);
        }
    }
    
    private void loadAsPlainText(Throwable cause)
    {
        details = new ColouredTextArea(JDETextEditor.EDITOR_TEXT_SIZE);
        details.setSharedTextEditorState(sharedState);
        details.setEditable(false);

        details.setText("");
        details.appendColouredText("JAR Detail for "+getURI()+"\n\n", Color.MAGENTA);

        if (attrMap == null)
            details.appendColouredText("No Manifest Found", Color.BLUE);
        else
        {
            Iterator itt = attrMap.keySet().iterator();
            while (itt.hasNext())
            {
                String key = (String) itt.next();
                String value = (String) attrMap.get(key);
                details.appendColouredText(key+": "+value+"\n", Color.BLUE);
            }
        }

        details.appendColouredText("\n\n", Color.GREEN);

        Iterator itt = jarIndex.keySet().iterator();
        while (itt.hasNext())
        {
            String key = (String) itt.next();
            details.appendColouredText(key+"\n", Color.GREEN);
        }
            
        if (cause != null)
            details.appendColouredText("\n\n"+JDETextEditor.toString(cause), Color.RED);
        setCenter(details);
    }

    public void requestFocus()
    {
        super.requestFocus();
        if (details != null)
            details.requestFocus();
    }

    public void setDisplayed(boolean isShowing) 
    {
        super.setDisplayed(isShowing);
        if (details != null)
            details.setIsShowing(isShowing);
    }

    public Menu[] createMenus()
    {
        MenuItem textOnly = new MenuItem("Show Text Only View");
        textOnly.setOnAction((event) -> loadAsPlainText(null));

        Menu options = new Menu("Options");
        options.getItems().addAll(textOnly);
        return new Menu[]{options};
    }

    public void closeServices()
    {
        try
        {
            archiveServer.close();
        }
        catch (Exception e) {}
    }

    private void initWithJarContent(URI uri) throws Exception
    {
        byte[] raw = Utils.load(uri);

        JarInputStream jin = new JarInputStream(new ByteArrayInputStream(raw));
        Manifest mf = jin.getManifest();
        if (mf != null)
        {
            attrMap = new LinkedHashMap();

            Attributes attrs = mf.getMainAttributes();
            Iterator keys = attrs.keySet().iterator();
            while (keys.hasNext())
            {
                Object key = keys.next();
                Object value = attrs.get(key);
                attrMap.put(key.toString(), value.toString());

                if (key.toString().toLowerCase().startsWith("readme"))
                    mainDocURL = value.toString();
            }
        }

        String shortestHTML = null;
        jin = new JarInputStream(new ByteArrayInputStream(raw));            
        while (true)
        {
            JarEntry jen = jin.getNextJarEntry();
            if (jen == null)
                break;

            try
            {
                byte[] entryValue = Utils.load(jin, false);
                String name = jen.getName();
                if (name.endsWith("/"))
                    continue;

                jarIndex.put(jen.getName(), entryValue);
                jin.closeEntry();
                
                if (name.endsWith("index.html") && ((mainIndexURL == null) || name.length() < mainIndexURL.length()))
                    mainIndexURL = name;
                if (name.endsWith(".html") && ((shortestHTML == null) || name.length() < shortestHTML.length()))
                    shortestHTML = name;
            }
            catch (Exception e) {}
        }

        if (mainIndexURL == null)
            mainIndexURL = shortestHTML;
        if (mainDocURL == null)
            mainDocURL = mainIndexURL;
        if (mainDocURL == null)
            mainDocURL = mainIndexURL = shortestHTML;
    }

    private void initWithZipContent(URI uri) throws Exception
    {
        byte[] raw = Utils.load(uri);
        ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(raw));

        String shortestHTML = null;
        zin = new ZipInputStream(new ByteArrayInputStream(raw));            
        while (true)
        {
            ZipEntry zen = zin.getNextEntry();
            if (zen == null)
                break;

            try
            {
                byte[] entryValue = Utils.load(zin, false);
                String name = zen.getName();
                if (name.endsWith("/"))
                    continue;
                jarIndex.put(name, entryValue);
                zin.closeEntry();
                
                if (name.endsWith("index.html") && ((mainIndexURL == null) || name.length() < mainIndexURL.length()))
                    mainIndexURL = name;
                if (name.endsWith(".html") && ((shortestHTML == null) || name.length() < shortestHTML.length()))
                    shortestHTML = name;
            }
            catch (Exception e) {}
        }

        mainDocURL = mainIndexURL;
        if (mainIndexURL == null)
            mainDocURL = mainIndexURL = shortestHTML;
    }

    class ArchiveContentsFilter extends AbstractRequestFilter
    {
        ArchiveContentsFilter()
        {
            super("JDE Archive Filter", null);
        }

        byte[] generateHTMLJarContents()
        {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(bout);
        
            ps.println("<!DOCTYPE html><HTML><HEAD>");
            ps.println("<style>body {font-family:verdana, arial, helvetica, sans-serif; border:none} td {text-align:left; vertical-align:top} th {padding-bottom: 10px; text-align:left} .doc {color: #990033}</style>");
            ps.println("</HEAD><BODY>");

            String title = getURI().toString();
            int slash = title.lastIndexOf("/");
            if (slash >= 0)
                title = title.substring(slash+1);

            if (mainDocURL != null)
            {
                if (!mainDocURL.startsWith("/"))
                    mainDocURL = "/"+mainDocURL;
                ps.println("<H1><a href='"+mainDocURL+"' style='color:#3366CC'>"+title+"</a></H1>");
            }
            else
                ps.println("<H1 style='color:#3366CC'>"+title+"</H1>");

            ps.println("<h3 style='color:blue'>Full URI: <span style='color:red'>"+getURI()+"</span></h3>");
            if (mainIndexURL != null)
            {
                if (!mainIndexURL.startsWith("/"))
                    mainIndexURL = "/"+mainIndexURL;
                ps.println("<h4><a href='"+mainIndexURL+"'>Documentation Index</a></h4>");
            }

            if (attrMap != null)
            {
                ps.println("<TABLE>");
                ps.println("<TR><TH style='min-width:300px'>Attribute Name</TH><TH>Attribute Value</TH></TR>");
                Iterator itt2 = attrMap.keySet().iterator();
                while (itt2.hasNext())
                {
                    String key = (String) itt2.next();
                    String value = (String) attrMap.get(key);

                    ps.print("<TR>");
                    ps.print("<TD style='color:green'>"+key+"</TD>");
                    if (key.toLowerCase().startsWith("readme") && (mainDocURL != null))
                        ps.print("<TD><a href='"+mainDocURL+"'>"+value+"</a></TD>");
                    else
                        ps.print("<TD>"+value+"</TD>");
                    ps.println("</TR>");
                }
                ps.println("</TABLE>");
                ps.println("<br><br>");
            }

            ps.println("<TABLE>");
            ps.println("<TR><TH colspan='2'>JAR Contents</TH><TH>Size</TH></TR>");
            
            Iterator itt = jarIndex.keySet().iterator();
            for(int i=0; itt.hasNext(); i++)
            {
                String name = (String) itt.next();
                byte[] contents = (byte[]) jarIndex.get(name);
                if (!name.startsWith("/"))
                    name = "/"+name;

                ps.print("<TR>");
                ps.print("<TD style='color:green;min-width:80px'>");
                ps.print(String.valueOf(i+1));
                ps.print("</TD>");
                if (name.endsWith(".html"))
                    ps.print("<TD><a class='doc' href=\""+name+"\">"+name+"</a></TD>");
                else
                    ps.print("<TD><a href=\""+name+"\">"+name+"</a></TD>");

                if (contents.length < 1024)
                    ps.print("<TD style='color:cyan'>"+contents.length+" bytes</TD>");
                else if (contents.length < 1024*1024)
                    ps.print("<TD style='color:blue'>"+String.format("%2.2f", contents.length/1024.0)+" kb</TD>");
                else 
                    ps.print("<TD style='color:magenta'>"+String.format("%2.2f", contents.length/1024.0/1024.0)+" Mb</TD>");
                    
                ps.println("</TR>");
            }
            ps.println("</TABLE></BODY></HTML>");
            ps.flush();

            return bout.toByteArray();
        }

        private String modifiers(int modifiers) throws Exception
        {
            String desc = Modifier.toString(modifiers);
            return "<span style='color:#CC6600'>"+desc+"</span>";
        }

        private void appendExecutableDetails(Executable ex, PrintStream ps) throws Exception
        {
            int mods = ex.getModifiers();
            if (!Modifier.isPublic(mods))
                return;

            String nameDesc = modifiers(mods)+" ";
            int spaceLen = nameDesc.length()-33;
            if (ex instanceof Method)
            {
                Class retType = ((Method) ex).getReturnType();
                String classURL = retType.getName().replace(".", "/")+".class";
                if (jarIndex.get(classURL) != null)
                    nameDesc += "<span style='color:magenta'><a href='/"+classURL+"'>"+retType.getCanonicalName()+"</a></span> ";
                else
                    nameDesc += "<span style='color:magenta'>"+retType.getCanonicalName()+"</span> ";
                spaceLen += retType.getCanonicalName().length();
            }
            
            nameDesc += "<span style='color:green'>"+ex.getName()+"</span>(";
            spaceLen += ex.getName().length();
            ps.print(nameDesc);
            
            StringBuffer space = new StringBuffer();
            for (int i=0; i<spaceLen; i++)
                space.append(" ");

            Class[] params = ex.getParameterTypes();
            if (params.length == 0)
                ps.print(")  ");
            else
            {
                for (int i=0; i<params.length; i++)
                {
                    String classURL = params[i].getName().replace(".", "/")+".class";
                    if (jarIndex.get(classURL) != null)
                        ps.print("<span style='color:magenta'><a href='/"+classURL+"'>"+params[i].getCanonicalName()+"</a></span> arg"+i);
                    else
                        ps.print("<span style='color:magenta'>"+params[i].getCanonicalName()+"</span> arg"+i);
                        
                    if (i == params.length-1)
                        ps.print(")  ");
                    else
                        ps.print(",\n"+space);
                }
            }

            Class[] execeptions = ex.getExceptionTypes();
            if (execeptions.length > 0)
            {
                ps.print("throws ");
                for (int i=0; i<execeptions.length; i++)
                {
                    ps.print("<span style='color:red'>"+execeptions[i].getCanonicalName()+"</span>");
                    if (i < execeptions.length-1)
                        ps.print(", ");
                }
            }

            ps.println();
            ps.println();
        }

        private void generateClassDetails(Class cls, PrintStream ps) throws Exception
        {
            ps.println("<h2>Public Constructors</h2>");
            ps.println("<pre style='font-size:18px'>");

            TreeMap tm = new TreeMap();
            Constructor[] cc = cls.getDeclaredConstructors();
            for (int i=0; i<cc.length; i++)
                tm.put(cc[i].toGenericString(), cc[i]);

            Iterator itt = tm.values().iterator();
            while (itt.hasNext())
                appendExecutableDetails((Executable) itt.next(), ps);
            ps.println("</pre>");

            ps.println("<hr>");
            ps.println("<h2>Public Fields</h2>");

            ps.println("<pre style='font-size:18px'>");
            Field[] ff = cls.getDeclaredFields();
            for (int i=0; i<ff.length; i++)
            {
                int mods = ff[i].getModifiers();
                if (!Modifier.isPublic(mods))
                    continue;

                Class type = ff[i].getType();
                String classURL = type.getName().replace(".", "/")+".class";
                if (jarIndex.get(classURL) != null)
                    ps.print("    "+modifiers(mods)+" <span style='color:magenta'><a href='/"+classURL+"'>"+type.getCanonicalName()+"</a></span> "+ff[i].getName());
                else
                    ps.print("    "+modifiers(mods)+" <span style='color:magenta'>"+type.getCanonicalName()+"</span> "+ff[i].getName());
                
                try
                {
                    Object val = ff[i].get(null);
                    ps.println(" = "+val);
                }
                catch (Exception e) 
                {
                    ps.println();
                }
            }
            ps.println("</pre>");

            ps.println("<hr>");
            ps.println("<h2>Public Methods</h2>");

            ps.println("<pre style='font-size:18px'>");
            TreeMap tm2 = new TreeMap();
            Method[] mm = cls.getDeclaredMethods();
            for (int i=0; i<mm.length; i++)
                tm2.put(mm[i].toGenericString(), mm[i]);
            
            Iterator itt2 = tm2.values().iterator();
            while (itt2.hasNext())
                appendExecutableDetails((Executable) itt2.next(), ps);
            ps.println("</pre>");
        }

        private byte[] generateClassDetails(String className)
        {
            if (className.startsWith("/"))
                className = className.substring(1);
            className = className.replace("/", ".");
            if (className.endsWith(".class"))
                className = className.substring(0, className.length()-6);

            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(bout);
        
            ps.println("<!DOCTYPE html><HTML><HEAD>");
            ps.println("<style>body {font-family:verdana, arial, helvetica, sans-serif; border:none} td {text-align:left; vertical-align:top} th {padding-bottom: 10px; text-align:left} .doc {color: #990033}</style>");
            ps.println("</HEAD><BODY>");

            if (jarLoader == null)
                ps.println("<h1 style='color:red'>CLASS LOADER COULD NOT BE CREATED FOR THIS URI</h1>");
            else
            {
                try
                {
                    Class cls = jarLoader.loadClass(className);
                    ps.println("<H1>"+className+"<H1>");

                    Class superClass = cls.getSuperclass();
                    if (superClass != null)
                    {
                        String superClassURL = superClass.getName().replace(".", "/")+".class";
                        if (jarIndex.get(superClassURL) != null)
                            ps.println("<h2>Extends <span style='color:magenta'><a href='/"+superClassURL+"'>"+superClass.getCanonicalName()+"</a></span></h2>");
                        else
                            ps.println("<h2>Extends <span style='color:magenta'>"+superClass.getCanonicalName()+"</span></h2>");
                    }

                    generateClassDetails(cls, ps);
                }
                catch (Throwable e) 
                {
                    ps.println("<p>Error loading class '"+className+"'</p>");
                    ps.println("<pre style='color:red'>"+JDETextEditor.toString(e)+"</pre>");
                    ps.println("<pre style='color:blue'>"+Environment.getClassloaderDetails(jarLoader)+"</pre>");
                }
            }

            ps.println("</TABLE></BODY></HTML>");
            ps.flush();

            return bout.toByteArray();
        }
        
        protected boolean handleRequest(HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
        {
            String path = request.getHeaders().getPath();

            byte[] content = (byte[]) jarIndex.get(path);
            if (content == null)
                content = (byte[]) jarIndex.get("/"+path);
            if ((content == null) && path.startsWith("/"))
                content = (byte[]) jarIndex.get(path.substring(1));

            String mimeType = HTTPHeaders.guessMIMEType(path, "text/html");
            if ((content != null) && path.endsWith(".class"))
            {
                content = generateClassDetails(path);
                mimeType = "text/html";
            }
            else if (content == null)
            {
                content = generateHTMLJarContents();
                mimeType = "text/html";
            }
            else if (path.endsWith(".json") || path.endsWith(".xml") || path.endsWith(".csv"))
            {
                String jsonContent = Utils.toString(content);
                jsonContent = jsonContent.replace("&", "&amp;");
                jsonContent = jsonContent.replace("<", "&lt;");
                jsonContent = jsonContent.replace(">", "&gt;");
                
                StringBuffer buf = new StringBuffer("<HTML><BODY>\n<pre>\n");
                buf.append(jsonContent);
                buf.append("\n</pre>\n</body></html>");

                content = Utils.getAsciiBytes(buf.toString());
                mimeType = "text/html";
            }

            response.getHeaders().configureCacheControl(-1);
            response.getHeaders().configureAsOK();
            response.getHeaders().setContentType(mimeType);
            response.sendContent(content);
            return true;
        }
    }
}
