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
import java.text.*;

import jjsp.http.*;

public class OrderedValueMap
{
    private TreeMap index;

    public OrderedValueMap()
    {
        index = new TreeMap();
    }

    public synchronized String[] getOrderedKeys()
    {
        String[] result = new String[index.size()];
        index.keySet().toArray(result);
        return result;
    }

    public synchronized Comparable[] getAllValuesFor(String key)
    {
        ArrayList values = (ArrayList) index.get(key);
        if (values == null)
            return null;

        Comparable[] result = new Comparable[values.size()];
        values.toArray(result);
        return result;
    }

    public synchronized int getNumberOfValuesFor(String key)
    {
        ArrayList values = (ArrayList) index.get(key);
        if (values == null)
            return 0;
        return values.size();
    }
    
    public synchronized Comparable getValueFor(String key, int position)
    {
        ArrayList values = (ArrayList) index.get(key);
        if ((values == null) || (position < 0) || (position >= values.size()))
            return null;
        return (Comparable) values.get(position);
    }

    public synchronized Comparable getMatchingValue(String key, Comparable target)
    {
        ArrayList values = (ArrayList) index.get(key);
        if (values == null) 
            return null;
        
        for (int i=0; i<values.size(); i++)
            if (target.equals(values.get(i)))
                return (Comparable) values.get(i);
        return null;
    }
    
    public synchronized int getIndexOfValue(String key, Comparable target)
    {
        ArrayList values = (ArrayList) index.get(key);
        if (values == null) 
            return -1;

        for (int i=0; i<values.size(); i++)
            if (target.equals(values.get(i)))
                return i;
        return -1;
    }

    public synchronized Comparable getLargestValueFor(String key)
    {
        ArrayList values = (ArrayList) index.get(key);
        if ((values == null) || (values.size() == 0))
            return null;
        return (Comparable) values.get(0);
    }

    public synchronized Comparable getSmallestValueFor(String key)
    {
        ArrayList values = (ArrayList) index.get(key);
        if ((values == null) || (values.size() == 0))
            return null;
        return (Comparable) values.get(values.size()-1);
    }

    public synchronized void putKey(String key)
    {
        ArrayList values = (ArrayList) index.get(key);
        if (values == null)
        {
            values = new ArrayList();
            index.put(key, values);
        }
    }

    public synchronized void putValueFor(String key, Comparable aValue)
    {
        ArrayList values = (ArrayList) index.get(key);
        if (values == null)
        {
            values = new ArrayList();
            index.put(key, values);
        }
        
        if (aValue == null)
            return;

        for (int j=0; j<values.size(); j++)
        {
            Comparable ff = (Comparable) values.get(j);
            if (aValue.compareTo(ff) > 0)
            {
                values.add(j, aValue);
                return;
            }
            else if (aValue.compareTo(ff) == 0) 
            {
                if (!aValue.equals(ff))
                    values.add(j, aValue);
                return;
            }
        }
        
        values.add(aValue);
    }

    public static void main(String[] args) 
    {
        OrderedValueMap om = new OrderedValueMap();

        om.putValueFor("test", new Date(1200000));
        om.putValueFor("test", new Date(0));
        om.putValueFor("test", new Date(60000));

        System.out.println(om.getLargestValueFor("test"));
        
        Comparable[] cc = om.getAllValuesFor("test");
        for (int i=0; i<cc.length; i++)
            System.out.println(cc[i]);
    }
}
