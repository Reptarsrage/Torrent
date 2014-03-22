/**
 * Justin Robb
 * 12/13/13
 * Server incoming connection listener
 */

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles the job of listening for client connections a given port,
 * and spawning a thread to handle each connection.
 * 
 * @author Justin Robb
 *
 */
public class ServerListener extends Thread{
	int port_num; 								// Port to listen on
	ServerSocket serverSocket; 					// socket to listen on
	private boolean shutdown_normally = false;  // Should we show error messages? (false = yes)
	List<Thread> threadpool;					// threads being managed by this thread
	public static List<ClientObj> clients;		// list (thread safe) of clients who're
												// currently connected to the server.
	/**
	 * Constructs a server listening for incoming TCP 
	 * connections on a given port.
	 * 
	 * @param port_num The port to listen on.
	 */
	public ServerListener(int port_num){
		this.port_num = port_num;
		clients = new ArrayList<ClientObj>();
		clients = java.util.Collections.synchronizedList(clients);
		threadpool = new ArrayList<Thread>();
	}
	
	/**
	 * Should be called when the server is shut down by the client.
	 * This closes the socket and exits, bypassing the infinite loop 
	 * of listening for clients.
	 */
	@Override
	public void interrupt() {
		shutdown_normally = true;
		System.out.println("Server shutting down...");
		for (Thread t : threadpool)
			t.interrupt();
		System.out.println("Waiting for current connections to finish...");
		try {
			serverSocket.close();
		} catch (IOException e) {
			return;
		}	
	}
	
	/**
	 * Waits for a connection on the specified port, and
	 * spawns threads to handle each connection.
	 */
	@Override
	public void run() {
	    try {
		    serverSocket = new ServerSocket(port_num);  
		    System.out.println("Server - Initialized server. Waiting for clients on port " + port_num);
			System.out.println("Server - Listening for connections...");
			while(!shutdown_normally){  
				// wait for a connection then start a new thread to handle it
				byte[] receiveData = new byte[Utility.getPaddedLength(Constants.HEADER_LEN+Constants.GREETING.length())];
				Socket clientSocket = serverSocket.accept();
				System.out.print(" Recieved a req for a connection, processing...");
				OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream());
				InputStream in = new BufferedInputStream(clientSocket.getInputStream());
				ByteBuffer buf = Utility.readIn(in, receiveData.length);
				if (buf == null)
					// read failed, forget connection
					continue;
				
				// check correctness of handshake
				if (!Utility.checkHeader(buf, Constants.OPEN_CONNECTION, Constants.GREETING.length(), 0))
		        	throw new RuntimeException("Connection - FAILURE! (Malformed header handshake, header incorrect)");
		        byte[] greeting_bytes = Constants.GREETING.getBytes();
		        for (int i =0; i < Constants.GREETING.length(); i++)
		        	if (buf.get(Constants.HEADER_LEN + i) != greeting_bytes[i])
		        		throw new RuntimeException("Connection - FAILURE! (Malformed payload in handshake, wrong greeting)");        
		        
		        // construct response for handshake
		        // must look like (connect response s->c) [header | client id | tcp port]
		        int client_id = clientSocket.getInetAddress().hashCode() * clientSocket.getPort();
		        ClientObj client = new ClientObj(null, 0, client_id);
		        
		        // send response
		        buf = Utility.addHeader(Constants.OPEN_CONNECTION, 8, 0);
		        buf.putInt(Constants.HEADER_LEN, client_id);
			        int thread_port = Utility.getValidPort();
			        try{
			        	// spawn thread to handle connection
			        	ServerSocket thread_socket = new ServerSocket(thread_port);
			        	buf.putInt(Constants.HEADER_LEN + 4, thread_port);
				        // send 
				        out.write(buf.array());
				        out.flush();
				        // close
				        in.close();
				        out.close();
				        clientSocket.close();
			        	Thread clientProcess = new Thread(new ServerProcessConnection(thread_socket, clients, client));
			        	threadpool.add(clientProcess);
			        	clientProcess.start();
			        	System.out.print("done.\n");
			        } catch (IOException e) {
			        	System.out.println("Connection - FAILURE! Issue connecting to client, aborting!");
			        }
			}
	    } catch (Exception e){
			if (!shutdown_normally){
				System.err.println("Server - Failure! (" + e.getMessage() + ")");
			}
			System.exit(1);
		} finally {
			try {
			if (serverSocket != null && !serverSocket.isClosed())
				 serverSocket.close();
			} catch (IOException e){
				System.exit(0);
			}
		}
	}	
}
