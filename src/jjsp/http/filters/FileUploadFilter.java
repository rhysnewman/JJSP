package jjsp.http.filters;

import java.io.*;
import java.lang.ref.*;
import java.net.*;
import java.util.*;
import java.text.*;

import jjsp.http.*;
import jjsp.util.*;

/**
   A handler which allows HTTP post requests to upload content into a directory. Often used in conjunction with a basic DirectoryHandler 
 */
public class FileUploadFilter extends DirectoryFilter
{
    public static final int MAX_UPLOAD_SIZE = 8*1024*1024;

    protected int maxUploadSize;
    protected boolean allowDeletion;
    
    public FileUploadFilter(File directory, HTTPRequestFilter filterChain) throws IOException
    {
        this(directory, false, "", filterChain);
    }

    public FileUploadFilter(File directory, String pathPrefix, HTTPRequestFilter filterChain) throws IOException
    {
        this(directory, false, pathPrefix, filterChain);
    }

    public FileUploadFilter(File directory, boolean allowDeletion, HTTPRequestFilter filterChain) throws IOException
    {
        this(directory, allowDeletion, "", filterChain);
    }

    public FileUploadFilter(File directory, boolean allowDeletion, String pathPrefix, HTTPRequestFilter filterChain) throws IOException
    {
        super(directory, pathPrefix, filterChain);
        maxUploadSize = MAX_UPLOAD_SIZE;
        this.allowDeletion = allowDeletion;
    }

    public void setMaxUploadSize(int size)
    {
        maxUploadSize = Math.max(0, size);
    }
    
    protected DataSource getDirectoryHTMLListPage(File dir)
    {
        byte[] directoryHTML = generateHTMLDirectoryListingWithUpload(allowDeletion, pathPrefix, rootDirectory, dir, selectFilesFromDirectory(dir));
        return new ByteArrayDataSource(directoryHTML);
    }

    public static byte[] generateHTMLDirectoryListingWithUpload(boolean allowDeletion, String pathPrefix, File root, File dir, File[] files)
    {
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd    HH:mm:ss", DateFormatSymbols.getInstance(Locale.US));
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bout);

        ps.println("<HTML><BODY>");
        ps.println("<form action='"+pathPrefix+relativePath(root, dir)+"' method='POST' enctype='multipart/form-data'>");
        ps.println("Upload File <input type='file' name='target' size='80'/>");
        ps.println("<input type='submit' name='submit' value='Upload'/>");
        ps.println("</form>");
        ps.println("<HR>");

        ps.println("<TABLE>");
        if (allowDeletion)
            ps.println("<TR><TH width=40></TH><TH width=500 align=left>File Name</TH><TH width=250 align=right>Modified</TH><TH width=100 align=right>Size</TH><TH width=50 align=right></TH></TR>");
        else
            ps.println("<TR><TH width=40></TH><TH width=500 align=left>File Name</TH><TH width=250 align=right>Modified</TH><TH width=100 align=right>Size</TH></TR>");

        URI rootURI = root.toURI();
        for (int i=0; i<files.length; i++)
        {
            String relativePath = relativePath(root, files[i]);

            String lastModified = dateFormat.format(new Date(files[i].lastModified()));
            ps.print("<TR>");
            ps.print("<TD>"+(i+1)+"</TD>");
            ps.print("<TD><a href=\""+pathPrefix+relativePath+"\">"+Utils.URLDecode(relativePath.substring(1))+"</a></TD>");
            ps.print("<TD align=right>"+lastModified+"</TD>");
            
            if (files[i].isDirectory())
                ps.print("<TD align=right><font color=green>DIR</font></TD>");
            else
                ps.print("<TD align=right>"+formatLength(files[i].length())+"</TD>");
            if (allowDeletion && !files[i].isDirectory())
                ps.println("<TD align=right><a href=\""+pathPrefix+relativePath+"?delete=true\">Delete</a></TD>");
            ps.println("</TR>");
        }
        ps.println("</TABLE></BODY></HTML>");
        ps.flush();
        
