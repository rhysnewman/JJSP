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

package jjsp.http;

import java.io.*;
import java.net.*;
import javax.net.*;
import javax.net.ssl.*;
import java.util.*;
import java.util.concurrent.*;

import jjsp.util.*;

/**
   A multi-threaded abstract server class which dispatches multiple threads to handle incoming TCP connections. 
 */
public abstract class Server implements ThreadFactory
{
    public static final int CORE_THREAD_COUNT = 100;
    public static final int MAX_THREAD_COUNT = 10000;
    public static final int DEFAULT_SOCKET_TIMEOUT = 60000;
    public static final int SOCKET_BACKLOG = 100000;

    public static final long THREAD_KEEP_ALIVE = 120000;

    public static final int SO_LINGER_TIME = 30; //In Seconds!!
    public static final int SEND_BUFFER_SIZE = 64*1024;
    public static final int RECEIVE_BUFFER_SIZE = 64*1024;
    
    private final int receiveBufferSize, sendBufferSize;

    private long nameCounter;
    private TreeMap acceptorMap;
    private ExecutorService executor;

    private volatile boolean closed;

    public Server()
    {
        this(RECEIVE_BUFFER_SIZE, SEND_BUFFER_SIZE);
    }

    public Server(int receiveBufferSize, int sendBufferSize)
    {
        closed = false;
        acceptorMap = new TreeMap();
        this.receiveBufferSize = receiveBufferSize;
        this.sendBufferSize = sendBufferSize;
        executor = createThreadPool();
    }

    protected void errorOnListen(int port, boolean isSecure, Throwable t) {}
    
    protected void errorOnSocketAccept(int port, boolean isSecure, Throwable t) {}

    protected void configureAcceptedSocket(Socket socket) throws SocketException
    {
        socket.setSoTimeout(DEFAULT_SOCKET_TIMEOUT);
        socket.setReceiveBufferSize(receiveBufferSize);
        socket.setSendBufferSize(sendBufferSize);
        socket.setTcpNoDelay(true);
        socket.setSoLinger(true, SO_LINGER_TIME);
    }

    protected InputStream decorateSocketInputStream(InputStream rawInput)
    {
        return rawInput;
    }

    protected OutputStream decorateSocketOutputStream(OutputStream rawOutput)
    {
        return rawOutput;
    }

    /** The key method for handling a server protocol, typically a case of reading input and sending appropriate output down the wire. 
       */
    protected abstract void handleSocketStreams(InetSocketAddress address, int serverPort, boolean isSecure, InputStream input, OutputStream output) throws IOException;

    protected void connectionTimeout(InetSocketAddress clientAddress, int serverPort, boolean isSecure, Throwable e){}

    protected void connectionError(InetSocketAddress clientAddress, int serverPort, boolean isSecure, Throwable e){}

    public Thread newThread(Runnable r) 
    {
        long nc = 0;
        synchronized (this)
        {
            nc = nameCounter++;
        }
        Thread result = new Thread(r, "Socket Handler Thread "+nc);
        result.setPriority(Thread.NORM_PRIORITY);
        return result;
    }

    protected ExecutorService createThreadPool()
    {
        //System.out.println("\n\n\nWARNING - DEBUG THREAD POOL SIZE = 1");
        //return Executors.newFixedThreadPool(1);
        BlockingQueue q = new ArrayBlockingQueue(1, true);
        return new ThreadPoolExecutor(CORE_THREAD_COUNT, MAX_THREAD_COUNT, THREAD_KEEP_ALIVE, TimeUnit.MILLISECONDS, q, this);
    }

    class SocketHandler implements Runnable
    {
        private int port;
        private Socket socket;
        private boolean isSecure;
        private InputStream input;
        private OutputStream output;
        private SocketAcceptor acceptor;
        private InetSocketAddress clientAddress;

        SocketHandler(SocketAcceptor acceptor, Socket s, int port, boolean isSecure)
        {
            socket = s;
            this.acceptor = acceptor;
            this.port = port;
            this.isSecure = isSecure;
        }

        void close()
        {
            try
            {
                input.close();
            }
            catch (Throwable e) {}

            try
            {
                output.flush();
            }
            catch (Throwable e) {}

            try
            {
                output.close();
            }
            catch (Throwable e) {}

            try
            {
                socket.close();
            }
            catch (Throwable e) {}

            acceptor.socketHandlerClosed(this);
        }

        public void run()
        {
            InetSocketAddress clientAddress = null;
            output = null;
            input = null;

            try
            {
                try
                {
                    clientAddress = new InetSocketAddress(socket.getInetAddress(), socket.getPort());
                    isSecure = (socket instanceof SSLSocket);
                    configureAcceptedSocket(socket);
                
                    input = decorateSocketInputStream(socket.getInputStream());
                    output = decorateSocketOutputStream(socket.getOutputStream());
                }
                catch (Throwable t)
                { 
                    errorOnSocketAccept(port, isSecure, t);
                    return;
                }

                try
                {
                    handleSocketStreams(clientAddress, port, isSecure, input, output);
                }
                catch (SocketTimeoutException e) 
                {
                    connectionTimeout(clientAddress, port, isSecure, e);
                }
                catch (Throwable e)
                {
                    connectionError(clientAddress, port, isSecure, e);
                }
            }
            finally
            {
                close();
            }
        }
    }

