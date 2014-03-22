/**
 * Justin Robb
 * 1024715
 * 12/13/13
 * cse461 Final Project
 * Code for handling a connected client
 */

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Handles all the requests for one client.
 * 
 * @author Justin Robb
 *
 */
public class ServerProcessConnection extends Thread {
	static ByteBuffer buf;				// Used for both sending and recieveing
	int port;							// port number assigned to this thread(client)
	ClientObj client;					// the client served by this thread
	boolean shutdown_normally = false;	// should we close without errors?
	ServerSocket serverTcpSocket;		// our Socket used to communicate with the client
	Socket clientSocket;				// client's socket for communication 
	OutputStream out;					// stream for sending data to client
	InputStream in; 					// stream for reading data from client
	List<ClientObj> clients;			// shared list of all clients (thread safe)
	List<ServerFwding> peer_threads;	// threads that are being managed by this
	
	/**
	 * Constructs a thread which will handle one client connection to the server.
	 * 
	 * @param socket Socket used to communicate with the client.
	 * @param clients Synchronized list of connected clients.
	 * @param client The client to be handled by this thread.
	 * @throws IOException
	 */
	public ServerProcessConnection(ServerSocket socket, 
			List<ClientObj> clients, ClientObj client) {
		this.client = client;
		this.clients = clients;
	    serverTcpSocket = socket;
	    peer_threads = new ArrayList<ServerFwding>();
	}
	
	/**
	 * A thread for processing the client. Thread will terminate if interrupted, or
	 * communication protocols are broken.
	 */
	@Override
	public void run(){
		InetAddress IPAddress = serverTcpSocket.getInetAddress();
		for (ClientObj c: clients)
			if (c.getId() == client.getId()){
				System.out.print(IPAddress + " Client already connected!");
				return;
			}
		try{
			// Check handshake received for correctness
			//(connect c->s) [herader | "JUSTIN\0" | connection port] (udp)
			//(connect response s->c) [header | client id | port] (udp)
	     
	        // open the connection
		    clientSocket = null;
		    Socket clientListener = null;
		    // wait for client connection on tcp port
		    //serverTcpSocket.setSoTimeout(5000);
	    	System.out.println (" Waiting for tcp connection.....");
	    	clientSocket = serverTcpSocket.accept();
	    	clientListener = serverTcpSocket.accept();
	    	client.setListenerSocket(clientListener);
			IPAddress = clientSocket.getInetAddress();
			int recv_port = clientSocket.getPort();
			System.out.println(IPAddress + ": Client connected @ port " + recv_port);

		    // handle tcp connection
			clientSocket.setSoTimeout(Constants.ACK_TIMEOUT);
		    out = new BufferedOutputStream(clientSocket.getOutputStream());
			in = new BufferedInputStream(clientSocket.getInputStream());
			client.setAddress(clientSocket.getInetAddress());
			client.setPort(clientSocket.getPort());
		    clients.add(client);
			
		    // handle requests
			while (!shutdown_normally) {
				// read just the header, then handle the req based on the info in the header
				if ((buf = Utility.readIn(in, Constants.HEADER_LEN)) == null)
					break;
				int flag = buf.getInt(0);
				int len2 = buf.getInt(4);
				int id = buf.getInt(8);
				
				// check for correctness of header
				
				if (flag < Constants.OPEN_CONNECTION || 
						flag > Constants.NUM_FLAGS || 
						id != client.getId() || 
						len2 < 0){
					out.close(); 
					in.close(); 
					clientSocket.close(); 
					serverTcpSocket.close();
					clients.remove(client);
					throw new RuntimeException(
							"Connection - FAILURE! (malformed header)");
				}
				
				// read payload
				if ((buf = Utility.readIn(in, Utility.getPaddedLength(len2))) == null)
					throw new IOException("read failed.");
				// update address (necessary?)
				clients.get(clients.indexOf(client)).setAddress(clientSocket.getInetAddress());
				client.setAddress(clientSocket.getInetAddress());
				switch (flag){
					case Constants.ACK:
						break;
					case Constants.VIEW_REQ:
						System.out.println(client.getAddress() + 
								": Processing view request...");
						ViewFiles(buf);
						break;
					case Constants.DOWNLOAD_REQ:
						System.out.println(client.getAddress() + 
								": Processing download request...");
						Download(buf);
						break;
					case Constants.UPLOAD_REQ:
						System.out.println(client.getAddress() + 
								": Processing upload request...");
						Upload(buf);
						break;
					case Constants.DOWNLOAD_ACK:
						System.out.println(client.getAddress() + 
								": Processing download acknowledgment...");
						DownloadCompleted(buf);
						break;
					case Constants.CLOSE_CONNECTION:
						shutdown_normally = true;
					default:
						break;
				}
			}
			// close all open sockets
		    out.close(); 
		    in.close(); 
		    clientSocket.close(); 
		    serverTcpSocket.close();
		} catch (SocketTimeoutException e) {
			System.err.println(IPAddress + ": Timeout waiting for response.");
		} catch (UnknownHostException e) {
			// IPAdress unknown
			System.err.println("Don't know about host " + IPAddress);
		} catch (IOException e) {
			// Error in communication
			System.err.println("Couldn't get I/O for the connection to " +
					IPAddress);
		} catch (RuntimeException e){
			// Malformed Header or payload most likely
			System.err.println(IPAddress + ": Connection - FAILURE! (" + e.getMessage() + ")");
		} finally {
			// remove this client from active lists, close all (possibly) open sockets
			System.out.println(IPAddress + ": Connection - Closing!");
			clients.remove(client);
			try {
				Socket clientConnection = client.getListenerSocket();
				OutputStream out_c = new BufferedOutputStream(clientConnection.getOutputStream());
				buf = Utility.addHeader(Constants.CLOSE_CONNECTION, 0, client.getId());
				out_c.write(buf.array());
				out_c.flush();
				out_c.close();
				clientConnection.close();

				if (out != null)
					out.close();
				if (in != null)
					in.close();
				if (!clientSocket.isClosed())
					clientSocket.close();
				if (!serverTcpSocket.isClosed())
					serverTcpSocket.close();
			} catch (IOException e){
				return;
			}
		}
   }
	
