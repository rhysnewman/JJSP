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
package jjsp.data;

import java.io.*;
import java.net.*;
import java.util.*;

import jjsp.util.*;
import jjsp.http.*;

public class DataServer extends HTTPServer
{
    public DataServer(Map<String, DataInfoIndex> dataIndices, boolean queryShortcutMode, HTTPServerLogger logger) throws Exception
    {
        super(new VersionDataFilter(dataIndices, queryShortcutMode), logger);
    }

    protected void connectionError(InetSocketAddress clientAddress, int serverPort, boolean isSecure, Throwable e)
    {
        e.printStackTrace();
    }

    static class VersionDataFilter extends AbstractRequestFilter
    {
        final TreeMap pathMapIndex;
        final boolean queryShortcutMode;
        
        VersionDataFilter(Map<String, DataInfoIndex> dataIndices, boolean queryShortcutMode)
        {
            super("Version Data Filter", null);

            pathMapIndex = new TreeMap();
            this.queryShortcutMode = queryShortcutMode;
            
            Iterator<String> itt = dataIndices.keySet().iterator();
            while (itt.hasNext())
            {
                String key = itt.next();
                registerDataInfoIndex(key, dataIndices.get(key));
            }
        }

        void registerDataInfoIndex(String urlPathPrefix, DataInfoIndex infoIndex)
        {
            if (urlPathPrefix.startsWith("/"))
                urlPathPrefix = urlPathPrefix.substring(1);
            if (urlPathPrefix.endsWith("/"))
                urlPathPrefix = urlPathPrefix.substring(0, urlPathPrefix.length()-1);

            pathMapIndex.put(urlPathPrefix, infoIndex);
        }

        DataInfo lookupDataWithQueryShortcut(DataInfoIndex infoIndex, String reqQuery) throws IOException
        {
            String verSpec = "";
            DataInfo[] versions = infoIndex.getVersions(reqQuery);

            if (queryShortcutMode && ((versions == null) || (versions.length == 0)))
            {
                int dot = reqQuery.lastIndexOf(".");
                if ((dot >= 0) && (dot < reqQuery.length()-1))
                {
                    verSpec = reqQuery.substring(dot+1).trim();
                    reqQuery = reqQuery.substring(0, dot);
                    versions = infoIndex.getVersions(reqQuery);
                }
            }

            if ((versions == null) || (versions.length == 0))
                return null;
            if (!queryShortcutMode)
                return null;
            
            if (verSpec.startsWith("[") && verSpec.endsWith("]"))
            {
                verSpec = verSpec.substring(1, verSpec.length()-1).trim();
                try
                {
                    int position = Integer.parseInt(verSpec);
                    if (position < 0)
                        position = Math.max(0, versions.length + position);
                    else
                        position = Math.min(versions.length-1, position);

                    return versions[position];
                }
                catch (Exception e) {}
            }

            if (verSpec.length() >= 4)
            {
                if (verSpec.startsWith("["))
                    verSpec = verSpec.substring(1).trim();
                if (verSpec.endsWith("]"))
                    verSpec = verSpec.substring(verSpec.length()-1).trim();
                
                for (int i=0; i<versions.length; i++)
                {
                    DataInfo info = versions[i];
                    if (info.createdString.startsWith(verSpec) || info.contentHash.startsWith(verSpec) || info.versionName.startsWith(verSpec))
                        return info;
                }
            }
            else
                return versions[0];

            return null;
        }
        
        DataInfo lookupDataWithQueryParameters(DataInfoIndex infoIndex, String keyName, Map params) throws IOException
        {
            DataInfo[] versions = infoIndex.getVersions(keyName);
            if ((versions == null) || (versions.length == 0))
                return null;

            String hashSpec = (String) params.get("hash");
            String versionNameSpec = (String) params.get("versionName");
            String createdSpec = (String) params.get("created");
            int versionIndex = -1;
            try
            {
                versionIndex = Integer.parseInt((String) params.get("index"));
            }
            catch (Exception e) {}

            for (int i=0; i<versions.length; i++)
            {
                DataInfo di = versions[i];
                
                if ((hashSpec != null) && (hashSpec.length() >= 4) && di.contentHash.startsWith(hashSpec))
                    return di;
                if ((versionNameSpec != null) && versionNameSpec.equals(di.versionName))
                    return di;
                if ((createdSpec != null) && (createdSpec.length() >= 4) && di.createdString.startsWith(createdSpec))
                    return di;
                if (i == versionIndex)
                    return di;
            }

            return null;
        }

