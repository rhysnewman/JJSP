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
import java.util.regex.*;
import java.text.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jjsp.http.*;
import jjsp.util.*;

public class SimpleDirectoryDataIndex implements WritableDataInfoIndex
{
    private static final String DELIMITER = ":$&:";

    private final File directory;
    private final OrderedValueMap index;

    public SimpleDirectoryDataIndex(File dir) throws IOException
    {
        this.directory = dir;

        directory.mkdir();
        index = new OrderedValueMap();
        scanDirectory();
    }

    private void scanDirectory() throws IOException
    {
        File[] ff = directory.listFiles();
        for (int i=0; i<ff.length; i++)
        {
            String name = ff[i].getName();
            if (!name.endsWith(".fic"))
                continue;
            name = name.substring(0, name.length()-4);

            try
            {
                String raw = Utils.toAsciiString(Base64.getUrlDecoder().decode(name));
                String[] parts = raw.split(Pattern.quote(DELIMITER));

                String keyName = parts[0];
                String versionName = parts[1];
                String contentHash = parts[2];
                Date created = new Date(Long.parseLong(parts[3]));

                SimpleFileDataInfo info = new SimpleFileDataInfo(ff[i], keyName, versionName, contentHash, created);
                index.putValueFor(keyName, info);
            }
            catch (Exception e)
            {
                System.out.println("WARNING: Failed to load data info from file "+ff[i]+"  "+e);
            }
        }
    }

    public synchronized DataInfo writeVersion(DataInfo info, byte[] contents) throws IOException
    {
        DataInfo existing = (DataInfo) index.getMatchingValue(info.keyName, info);
        if (existing != null)
            return existing;

        if ((info.length >= 0) && (info.length != contents.length))
            throw new IOException("Data length mismatch betwen info "+info.length+" and supplied data "+contents.length);

        String hashCheck = Utils.toHexString(Utils.generateMD5Checksum(contents));
        if (!info.contentHash.equals(hashCheck))
            throw new IOException("Content hash mismatch - data corrupted");

        String rawFileName = Stream.of(info.keyName, info.versionName, info.contentHash, Long.toString(info.created.getTime())).collect(Collectors.joining(DELIMITER));
        String fileName = Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.getAsciiBytes(rawFileName))+".fic";

        File ff = new File(directory, fileName);
        try
        {
            FileOutputStream fout = new FileOutputStream(ff);
            fout.write(contents);
            fout.close();
        }
        catch (Exception e)
        {
            try
            {
                ff.delete();
            }
            catch (Exception ee) {}
            throw new IOException(e);
        }

        info = new SimpleFileDataInfo(ff, info.keyName, info.versionName, info.contentHash, info.created);
        index.putValueFor(info.keyName, info);
        return info;
    }

    class SimpleFileDataInfo extends DataInfo
    {
        final File file;

        SimpleFileDataInfo(File file, String keyName, String versionName, String hash, Date created) throws IOException
        {
            super(keyName, versionName, file.length(), hash, created);
            this.file = file;
        }
    }

    public String[] getKeyNames() throws IOException
    {
        return index.getOrderedKeys();
    }

    public DataInfo[] getVersions(String key) throws IOException
    {
        Comparable[] values = index.getAllValuesFor(key);
        if (values == null)
            return null;

        DataInfo[] result = new DataInfo[values.length];
        System.arraycopy(values, 0, result, 0, values.length);
        return result;
    }
    
    public InputStream getDataStream(DataInfo info) throws IOException
    {
        SimpleFileDataInfo sfi = (SimpleFileDataInfo) index.getMatchingValue(info.keyName, info);
        if (sfi == null)
            return null;
        return new FileInputStream(sfi.file);
    }

    public static String encodeToFilename(String str, String extension)
    {
        if (extension == null)
            extension = "";
        if ((extension.length() > 0) && (!extension.startsWith(".")))
            extension = "."+extension;

        return Base64.getUrlEncoder().withoutPadding().encodeToString(Utils.getAsciiBytes(str))+extension;
    }
    
    public String toString()
    {
        return "SimpleDirectoryDataIndex["+directory+"]";
    }
}