	/**
	 * Handles a download complete acknowledgment from the client.
	 * 
	 * @param packet The payload of the acknowledgment.
	 * @throws RuntimeException
	 * @throws IOException
	 */
	private void DownloadCompleted(ByteBuffer packet) throws RuntimeException, IOException{
		// (for download completion ack c->s) [header | file_id (4)]
		// no HEADER
		
		// parse the req
		if (packet.capacity() < 4)
			throw new RuntimeException(
					"file not correctly specified");
		int file_id = packet.getInt(0);
		for (ServerFile f : totalFiles().keySet()){
			if (f.id() == file_id){
				client.addUpload(f);
				// construct response
				String send_name = f.name() + '\0';
				buf = Utility.addHeader(Constants.DOWNLOAD_ACK, send_name.length(), client.getId());
				
				for (int i = 0; i < send_name.length(); i++){
					buf.put(Constants.HEADER_LEN + i, send_name.getBytes()[i]);
				}
				out.write(buf.array());
				out.flush();
				System.out.println(client.getAddress() + 
						": Correctly processed download ack for " + file_id);
				return;
			}
		}
		throw new RuntimeException("file id does not exist");
	}
	
	/**
	 * Handles a request from the client to view the available torrent files on the server.
	 * 
	 * @param packet The payload for a view file request from the client.
	 * @throws IOException
	 */
	private void ViewFiles(ByteBuffer packet) throws IOException{
		// (view files req c->s) [header | page (4)]
		// (initial response) [header | num files to expect | total num (4)]
		// (list of available clients s->c) [header | size(8) | num_peers(4) | id(4) | name(?)]xNum files
		Map<ServerFile, Integer> files = totalFiles();
		
		//read payload / construct response
		if (packet.capacity() < 4)
			throw new RuntimeException(
					"page not correctly specified");
		
		int page = packet.getInt(0);
		
		int num_to_send = files.size();
		if (files.size() > (Constants.PAGE_SIZE * page))
			num_to_send = Constants.PAGE_SIZE;
		else if (files.size() > (Constants.PAGE_SIZE * (page - 1)))
			num_to_send = files.size() % Constants.PAGE_SIZE;
		else {
			num_to_send = files.size() % Constants.PAGE_SIZE;
			page = (files.size() / Constants.PAGE_SIZE) + 1;
		}
			
		buf = Utility.addHeader(1, 8, client.getId());
		buf.putInt(Constants.HEADER_LEN, num_to_send);
		buf.putInt(Constants.HEADER_LEN + 4, files.size());
		byte[] sendData = buf.array();
		
		// send response
		out.write(sendData);
		out.flush();
		
		//construct/send file infos
		Iterator<ServerFile> it = files.keySet().iterator();
		for (int count = 0; count < ((page - 1) * Constants.PAGE_SIZE); count++)
			it.next();
		for (int count = 0; count < num_to_send; count++) {
			ServerFile f = it.next();
			String sent_name = f.name() + '\0';
			buf = Utility.addHeader(1, sent_name.length() + 16, client.getId());
			buf.putLong(Constants.HEADER_LEN, f.size());
			buf.putInt(Constants.HEADER_LEN + 8, files.get(f)); 
			buf.putInt(Constants.HEADER_LEN + 12, f.id());
			byte[] sent_name_bytes = sent_name.getBytes();
			for (int i = 0; i < sent_name.length(); i++)
				buf.put(Constants.HEADER_LEN + 16 + i, sent_name_bytes[i]);
			sendData = buf.array();
			out.write(sendData);
			out.flush();
		}		
		System.out.println(client.getAddress() + ": Correctly processed file req");
	}
	