        return bout.toByteArray();
    }

    private String getDirectoryListURL(HTTPInputStream request)
    {
        String resultList = request.getHeaders().getAbsoluteURL();
        if (!resultList.endsWith("/"))
        {
            int slash = Math.max(0, resultList.lastIndexOf("/"));
            resultList = resultList.substring(0, slash)+"/";
        }
        return resultList;
    }

    protected String handlePostRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException 
    {
        String urlPath = request.getHeaders().getPath();
        String pathString = getResourcePathForRequestPath(urlPath);
        if (pathString == null)
        {
            response.getHeaders().configureAsNotFound();
            response.sendHeaders();
            return "Not Found "+urlPath;
        }

        String resultList = getDirectoryListURL(request);
        response.getHeaders().configureAsRedirect(resultList);

        try
        {
            if (!request.contentAvailable())
                return "No data submitted";

            File target = new File(rootDirectory, pathString);
            if (!accessPermitted(target))
            {
                response.getHeaders().configureAsForbidden();
                response.getHeaders().configureToPreventCaching();
                return "Permission Denied";
            }

            String type = request.getHeaders().getHeader("Content-Type", null);
            if ((type == null) || type.equalsIgnoreCase("application/octet-stream"))
            {
                byte[] buffer = new byte[2*1024*1024];
                String fileName = request.getHeaders().getQuery("filename", null);

                if ((fileName != null) && (fileName.length() > 0))
                    target = new File(target, fileName);
                else
                {
                    response.getHeaders().configureAsBadRequest("Required filename parameter missing");
                    return "No Filename in upload";
                }
                
                target.getParentFile().mkdirs();
                target.createNewFile();

                FileOutputStream fout = new FileOutputStream(target);
                while (true)
                {
                    int r = request.read(buffer);
                    if (r < 0)
                        break;
                    fout.write(buffer, 0, r);
                }
                fout.close();

                return "Accepted binary data: "+target;
            }
            else if (type.indexOf("multipart/form-data") >= 0)
            {
                int total = 0;
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                while (true)
                {
                    int read = request.read(buffer);
                    if (read < 0)
                        break;
                    
                    bout.write(buffer, 0, read);
                    total += read;
                    if (total > maxUploadSize)
                    {
                        response.getHeaders().configureAsTooLarge();
                        return "Maximum upload size reached";
                    }
                }
                
                HTMLFormPart[] parts = HTMLFormPart.processPostedFormData(request.getHeaders(), bout.toByteArray());
                for (int i=0; i<parts.length; i++)
                {
                    if ("target".equalsIgnoreCase(parts[i].getAttribute("name")))
                    {
                        String fileName = parts[i].getAttribute("filename");
                        if ((fileName != null) && (fileName.length() > 0))
                        {
                            byte[] rawData = parts[i].getData();
                            File f = new File(target, fileName);
                            f.getParentFile().mkdirs();
                            f.createNewFile();
                            FileOutputStream fout = new FileOutputStream(f);
                            fout.write(rawData);
                            fout.close();
                            
                            return "Accepted HTML form data: "+f;
                        }
                    }
                }
                
                return "No upload data found";
            }
            else
                return "Invalid POST for data upload";
        }
        finally
        {
            response.sendHeaders();
        }
    }

    protected String handleGetRequest(HTTPFilterChain chain, HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException 
    {
        String urlPath = request.getHeaders().getPath();
        String pathString = getResourcePathForRequestPath(urlPath);
        if (pathString == null)
        {
            response.getHeaders().configureAsNotFound();
            response.sendHeaders();
            return "Not Found "+urlPath;
        }

        if (!request.getHeaders().hasQueryParam("delete"))
            return super.handleGetRequest(chain, request, response, state);

        String report = "Deletion of "+pathString+" denied";
        if (allowDeletion)
        {
            File f = new File(rootDirectory, pathString);
            if (accessPermitted(f) && f.exists() && f.isFile())
            {
                f.delete();
                report = "Deleted "+urlPath;
            }
        }

        String resultList = getDirectoryListURL(request);
        response.getHeaders().configureAsRedirect(resultList);
        response.sendHeaders();
        return report;
    }
}
