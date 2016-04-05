package jjsp.data;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;

import jjsp.http.*;

public interface WritableDataInfoIndex extends DataInfoIndex
{
    public DataInfo writeVersion(DataInfo info, byte[] contents) throws IOException;
}
