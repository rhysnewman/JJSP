package jjsp.util;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;

import java.net.*;
import java.util.*;
import java.security.*;
import java.util.zip.*;

public class ReplicatedFile
{
    private File src;
    private FileLock fileLock;
    private FileChannel fileChannel;

    private boolean autoFlush, isNew;
    private byte[] latestEntryRawData;
    private int startEntryLength, endEntryLength;
    private long startEntryTime, endEntryTime, lastestStoredTime, magicNumber;

    public ReplicatedFile(File src, long magicNumber) throws IOException
    {
        this(src, magicNumber, true);
    }

    public ReplicatedFile(File src, long magicNumber, boolean autoFlush) throws IOException
    {
        this.src = src;
        this.autoFlush = autoFlush;
        this.magicNumber = magicNumber;
        
        if (src == null)
            throw new NullPointerException("No source file");

        isNew = false;
        if (!src.exists() || (src.length() < 2*36))
        {
            formatNewFile();
            isNew = true;
        }

        fileChannel = new RandomAccessFile(src, "rw").getChannel();
        if (fileChannel.size() < 2*(36))
            formatNewFile();

        fileLock = fileChannel.tryLock(); 
        if (fileLock == null)
            throw new IOException("File "+src+" already locked by another process");

        boolean isOK = false;
        try
        {
            if (!readLatestData())
                throw new IOException("Failed to read latest data - invalidly formatted file");
            isOK = true;
        }
        finally
        {
            if (!isOK)
                fileLock.release();
        }
    }

    public boolean newFileWasCreated()
    {
        return isNew;
    }

    public void formatNewFile() throws IOException
    {
        if (src.exists() && (src.length() >= 2*36))
            throw new IOException("File "+src+" exists");
        src.createNewFile();
        RandomAccessFile raf = new RandomAccessFile(src, "rw");
        raf.setLength(2*36);
        raf.seek(0);
        
        long time = System.currentTimeMillis();
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        dout.writeLong(magicNumber);
        dout.writeLong(time);
        dout.writeInt(0); 
        dout.flush();

        byte[] md5 = Utils.generateMD5Checksum(bout.toByteArray());
        dout.write(md5);
        dout.flush();

        byte[] data = bout.toByteArray();
        raf.write(data);
        raf.write(data);
        raf.close();
    }

    public synchronized void storeData(byte[] newData) throws IOException
    {
        if (fileLock == null)
            throw new IOException("File closed");
        if (newData.length + 36 > Integer.MAX_VALUE/2)
            throw new IOException("Data too large - max is "+(Integer.MAX_VALUE/2 - 36));

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        DataOutputStream dout = new DataOutputStream(bout);
        
        dout.writeLong(magicNumber);
        long time = System.currentTimeMillis();
        dout.writeLong(time); // Time entry created
        dout.writeInt(newData.length);
        dout.write(newData);
        dout.flush();
        
        byte[] data = bout.toByteArray();
        byte[] md5 = Utils.generateMD5Checksum(data);
        dout.write(md5);
        dout.flush();

        byte[] entryData = bout.toByteArray();

        if (startEntryTime < endEntryTime)
        {
            if (entryData.length > fileChannel.size()/2)
            {
                fileChannel.position(fileChannel.size()/2);
                ByteBuffer temp = ByteBuffer.allocate((int) (fileChannel.size()/2));
                while (temp.hasRemaining())
                    fileChannel.read(temp);
                
                temp.flip();
                fileChannel.position(0);
                while (temp.hasRemaining())
                    fileChannel.write(temp);
                if (autoFlush)
                    flush();

                startEntryTime = endEntryTime;
                startEntryLength = endEntryLength;
            }
            else
            {
                ByteBuffer buf = ByteBuffer.wrap(entryData);
                fileChannel.position(0);
                while (buf.hasRemaining())
                    fileChannel.write(buf);
                if (autoFlush)
                    flush();

                readLatestData();
                return;
            }
        }

        long newLength = 2*Math.max(startEntryLength + 36, newData.length + 36);
        if (fileChannel.size() > newLength)
        {
            fileChannel.truncate(newLength);
            if (autoFlush)
                flush();
        }
        
        ByteBuffer buf = ByteBuffer.wrap(entryData);
        fileChannel.position(newLength/2);
        while (buf.hasRemaining())
            fileChannel.write(buf);
        if (autoFlush)
            flush();

        readLatestData();
    }
    