    public void listenOn(int port) throws IOException
    {
        listenOn(port, false);
    }

    public void listenOn(int port, boolean isSecure) throws IOException
    {
        listenOn(port, isSecure, null);
    }

    public void listenOn(int port, InetAddress bindAddress) throws IOException
    {
        listenOn(port, false, bindAddress);
    }

    public void listenOn(int port, boolean isSecure, InetAddress bindAddress) throws IOException
    {
        try
        {
            synchronized (acceptorMap)
            {
                if (closed)
                    throw new IOException("Server Closed");
                ServerSocket ssocket = null;
                if (isSecure)
                {
                    ServerSocketFactory ssocketFactory = SSLServerSocketFactory.getDefault();
                    ssocket = ssocketFactory.createServerSocket();
                }
                else
                    ssocket = new ServerSocket();

                ssocket.setReuseAddress(true);
                ssocket.setSoTimeout(500);
                ssocket.setReceiveBufferSize(receiveBufferSize);
                ssocket.setPerformancePreferences(1, 1, 0);
                ssocket.bind(new InetSocketAddress(bindAddress, port), SOCKET_BACKLOG);
        
                SocketAcceptor sa = new SocketAcceptor(port, isSecure, ssocket);
                acceptorMap.put(Integer.valueOf(port), sa);
                new Thread(sa).start();
            }
        }
        catch (IOException e)
        {
            errorOnListen(port, isSecure, e);
            throw e;
        }
    }

    public void stopListeningOn(int port)
    {
        synchronized (acceptorMap)
        {
            try
            {
                SocketAcceptor sa = (SocketAcceptor) acceptorMap.get(Integer.valueOf(port));
                sa.close();
            }
            catch (Exception e) {}
        }
    }

    public InetSocketAddress[] getListeningAddresses()
    {
        synchronized (acceptorMap)
        {
            SocketAcceptor[] sas = new SocketAcceptor[acceptorMap.size()];
            acceptorMap.values().toArray(sas);
            
            InetSocketAddress[] result = new InetSocketAddress[sas.length];
            for (int i=0; i<sas.length; i++)
            {
                try
                {
                    result[i] = (InetSocketAddress) sas[i].getListeningAddress();
                }
                catch (Exception e) {}
            }

            return result;
        }
    }

    public void close()
    {
        close(0);
    }

    public void close(long msToWait)
    {
        try
        {
            closed = true;
            
            if (msToWait <= 0)
                executor.shutdownNow();
            else
            {
                executor.shutdown();
                executor.awaitTermination(msToWait, TimeUnit.MILLISECONDS);
            }
        }
        catch (Throwable e) {}

        synchronized (acceptorMap)
        {
            Iterator itt = acceptorMap.values().iterator();
            while (itt.hasNext())
            {
                try
                {
                    ((SocketAcceptor) itt.next()).close();
                }
                catch (Throwable e) {}
            }
        }
    }

    class SocketAcceptor implements Runnable
    {
        private int port;
        private boolean isSecure;
        private ServerSocket ssocket;
        private HashSet acceptedSockets;

        private volatile boolean closed;

        SocketAcceptor(int port, boolean isSecure, ServerSocket ssocket)
        {
            this.port = port;
            this.isSecure = isSecure;
            this.ssocket = ssocket;
            
            closed = false;
            acceptedSockets = new HashSet();
        }

        void socketHandlerClosed(SocketHandler handler)
        {
            synchronized (acceptedSockets)
            {
                acceptedSockets.remove(handler);
            }
        }
        
        SocketAddress getListeningAddress()
        {
            return ssocket.getLocalSocketAddress();
        }
        
        void close()
        {
            SocketHandler[] openHandlers = null;
            synchronized (acceptedSockets)
            {
                closed = true;
                openHandlers = new SocketHandler[acceptedSockets.size()];
                acceptedSockets.toArray(openHandlers);
            }

            try
            {
                ssocket.close();
            }
            catch (Throwable e) {}

            for (int i=0; i<openHandlers.length; i++)
                openHandlers[i].close();
        }

        public void run()
        {
            try
            {
                Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            }
            catch (Exception e) {}

            int errorCounter = 0;
            while (!closed)
            {
                try
                {
                    Socket s = ssocket.accept();
                    SocketHandler sh = new SocketHandler(this, s, port, isSecure);

                    synchronized (acceptedSockets)
                    {
                        if (closed)
                        {
                            try
                            {
                                s.close();
                            }
                            catch (Throwable e) {}
                            return;
                        }

                        acceptedSockets.add(sh);
                        executor.execute(sh);
                    }

                    errorCounter = 0;
                }
                catch (SocketTimeoutException e) {}
                catch (Throwable e) 
                {
                    errorCounter = Math.min(errorCounter+1, Integer.MAX_VALUE);
                    try
                    {
                        errorOnSocketAccept(port, isSecure, e);
                    }
                    catch (Throwable tt) {}

                    if (errorCounter > 10000)
                    {
                        try
                        {
                            Thread.sleep(100);
                        }
                        catch (Throwable ee) {}
                    }
                }
            }
        }
    }
}
