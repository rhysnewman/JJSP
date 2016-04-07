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
import java.nio.file.*;
import java.util.logging.*;

public class Log 
{
    public static void set() throws IOException 
    {
        set("jet_log");
    }

    public static void set(String directory) throws IOException 
    {
        int maxLength = 1024 * 1024 * 10;
        int count = 10;
        set(directory, maxLength, count, "log", new SimpleFormatter());
    }

    public static void set(String directory, int maxLength, int count) throws IOException 
    {
        set(directory, maxLength, count, "log", new SimpleFormatter());
    }

    public static void set(String directory, int maxLength, int count, String name, Formatter formatter) throws IOException 
    {
        Path dir = Paths.get(directory);
        dir.toFile().mkdirs();

        Path path = dir.resolve(name);
        FileHandler handler = new FileHandler(path.toString(), maxLength, count, true);
        handler.setFormatter(formatter);

        Logger.getGlobal().addHandler(handler);
        Logger.getGlobal().setUseParentHandlers(false);
    }
}
