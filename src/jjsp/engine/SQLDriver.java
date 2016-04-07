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
import java.sql.*;
import java.util.*;

import javax.script.*;

import jjsp.util.*;

public class SQLDriver implements AutoCloseable
{
    public static final int MAX_CONNECTIONS = 1024;

    public static final String URL = "url";
    public static final String USER = "user";
    public static final String DRIVER = "driver";
    public static final String PASSWORD = "password";
    
    private URI dbURI;
    private Map props;
    private boolean closed;
    private Driver dbDriver;
    private HashSet allConnections;
    private LinkedList connections;
    private String dbURL, user, password;

    public SQLDriver(Map props, ClassLoader serviceLoader) throws Exception
    {
        closed = false;
        String driverName = (String) props.get(DRIVER);
        if (driverName == null)
            driverName = "com.mysql.jdbc.Driver";

        user = (String) props.get(USER);
        if (user == null)
            throw new IllegalStateException("No 'user' specified for database connection");

        password = (String) props.get(PASSWORD);
        if (password == null)
            throw new IllegalStateException("No 'password' specified for database connection");

        dbURL = (String) props.get(URL);
        if (dbURL == null)
            throw new IllegalStateException("No 'url' specified for database connection");
        
        if (serviceLoader == null)
            serviceLoader = getClass().getClassLoader();
        dbDriver = (Driver) serviceLoader.loadClass(driverName).newInstance();
        this.props = props;
        
        if (dbURL.startsWith("jdbc:"))
            dbURI = new URI(dbURL.substring(5));
        else
            dbURI = new URI(dbURL);

        allConnections = new HashSet();
        connections = new LinkedList();
    }

    public synchronized boolean isClosed()
    {
        return closed;
    }

    public URI getDatabaseURI()
    {
        return dbURI;
    }

    public class ConnectionWrapper implements AutoCloseable
    {
        Thread currentOwner;
        Connection connection;

        private Statement statement;
        private HashMap preparedStatementCache;

        ConnectionWrapper(Connection conn) throws SQLException
        {
            connection = conn;
            connection.setAutoCommit(false);
            currentOwner = null;
        }
        
        public URI getDatabaseURI()
        {
            return SQLDriver.this.getDatabaseURI();
        }

        public synchronized Connection getConnection()
        {
            return connection;
        }

        public synchronized boolean isOwner()
        {
            return Thread.currentThread() == currentOwner;
        }

        public synchronized Statement getStatement() throws SQLException
        {
            if (Thread.currentThread() != currentOwner)
                throw new IllegalStateException("Current thread is not the valid owner of the pooled database connection of "+getDatabaseURI());
            if ((statement == null) || statement.isClosed())
                statement = connection.createStatement();
            return statement;
        }

        public synchronized PreparedStatement getPreparedStatement(String sql) throws SQLException
        {
            if (Thread.currentThread() != currentOwner)
                throw new IllegalStateException("Current thread is not the valid owner of the pooled database connection of "+getDatabaseURI());
            if (preparedStatementCache == null)
                preparedStatementCache = new HashMap();
            
            PreparedStatement result = (PreparedStatement) preparedStatementCache.get(sql);
            if ((result != null) && !result.isClosed())
                return result;
            result = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            preparedStatementCache.put(sql, result);
            return result;
        }

        public synchronized void disposePreparedStatement(String sql)
        {
            if (Thread.currentThread() != currentOwner)
                return;
            if (preparedStatementCache == null)
                return;

            PreparedStatement result = (PreparedStatement) preparedStatementCache.remove(sql);
            try
            {
                result.close();
            }
            catch (Exception e) {}
        }

        synchronized void setOwner(Thread t)
        {
            currentOwner = t;
        }

        synchronized void closeInternal()
        {
            setOwner(null);
            try
            {
                connection.close();
            }
            catch (Exception e) {}
        }

        public synchronized void rollback() throws SQLException
        {
            if (Thread.currentThread() != currentOwner)
                throw new IllegalStateException("Thread not owner of SQL Connection ["+getDatabaseURI()+"]");
            connection.rollback();
        }

