
package jjsp.util;

public class UniqueID
{
    private long lastMS, lastValue;
    private int seqCounter, serialNumber;

    public UniqueID(int serialNumber)
    {
        this.serialNumber = serialNumber;
        seqCounter = 0;
        lastMS = 0;
        lastValue = 0;
    }

    public synchronized long getNewID()
    {
        long time = System.currentTimeMillis();
        if (time != lastMS)
            seqCounter = 0;
            
        lastMS = time;
        long value = generateID(time, seqCounter++, serialNumber);
        if (value <= lastValue)
            throw new IllegalStateException("Unique ID generation failed - clock time has slipped into the past");
        lastValue = value;
        return value;
    }
    
    public static String toString(long id)
    {
        return Long.toHexString(id).toUpperCase();
    }

    /** Generates a unique ID based on Twitter's Snowflake approach.
        1) Top bit is always 0, for non-negative ordering.
        2) Time in milliseconds with 41 bits goes from 1970 to 2039
        3) Sequence number that can be incremented by a process on a machine without reference to anything else.
        4) Serial Number - a number centrally registered to identify the process/data store and location of the data associated with the ID.
           Note: data stores can be reassigned with a time based allocation when stores move location etc.
    */
    public static long generateID(long timeMillis, int sequenceNumber, int serialNumber)
    {
        return ((0x1FFFFFFFFFFl & timeMillis) << 22) | ((0xFFF & sequenceNumber) << 10) | (0x3FF & serialNumber);
    }
}


/*
Example type safe use case:

public class ID
{
    static UniqueID uid = new UniqueID(1);

    private final long id;
    
    private ID(long val)
    {
        id = val;
    }

    public static ID getID(long val)
    {
        return new ID(val);
    }

    public static ID getID()
    {
        return new ID(uid.getNewID());
    }
    }*/