    private byte[] readEntry(long offset, long[] time, int[] length) throws IOException
    {
        time[0] = -1;
        length[0] = 0;

        ByteBuffer buf = ByteBuffer.allocate(20);
        fileChannel.position(offset);
        while (buf.hasRemaining())
            fileChannel.read(buf);

        buf.flip();
        if (buf.getLong() != magicNumber)
            return null;
        long t = buf.getLong();
        int len = buf.getInt();
        if ((len < 0) || (len > (fileChannel.size() - 2*36)/2))
            return null;

        byte[] entry = new byte[len + 20];
        ByteBuffer bb1 = ByteBuffer.wrap(entry);
        fileChannel.position(offset);
        while (bb1.hasRemaining())
            fileChannel.read(bb1);

        buf.clear();
        buf.limit(16);
        while (buf.hasRemaining())
            fileChannel.read(buf);

        buf.flip();
        byte[] md5 = Utils.generateMD5Checksum(entry);
        for (int i=0; i<md5.length; i++)
            if (md5[i] != (byte) buf.get())
                return null;

        time[0] = t;
        length[0] = len;
        byte[] entryData = new byte[len];
        System.arraycopy(entry, 20, entryData, 0, len);
        return entryData;
    }

    public synchronized boolean readLatestData() throws IOException
    {
        if (fileLock == null)
            throw new IOException("File closed");
        if (fileChannel.size() < 2*(36))
            throw new IOException("Invalid File "+src+" - length is too short");

        latestEntryRawData = null;
        startEntryTime = endEntryTime = -1;
        startEntryLength = endEntryLength = 0;
        
        long[] startTime = new long[1];
        long[] endTime = new long[1];
        int[] startLength = new int[1];
        int[] endLength = new int[1];

        byte[] startEntry = readEntry(0, startTime, startLength);
        byte[] endEntry = readEntry(fileChannel.size()/2, endTime, endLength);
        
        startEntryTime = startTime[0];
        endEntryTime = endTime[0];
        startEntryLength = startLength[0];
        endEntryLength = endLength[0];

        lastestStoredTime = Math.max(startEntryTime, endEntryTime);
        if ((startEntry != null) && (startEntryTime >= endEntryTime))
        {
            latestEntryRawData = startEntry;
            lastestStoredTime = startEntryTime;
        }
        else if ((endEntry != null) && (startEntryTime < endEntryTime))
        {
            latestEntryRawData = endEntry;
            lastestStoredTime = endEntryTime;
        }
        else
            return false;

        return true;
    }

    public synchronized byte[] getLatestEntryData()
    {
        return getLatestEntryData(null);
    }

    public synchronized byte[] getLatestEntryData(byte[] buffer)
    {
        if (latestEntryRawData == null)
            return null;
        if ((buffer == null) || (buffer.length < latestEntryRawData.length))
            buffer = new byte[latestEntryRawData.length];
        
        System.arraycopy(latestEntryRawData, 0, buffer, 0, latestEntryRawData.length);
        return buffer;
    }

    public synchronized long getLatestSavedTime()
    {
        return lastestStoredTime;
    }

    public synchronized boolean invalidateLatestEntry() throws IOException
    {
        if ((startEntryTime <= 0) || (endEntryTime <= 0))
            return false;

        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putLong(0);
        buf.flip();
        
        if (startEntryTime >= endEntryTime)
            fileChannel.position(0);
        else
            fileChannel.position(fileChannel.size()/2);
        
        while (buf.hasRemaining())
            fileChannel.write(buf);
        if (autoFlush)
            flush();

        return readLatestData();
    }

    public synchronized void flush() throws IOException
    {
        fileChannel.force(true);
    }

    public synchronized void close() throws IOException
    {
        try
        {
            fileLock.release();
        }
        catch (Throwable e) {}

        fileChannel.close();
    }
}