        public synchronized void commit() throws SQLException
        {
            if (Thread.currentThread() != currentOwner)
                throw new IllegalStateException("Thread not owner of SQL Connection ["+getDatabaseURI()+"]");
            connection.commit();
        }

        public void close() throws Exception
        {
            returnToPool();
        }
    
        public boolean returnToPool()
        {
            try
            {
                rollback();
            }
            catch (Throwable t) {}
                
            setOwner(null);
            return SQLDriver.this.returnToPool(this);
        }
    }

    private void dumpConnections()
    {
        synchronized (connections)
        { 
            int size = allConnections.size();
            while (true)
            {
                if (allConnections.size() <= size/2)
                    return;
                if (connections.isEmpty())
                    break;

                ConnectionWrapper cw = (ConnectionWrapper) connections.removeLast();
                cw.closeInternal();
                allConnections.remove(cw);
            }

            if (allConnections.size() < MAX_CONNECTIONS)
                return;
                
            Iterator itt = allConnections.iterator();
            while (itt.hasNext())
            {
                ConnectionWrapper cw = (ConnectionWrapper) itt.next();
                cw.closeInternal();
            }
            
            allConnections.clear();
            connections.clear();
        }
    }

    public ConnectionWrapper getConnection(long timeout) throws IOException
    {
        long start = System.currentTimeMillis();
        while (true)
        {
            ConnectionWrapper cw = null;
            
            synchronized (connections)
            { 
                if (closed)
                    throw new IOException("Connection pool to "+getDatabaseURI()+" closed");
                if (!connections.isEmpty())
                    cw = (ConnectionWrapper) connections.removeFirst();
            }
            
            if (cw == null)
            {
                try
                {
                    Properties pp = new Properties();
                    pp.putAll(props);
                    pp.remove(dbURL);

                    synchronized (dbDriver)
                    {
                        cw = new ConnectionWrapper(dbDriver.connect(dbURL, pp));
                    }

                    synchronized (connections)
                    { 
                        if (closed)
                        {
                            cw.closeInternal();
                            throw new IOException("Connection pool closed");
                        }
                        if (allConnections.size() >= MAX_CONNECTIONS)
                            dumpConnections();
                        allConnections.add(cw);
                    }

                    cw.setOwner(Thread.currentThread());
                    return cw;
                }
                catch (Exception e) 
                {
                    long time = System.currentTimeMillis();
                    long waitTime = timeout - (time - start);
                    if (waitTime <= 0)
                        throw new IOException("Failed to connect to database "+getDatabaseURI()+" within specified time limit of "+timeout+" ms");
                    
                    try
                    {
                        Thread.sleep(1000);
                    }
                    catch (Exception ee) {}
                }
            }
            else
            {
                try
                {
                    if (cw.connection.isValid(100))
                    {
                        cw.setOwner(Thread.currentThread());
                        return cw;
                    }
                }
                catch (Throwable e) {}
                
                try
                {
                    synchronized (connections)
                    { 
                        allConnections.remove(cw);
                    }
                    cw.closeInternal();
                }
                catch (Exception e) {}
            }
        }
    }

    public boolean returnToPool(ConnectionWrapper cw)
    {
        if (cw == null)
            return false;

        cw.setOwner(null);
        synchronized (connections)
        {
            if (allConnections.contains(cw))
            {
                connections.push(cw);
                return true;
            }
        }
        
        cw.closeInternal();
        return false;
    }

    public void close() throws Exception
    {
        synchronized (connections)
        {
            closed = true;
            
            Iterator itt = allConnections.iterator();
            while (itt.hasNext())
            {
                ConnectionWrapper cw = (ConnectionWrapper) itt.next();
                cw.closeInternal();
            }
            
            allConnections.clear();
            connections.clear();
        }
    }
    
    public static SQLDriver createConnection(String url, String user, String password) throws Exception
    {
        return createConnection(url, user, password, null);
    }

    public static SQLDriver createConnection(String url, String user, String password, ClassLoader loader) throws Exception
    {
        HashMap m = new HashMap();
        m.put(URL, url);
        m.put(USER, user);
        m.put(PASSWORD, password);
        return new SQLDriver(m, loader);
    }
}