        String jsonRegisteredInfoSources()
        {
            String[] prefices = new String[pathMapIndex.size()];
            pathMapIndex.keySet().toArray(prefices);

            StringBuffer buffer = new StringBuffer("[\n");
            for (int i=0; i<prefices.length; i++)
                buffer.append("\""+prefices[i]+"\",\n");

            if (prefices.length > 0)
                buffer.setLength(buffer.length()-2);
            buffer.append("\n]\n");

            return buffer.toString();
        }

        String jsonListing(DataInfoIndex infoIndex, String leadingMatch) throws IOException
        {
            StringBuffer buffer = new StringBuffer("[\n");

            int matchCount = 0;
            String[] keys = infoIndex.getKeyNames();
            for (int i=0; i<keys.length; i++)
            {
                if ((leadingMatch != null) && !keys[i].startsWith(leadingMatch))
                    continue;

                DataInfo[] versions = infoIndex.getVersions(keys[i]);
                DataInfo info = versions[0];

                buffer.append("{\"keyName\": \""+info.keyName+"\", " +
                        "\"versionName\": \""+info.versionName+"\", " +
                        "\"length\": "+info.length+", " +
                        "\"created\": \""+info.createdString+"\", " +
                        "\"time\":"+info.created.getTime()+", " +
                        "\"contentHash\": \""+info.contentHash+"\", " +
                        "\"versions\": "+versions.length+"},\n");
                matchCount++;
            }
            
            if (matchCount > 0)
                buffer.setLength(buffer.length()-2);
            buffer.append("\n]\n");
            
            return buffer.toString();
        }

        String versionListing(DataInfo[] versions) throws IOException
        {
            StringBuffer buffer = new StringBuffer("[\n");

            for (int i=0; i<versions.length; i++)
            {
                DataInfo info = versions[i];
                buffer.append("{\"versionIndex\": "+i+", \"keyName\": \""+info.keyName+"\", \"versionName\": \""+info.versionName+"\", \"length\":"+info.length+", \"created\": \""+info.createdString+"\", \"time\":"+info.created.getTime()+", \"contentHash\": \""+info.contentHash+"\"},\n");
            }
            if (versions.length > 0)
                buffer.setLength(buffer.length()-2);
            buffer.append("\n]\n");

            return buffer.toString();
        }

        protected boolean handleRequest(HTTPInputStream request, HTTPOutputStream response, ConnectionState state) throws IOException
        {
            String reqPath = request.getHeaders().getPath();
            if (reqPath.startsWith("/"))
                reqPath = reqPath.substring(1);

            if (reqPath.length() == 0)
            {
                response.getHeaders().configureAsOK();
                response.sendJSON(jsonRegisteredInfoSources());
                return true;
            }

            int slash = reqPath.indexOf("/");
            String urlPathPrefix = "";
            if (slash > 0)
            {
                urlPathPrefix = reqPath.substring(0, slash);
                reqPath = reqPath.substring(slash+1);
            }

            DataInfoIndex infoIndex = (DataInfoIndex) pathMapIndex.get(urlPathPrefix);
            if (infoIndex == null)
            {
                response.getHeaders().configureAsOK();
                response.sendJSON(jsonRegisteredInfoSources());
                return true;
            }

            if (reqPath.length() == 0)
            {
                response.getHeaders().configureAsOK();
                response.sendJSON(jsonListing(infoIndex, null));
                return true;
            }

            DataInfo info = null;
            Map reqParams = request.getHeaders().getQueryParameters();
            if (reqParams.size() > 0)
                info = lookupDataWithQueryParameters(infoIndex, reqPath, reqParams);
            else if (queryShortcutMode && ((request.getHeaders().getQueryString() != null) || reqPath.endsWith(".")))
            {
                if (reqPath.endsWith("."))
                    reqPath = reqPath.substring(0, reqPath.length()-1).trim();

                DataInfo[] versions = infoIndex.getVersions(reqPath);
                if ((versions != null) && (versions.length > 0))
                {
                    response.getHeaders().configureAsOK();
                    response.sendJSON(versionListing(versions));
                    return true;
                }
            }
            else
            {
                info = lookupDataWithQueryShortcut(infoIndex, reqPath);
                if (info == null)
                {
                    if (reqPath.endsWith("/"))
                        info = lookupDataWithQueryShortcut(infoIndex, reqPath.substring(0, reqPath.length()-1));
                }

                if (info == null)
                {
                    response.getHeaders().configureAsOK();
                    response.sendJSON(jsonListing(infoIndex, reqPath));
                    return true;
                }
            }
            
            if (info != null)
            {
                InputStream src = infoIndex.getDataStream(info);
                try
                {
                    if (src != null)
                    {
                        response.getHeaders().configureCacheControl(info.contentHash, info.created.getTime(), 86400); // Cache for a day!
                        response.getHeaders().configureAsOK();
                        response.getHeaders().setContentType(info.mimeType);
                        response.getHeaders().setHeader("Data-Content-Hash", info.contentHash);
                        response.getHeaders().setHeader("Data-Version", info.versionName);
                        response.getHeaders().setHeader("Data-Date", info.createdString);
                        response.getHeaders().setHeader("Data-Time", ""+info.created.getTime());
                        response.getHeaders().setHeader("Content-Disposition", "attachment; filename=\""+info.keyName+"\"");

                        if (info.length >= 0)
                            response.prepareToSendContent(info.length, false);
                        else
                            response.prepareToSendContent(info.length, true);
                    
                        byte[] buffer = new byte[1024*1024];
                        while (true)
                        {
                            int r = src.read(buffer);
                            if (r < 0)
                                break;
                            response.write(buffer, 0, r);
                        }
                        
                        response.close();
                        return true;
                    }
                }
                finally
                {
                    src.close();
                }
            }

            response.getHeaders().configureAsNotFound();
            response.sendHeaders();
            return true;
        }
    }