	/**
	 * Handles an upload request from client.
	 * 
	 * @param packet The payload for an upload request from the client.
	 * @throws RuntimeException
	 * @throws IOException
	 */
	private void Upload(ByteBuffer packet) throws RuntimeException, IOException{
		// given packet w/no header!
		// (for uploading request c->s) [header | filesize(8) | filename(?)]
		// (for upload acknowledgement s->c) [header | already exists (1) | file id (4)]
		
		// parse the req
		if (packet.capacity() < 4)
			throw new RuntimeException(
					"filename not correctly uploaded");
		long buf_size = packet.getLong(0);
		String buf_name = "";
		for (int i = 0; i < buf.capacity(); i++){
			if (buf.get(8 +i) == '\0')
				break;
			buf_name += (char)buf.get(8 + i);
		}
		if (buf_name.length() == 0)
			throw new RuntimeException(
					"filename not correctly uploaded");
		
		int id = buf_name.hashCode() + (int)buf_size;
		boolean add = true;
		for (ClientObj c : clients)
			if (c.hasFile(id)) // Check for duplicate id's, which would be problematic
				add = false;
		byte exists =  0;
		if (add)
			client.addUpload(new ServerFile(buf_name, buf_size, id));
		else {
			for (ServerFile f : totalFiles().keySet())
				if (f.id() == id)
					client.addUpload(f);
			exists = 1;
		}
		System.out.println(client.getAddress() + ": Correctly processed upload req for " + buf_name 
				+ " with size " + buf_size + " bytes and id " + id);
		
		//construct/send response
		buf = Utility.addHeader(3, 5, client.getId());
		buf.put(Constants.HEADER_LEN, exists);
		buf.putInt(Constants.HEADER_LEN + 1, id);
		byte[] sendData = buf.array();
		
		// send response
		out.write(sendData);
		out.flush();
	}
	
	/**
	 * 
	 * Handles a download request from a client.
	 * 
	 * @param packet The payload for a download request from the client.
	 * @throws IOException
	 */
	private void Download(ByteBuffer packet) throws IOException {
		// NO HEADER
		
		// allow only 1 download at a time, clear old threads
		for (ServerFwding f : peer_threads){
			if (f.isAlive())
				f.interrupt();
		}
		peer_threads.clear();
		
		//parse the req
		if (packet.capacity() < 4)
			throw new RuntimeException(
					"download request invalid");
		
		int buf_id = packet.getInt(0);
		
		Map<ClientObj, Integer> clients_with_file = new HashMap<ClientObj, Integer>();
		for (ClientObj c : clients){
			if (c.hasFile(buf_id) && c.getId() != client.getId())
				clients_with_file.put(c, 0);
		}
		
		// ask each client to prepare a port
		for (ClientObj c : clients_with_file.keySet()) {
			//open connection with clients listener
			Socket peerConnection = c.getListenerSocket();
			OutputStream out_c = new BufferedOutputStream(peerConnection.getOutputStream());
			
			//construct packet
			//(for connect to a peer conn s->c) [header | file_id (4) | peer id (4) | port num]
			int peer_server_port = Utility.getValidPort();
			buf = Utility.addHeader(Constants.PREPARE, 12, c.getId());
			buf.putInt(Constants.HEADER_LEN, buf_id);
			buf.putInt(Constants.HEADER_LEN + 4, client.getId());
			buf.putInt(Constants.HEADER_LEN + 8, peer_server_port);
			byte[] sendData = buf.array();
			
			// send response
			out_c.write(sendData);
			out_c.flush();
			System.out.println(client.getAddress() + 
					": sent peer client data (with server port " +peer_server_port+").");
			
			try {
				int client_server_port = Utility.getValidPort();
				ServerFwding peer_thread = new ServerFwding(
						client_server_port, c.getId(), peer_server_port, buf_id);
				peer_thread.start();
				
				// send peer info to client, await connection on thread
				buf = Utility.addHeader(Constants.DOWNLOAD_REQ, 8, client.getId());
				buf.putInt(Constants.HEADER_LEN, client_server_port);
				buf.putInt(Constants.HEADER_LEN + 4, c.getId());
				sendData = buf.array();
				out.write(sendData);
				out.flush();
				System.out.println(client.getAddress() + 
						": sent client peer data (with server port " +client_server_port+").");
				peer_threads.add(peer_thread);
			} catch (IOException e){
				// couldn't connect to peer, skip it
				System.err.println(client.getAddress()+ 
						": Failed to set up P2p connection " + e.getMessage());
			} catch (RuntimeException e){
				// couldn't connect to peer, skip it
				System.err.println(client.getAddress()+ 
						": Failed to set uo P2p connection " + e.getMessage());
			}
		}
		
		// send last to client
		buf = Utility.addHeader(Constants.DOWNLOAD_REQ, 8, client.getId());
		buf.putInt(Constants.HEADER_LEN, 0);
		buf.putInt(Constants.HEADER_LEN + 4, 0);
		out.write(buf.array());
		out.flush();
		
		System.out.println(client.getAddress() + 
				": Correctly processed download req for " + buf_id);
	}
	
	/**
	 * Should be called when the server is shut down by the client.
	 * This closes the socket and exits, bypassing the infinite loop 
	 * of listening for requests. Ends all managed threads.
	 */
	@Override
	public void interrupt() {
		shutdown_normally = true;
		try {
			for (ServerFwding f : peer_threads){
				if (f.isAlive())
					f.interrupt();
			}
			
			if (clients.contains(client)){
				Socket clientConnection = client.getListenerSocket();
				OutputStream out_c = new BufferedOutputStream(clientConnection.getOutputStream());
				buf = Utility.addHeader(Constants.CLOSE_CONNECTION, 0, client.getId());
				out_c.write(buf.array());
				out_c.flush();
				out_c.close();
				clientConnection.close();
				clients.remove(client);
			}
			clientSocket.close();
			serverTcpSocket.close();
		} catch (IOException e) {
			// Error in communication
			System.err.println(client.getAddress() + ": Experienced trouble closing. (Warning)");
		}
	}
	/**
	 * Calculates a list of all files uploaded to server by all connected clients.
	 * 
	 * @return The list of uploaded files.
	 */
	private Map<ServerFile, Integer> totalFiles(){
		Map<ServerFile, Integer> ret = new HashMap<ServerFile, Integer>();
		for (ClientObj c : clients){
			for (ServerFile f : c.getFiles()){
				if (!ret.containsKey(f))
					ret.put(f, 1);
				else
					ret.put(f, ret.get(f) + 1);
			}
		}
		return ret;
	}
}