    public static Map<String, DataInfoIndex> parseDataInfoConfig(String configFileContents) throws Exception
    {
        Map<String, DataInfoIndex> dataIndices = new HashMap<>();
        String[] configFileLines = configFileContents.split("\n");
            
        for (int i=0; i<configFileLines.length; i++)
        {
            String line = configFileLines[i].trim();
            if (line == null)
                continue;
            if ((line.length() == 0) || (line.startsWith("#")))
                continue;
                    
            String[] parts = line.split(" ");
            if (parts[0].startsWith("dir"))
            {
                File dir = new File(parts[1]);
                if (!dir.exists() || !dir.isDirectory())
                    throw new IllegalStateException("Cannot file directory "+dir);
                String stem = parts[2];
                        
                DataInfoIndex infoIndex = new FileSystemInfoIndex(dir);
                dataIndices.put(stem, infoIndex);
            }
            else if (parts[0].startsWith("db") || parts[0].startsWith("data"))
            {
                String url = parts[1];
                if (!url.startsWith("jdbc:"))
                    url = "jdbc:mysql://"+url;
                String stem = parts[4];
                        
                WritableDBDataInfoIndex infoIndex = new WritableDBDataInfoIndex(url, parts[2], parts[3]);
                dataIndices.put(stem, infoIndex);
            }
            else
                throw new IllegalStateException("Unknown config file directive on line "+i+":  "+line);
        }

        return dataIndices;
    }

    public static void main(String[] args) throws Exception
    {
        Args.parse(args);

        int port = Args.getInt("port", 80);
        String cfgFileName = Args.getArg("config", "DataServer.config");
        boolean queryShortcutMode = Args.getBoolean("queryShortcut", true);
        
        System.out.println(cfgFileName);
        String configFileContents = Utils.loadText(new File(cfgFileName));
        System.out.println(configFileContents);

        Map<String, DataInfoIndex> dataIndices = parseDataInfoConfig(configFileContents);
        
        /*dataIndices.put("files", findex);
        dataIndices.put("db", dbInfo);

        File fdi = new File("/home/ftpuser/data/");
        fdi.mkdir();
        FileSystemInfoIndex findex = new FileSystemInfoIndex(fdi);

        SimpleDirectoryDataIndex cache = new SimpleDirectoryDataIndex(new File(".DataCache"));
        CachedDataIndex cc = new CachedDataIndex(dbInfo, cache, 5000, true);

        Map<String, DataInfoIndex> dataIndices = new HashMap<>();
        dataIndices.put("files", findex);
        dataIndices.put("db", dbInfo);*/

        DataServer server = new DataServer(dataIndices, queryShortcutMode, new PrintStreamLogger());
        //Server server = new Server(dataIndices, queryShortcutMode, null);
        server.listenOn(port, false, null);

        System.out.println("Versioned Data Server listening on port "+port);
    }
}
