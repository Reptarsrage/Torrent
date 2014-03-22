/**
 * Justin Robb
 * 1024715
 * 12/13/13
 * cse461 Final Project
 * Client Main
 */

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 
 * The main program for clients. This program communicates with the torrent
 * server to share files over a p2p network. See Usage for instructions
 * 
 * @author Justin Robb
 *
 */
public class Client {
	static InetAddress IPAddress;	 			// Address of server
	static int port = Constants.DEFAULT_PORT;	// Server Port (default is 12235)
	static ByteBuffer buf;						// Used for both sending and receiving
	static int id;								// id of this client (0 until given by server)					// 
	static int page_number = 0;					// current page of torrents being viewed
	static Socket tcpSocket;					// connection to server
	static int num_files_on_server = 0;			// number of  torrent files we know to be on server
	static boolean shutdown_normally = false;	// should we close without errors?
	static Timer timer;							// to keep connection alive when idling
	static Random r = new Random();				// Random port generator
	static ClientConnection sideListener;		// Used to receive peer connections

	private static Map<Integer, String> uploaded_files;		// map of file id -> path
	private static Map<Integer, Integer> retrieved_files;	// map of number in list -> file id
	private static Map<Integer, Long> retrieved_file_sizes;	// map of file id -> file size
	
	/**
	 * 
	 * Runs the client program.
	 * 
	 * @param args
	 * 			<server location> Location of Server.
	 * 			<port> (optional) Local port to use.
	 * 			-help view Usage.
	 */
	public static void main(String args[]) {
		uploaded_files = new HashMap<Integer, String>();
		uploaded_files = java.util.Collections.synchronizedMap(uploaded_files);
		sideListener = new ClientConnection(uploaded_files);
		try {
			int port_number = Constants.DEFAULT_PORT;
			if (args.length == 1 && !args[0].equals("-help"))
				IPAddress = InetAddress.getByName(args[0]);
			else if (args.length == 2) {
				IPAddress = InetAddress.getByName(args[0]);
				try {
					port_number = Integer.parseInt(args[1]);
				} catch (NumberFormatException e) {
					Usage();
				}
			} else 
				Usage();

			System.out.println("Contacting server...");
			tcpSocket  = new Socket(IPAddress, port_number);
			tcpSocket.setSoTimeout(10000);
			OutputStream out = new BufferedOutputStream(tcpSocket.getOutputStream());
			InputStream in = new BufferedInputStream(tcpSocket.getInputStream());

			buf = Utility.addHeader(Constants.OPEN_CONNECTION, Constants.GREETING.length(), 0);
			byte[] greetng_bytes = Constants.GREETING.getBytes();
			for (int i =0; i < Constants.GREETING.length(); i++)
				buf.put(Constants.HEADER_LEN + i, greetng_bytes[i]);;
			out.write(buf.array());
			out.flush();
			System.out.print("sending...");
			
			// received response from server, process it
			if ((buf = Utility.readIn(in, Constants.HEADER_LEN + 8)) == null) {
				out.close();
				in.close();
				tcpSocket.close();
				throw new IOException("read failed.");
			}
			
			System.out.print("done\n");
			// check for correctness
			if (!Utility.checkHeader(buf, Constants.OPEN_CONNECTION, 8, 0)) {
				out.close();
				in.close();
				tcpSocket.close();
				throw new RuntimeException("Malformed header from Server detected!");
			}
			
			//parse
			id = buf.getInt(Constants.HEADER_LEN);
			int tcp_port = buf.getInt(Constants.HEADER_LEN + 4);
			out.close();
			in.close();
			tcpSocket.close();
			
			// connect to server on tcp
			System.out.print("Establishing connection with server...");
			tcpSocket = new Socket(IPAddress, tcp_port);
			System.out.print("done\n");
			sideListener.setupConnection(IPAddress, tcp_port);
			System.out.println("Connected to server with id: " + id);
			sideListener.setId(id);
			sideListener.start();
			out = new BufferedOutputStream(tcpSocket.getOutputStream());
			in = new BufferedInputStream(tcpSocket.getInputStream());
			BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
			// keep connection open with small ack every 5 seconds
			Acker ack;
			String userInput;
			System.out.println("Commands:" +
        			"\n\tretrieve\tRetrieves list of available files from the server." +
        			"\n\tnext\tRetrieves the next page of files from the server." +
        			"\n\tprev\tRetrieves the previous page of files from the server." +
        			"\n\tupload <file>\tUploads the specified file to the server." +
        			"\n\tdownload <#>\tDownloads the corrosponding file.");
            while (sideListener.isAlive()) {
            	timer = new Timer();
            	ack = new Acker(out, tcpSocket, id);
            	timer.scheduleAtFixedRate(ack, 0, Constants.ACK_TIMEOUT / 2);
    			userInput = stdIn.readLine().trim();
            	timer.cancel();
            	if (userInput.equals("exit")){
            		shutdown_normally = true;
            		break;
            	}
            	else if (userInput.equals("retrieve")){
                	//request file listings
                	retrieveFiles(out, in, 1);
        			page_number = 1;
        			continue;
                } else if (userInput.startsWith("next")) {         		
                	if (num_files_on_server > page_number * Constants.PAGE_SIZE) {
                		page_number++;
                		retrieveFiles(out, in, page_number);
                	} else
                		System.out.println("There is no next page.");
            		continue;
            	} else if (userInput.startsWith("prev")) { 	
            		if (page_number < 2){
                		System.out.println("There is no previous page!");
	            		if (page_number == 0){
	            			page_number++;
	            			retrieveFiles(out, in, page_number);
	                		continue;
	            		}
            		}else {
                		page_number--;
                		retrieveFiles(out, in, page_number);
                		continue;
                	}
            	} else if (userInput.startsWith("upload")){
                	if (userInput.length() < 7){
                		System.out.println("Please specify a file to upload.");
                		continue;
                	}
            		
            		String filepath = System.getProperty("user.dir") + "/"+userInput.substring(7);
                	if (uploaded_files != null && uploaded_files.containsValue(filepath))
                		System.out.println("File "+userInput.substring(7)+" already uploaded!");
                	else if (userInput.length() > 6) {
                		uploadFile(filepath, out, in);
                		continue;
                	}
                } else if (userInput.startsWith("download")){
                	if (retrieved_files == null){
                		System.out.println("There are no files currently available to download.");
                		continue;
                	}
                	try {
	                	String[] splitted = userInput.split(" ");
	                	if (splitted.length != 2)
	                		throw new NumberFormatException();
                		int number = Integer.parseInt(userInput.split(" ")[1]);
	                	Integer file_id = retrieved_files.get(number);
	                	if (uploaded_files != null && uploaded_files.containsKey(file_id))
	                		System.out.println("File already downloaded.");
	                	else if (file_id != null) {
	                		downloadFile(file_id, out, in, retrieved_file_sizes.get(file_id));
	                	} else
	                		System.out.println("File number " + number + " not recognized.");
	                	continue;
                	} catch (NumberFormatException e){
                		System.out.println("Please specify the number next to " +
                				"the coorosponding file you wish to download.");
                		continue;
                	}
                }
            	System.out.println("Commands:" +
            			"\n\tretrieve\tRetrieves list of available files from the server." +
            			"\n\tnext\tRetrieves the next page of files from the server." +
            			"\n\tprev\tRetrieves the previous page of files from the server." +
            			"\n\tupload <file>\tUploads the specified file to the server." +
            			"\n\tdownload <#>\tDownloads the corrosponding file.");
            }
			
			
			System.out.println("Closing Connection.");
			buf = Utility.addHeader(Constants.CLOSE_CONNECTION, 0, id);
			out.write(buf.array());
			out.flush();
			sideListener.interrupt();
			in.close();
			out.close();
		} catch (UnknownHostException e) {
			// IPAdress unknown
			if (!shutdown_normally){
				System.err.println("Don't know about host " + IPAddress);
				System.exit(1);
			}
		} catch (IOException e) {
			// Error in communication
			if (!shutdown_normally){
				System.err.println("Couldn't get I/O for the connection to " +
						IPAddress);
				System.exit(1);
			}
		} catch (RuntimeException e){
			// Malformed Header most likely
			if (!shutdown_normally){
				System.err.println("ERROR: " + e.getMessage());
				System.exit(1);
			}
		} finally {
			if (sideListener.isAlive())
				sideListener.interrupt();
			try {
				if (!tcpSocket.isClosed())
					tcpSocket.close();
			} catch (IOException e) {
				return;
			}
		}
		
	}
	
	/**
	 * 
	 * Uploads the file with the given path to the server. This client is added as
	 * a peer for this file automatically.
	 * 
	 * @param filepath The path to the file to be uploaded.
	 * @param out The stream to write to for communicating with the server.
	 * @param in The stream to read from for communicating with the server.
	 * @throws IOException
	 */
	private static void uploadFile(String filepath, OutputStream out, 
			InputStream in) throws IOException{
		// (for uploading request c->s) [header | filesize(8) | filename(?)]
		// (for upload acknowledgement s->c) [header | already exists (1) | file id (4)]	
		File upload = new File(filepath);

		if (!upload.exists()){
			 System.out.println("Cannot locate file: " + filepath);
			return;
		}
		System.out.println("Contacting Server...");
		String name = upload.getName() + '\0';
		buf = Utility.addHeader(Constants.UPLOAD_REQ, name.length() + 12, id);
		buf.putLong(Constants.HEADER_LEN, upload.length());
		byte[] name_bytes = name.getBytes();
		for (int i = 0; i < name.length(); i++){
			buf.put(Constants.HEADER_LEN + 8 + i, name_bytes[i]);
		}
		out.write(buf.array());
		out.flush();
		
		// Get Response
		if ((buf=Utility.readIn(in, Constants.HEADER_LEN + Utility.getPaddedLength(5))) == null)
			throw new IOException("read failed.");
		byte exists = buf.get(Constants.HEADER_LEN);
		int file_id = buf.getInt(Constants.HEADER_LEN + 1);
		if (exists == 0){
			System.out.println(name + " Successfuly uploaded.");
		} else {
			System.out.println(name + " added as a peer to an already " +
					"existing file on the server with the same name/size.");
		}
		uploaded_files.put(file_id, filepath);
	}
	
	/**
	 * 
	 * Contacts the server to set up peer connections. Downloads the file with id = file_id
	 * from peers. Saves the file in local directory. This client is added as
	 * a peer for this file automatically (suck it leechers).
	 * 
	 * @param file_id The id for the file to request peers for.
	 * @param out The stream to write to for communicating with the server.
	 * @param in The stream to read from for communicating with the server.
	 * @param file_size The size of the file to be downloaded.
	 * @throws IOException
	 * @throws RuntimeException
	 */
	private static void downloadFile(int file_id, OutputStream out, 
			InputStream in, long file_size) throws IOException, RuntimeException{
		// (req to download c->s) [header | file id]
		// (download response s->c) [header | Port | peer id]xNum clients
		// [LAST](download response s->c) [header | 0 | 0]
		
		File result = new File(file_id + ".data");
		result.createNewFile();
		
		// get peers
		List<ClientReceiver> peers = getPeers(file_id, out, in, result.getAbsolutePath()); 
		if (peers.size() == 0){
			System.out.println("Couldn't connect to peers!");
			result.delete();
			return;
		}
		
		// start all peer connections to read/writing
		List<int[]> unwritten_chunks = new ArrayList<int[]>();
		int chunk_size = (int)file_size / peers.size();
		if ((int)file_size % peers.size() != 0)
			// round to ceiling
			chunk_size++;
		int count = 0;
		for (ClientReceiver peer : peers){
			peer.setInterval(chunk_size*count, chunk_size*(count + 1));
			int[] interval = {chunk_size*count, chunk_size*(count + 1)};
			unwritten_chunks.add(interval);
			peer.start();
			count++;
		}
		
		int tries = 0;
		// handle issues
		while (tries < 10 && unwritten_chunks.size() != 0){
			tries++;
			while (peers.size() != 0){
				for (ClientReceiver peer : peers){
					if (!peer.isAlive()) {
						int[] interval = {peer.start_index, peer.stop_index};
						for (int i=0; i < unwritten_chunks.size(); i++){
							if (unwritten_chunks.get(i)[1] == interval[1] && 
									unwritten_chunks.get(i)[0] == interval[0]){
								unwritten_chunks.remove(i);
							}
						}
						if (peer.bytes_written == chunk_size){
							// peer did everything required of it
							peers.remove(peers.indexOf(peer));
							if (unwritten_chunks.size() == 0){
								completeDownload(out, in, file_id, result);
								return;
							}
							break;
						} else {
							// peer failed for some reason
							int[] gap = {peer.start_index + peer.bytes_written, peer.stop_index};
							unwritten_chunks.add(gap);
							peers.remove(peers.indexOf(peer));
							break;
						}
					}
				}
			}
			
			// may have gaps
			if (unwritten_chunks.size() > 0){
				peers = getPeers(file_id, out, in, result.getAbsolutePath());
				if (peers.size() == 0){
					System.out.println("Aborting download.");
					result.delete();
					return;
				}
				int gap_count = 0;
				for (ClientReceiver peer : peers){
					int[] interval = unwritten_chunks.get(gap_count);
					peer.setInterval(interval[0], interval[1]);
					peer.start();
					if (gap_count < unwritten_chunks.size() - 1)
						gap_count++;
				}
			}
		}
		// close all threads
		for (ClientReceiver cr : peers)
			if (cr.isAlive())
				cr.interrupt();
		
		// report result
		if (tries == 10){
			System.out.println("Aborting download.");
			result.delete();
		}
		else
			completeDownload(out, in, file_id, result);
	}
	
	private static void completeDownload(OutputStream out, InputStream in, int file_id, File result) throws IOException{
		// (for download completion ack c->s) [header | file_id (4)]
		
		// all peers done
		timer.cancel(); // avoid comm issues
		System.out.println("Download finishing... ");
		
		//construct packet
		buf = Utility.addHeader(Constants.DOWNLOAD_ACK, 4, id);
		buf.putInt(Constants.HEADER_LEN, file_id);
		out.write(buf.array());
		out.flush();
		
		//get response (header first)
		if ((buf = Utility.readIn(in, Constants.HEADER_LEN)) == null){
			result.delete();
			throw new IOException("read failed.");
		}
		if (buf.getInt(0) != Constants.DOWNLOAD_ACK || buf.getInt(8) != id){
			result.delete();
			throw new RuntimeException("Received malformed header from server.");
		}
		int payload_len = buf.getInt(4);
		if ((buf = Utility.readIn(in, Utility.getPaddedLength(payload_len))) == null){
			result.delete();
			throw new IOException("read failed.");
		}
		File name = new File(getNameFromBuf(0, buf));
		result.renameTo(name);
		// add to uploads (sorry no leeching)
		uploaded_files.put(file_id, name.getAbsolutePath());
		System.out.println("Download complete for " + name.getAbsolutePath());
	}
	
	/**
	 * 
	 * Contacts the server to receive a list of available peers for the given file.
	 * 
	 * @param file_id The id for the file to request peers for.
	 * @param out The stream to write to for communicating with the server.
	 * @param in The stream to read from for communicating with the server.
	 * @param path The path to the file with id = file_id.
	 * @return A list of available peers for file with id = file_id and file path = path.
	 * @throws IOException
	 * @throws RuntimeException
	 */
	private static List<ClientReceiver> getPeers(int file_id, 
		OutputStream out, InputStream in, String path) throws IOException, RuntimeException{
		// (req to download c->s) [header | file id]
		// (download response s->c) [header | IP | Port]xNum clients
		
		timer.cancel(); // comm issues
		
		// construct/send request
		System.out.println("Contacting Server...");
		buf = Utility.addHeader(Constants.DOWNLOAD_REQ, 4, id);
		buf.putInt(Constants.HEADER_LEN, file_id);
		out.write(buf.array());
		out.flush();
		
		// make sure to keep acking (this download may take some time)
    	timer = new Timer();
    	Acker ack = new Acker(out, tcpSocket, id);
    	timer.scheduleAtFixedRate(ack, 0, Constants.ACK_TIMEOUT / 2);
		
		// open connection with each peer
		List<ClientReceiver> peers = new ArrayList<ClientReceiver>();
		
		while (true) {
			//(download response s->c) [header | Port | peer id]xNum clients

			if ((buf = Utility.readIn(in, Constants.HEADER_LEN)) == null)
				throw new IOException("read failed.");
			if (buf.getInt(0) != Constants.DOWNLOAD_REQ || buf.getInt(8) != id)
				throw new RuntimeException("Malformed header!");
			int payload_len = buf.getInt(4);
			
			// read in payload
			if ((buf = Utility.readIn(in, Utility.getPaddedLength(payload_len))) == null)
					throw new IOException();
			int port_num = buf.getInt(0);
			int peer_id = buf.getInt(4);
			if (port_num == 0 && peer_id == 0)
				// no more peers
				break;
			
			// process peer
			InetAddress addr = tcpSocket.getInetAddress();
			Socket peer;
			System.out.println("Connecting to " + addr + " on port " + port_num +"!");
			try {
				peer = new Socket(addr, port_num);
			} catch (IOException e){
				System.out.println("Connection failed.");
				// what to do if a peer fails?
				continue;
			}
			ClientReceiver c = new ClientReceiver(id, path, file_id, peer_id, peer);
			peers.add(c);
			System.out.println("Connected to " + addr + " on port " + port_num +"!");
		}
		
		if (peers.size() == 0)
			System.out.println("No available peers for this file.");
		
		return peers;
	}
	
	/**
	 * 
	 * Requests server to send a list of torrent files, then displays this list.
	 * 
	 * @param out The stream to write to for communicating with the server.
	 * @param in The stream to read from for communicating with the server.
	 * @param page The page in the list of torrent files wanted.
	 * @throws IOException
	 * @throws RuntimeException
	 */
	private static void retrieveFiles(OutputStream out, InputStream in, int page) throws IOException, RuntimeException{
		System.out.println("Contacting Server...");
		// (view files req c->s) [header | page (4)]
		// (initial response) [header | num files to expect | total num (4)]
		// (list of available clients s->c) [header | size(8) | num_peers(4) | id(4) | name(?)]xNum files
		
		buf = Utility.addHeader(Constants.VIEW_REQ, 4, id);
		buf.putInt(Constants.HEADER_LEN, page);
		out.write(buf.array());
		out.flush();
		
		// read response
		if ((buf=Utility.readIn(in, Constants.HEADER_LEN + 8)) == null)
			throw new IOException("read failed.");
		
		// header?
		if (!Utility.checkHeader(buf, Constants.VIEW_REQ, 8, id)){
			throw new RuntimeException("Malformed header from Server detected!");
		}
		int num_files = buf.getInt(Constants.HEADER_LEN);
		num_files_on_server = buf.getInt(Constants.HEADER_LEN + 4);
		System.out.println("Retrieved " + num_files +"/" +num_files_on_server+
				" files from server (page #"+page+" of " +(num_files_on_server / Constants.PAGE_SIZE + 1)+"):");
		
		//Receive files from server
		retrieved_files = new HashMap<Integer, Integer>();
		retrieved_file_sizes = new HashMap<Integer, Long>();
		int flag, payload_len, buf_id, num_peers;
		long file_size;
		String name;
		for (int i=0; i < num_files; i++){
			if ((buf=Utility.readIn(in, Constants.HEADER_LEN)) == null)
				throw new IOException("read failed.");
			flag = buf.getInt(0);
			payload_len = buf.getInt(4);
			buf_id = buf.getInt(8);
			// Check correctness of header
			if (id != buf_id || flag != Constants.VIEW_REQ)
				throw new RuntimeException("Malformed header from Server detected!");
			// read payload
			if ((buf = Utility.readIn(in, Utility.getPaddedLength(payload_len))) == null)
					throw new IOException("read failed.");
			file_size = buf.getLong(0);
			num_peers = buf.getInt(8);
			buf_id = buf.getInt(12);
			name = getNameFromBuf(16, buf);
			if (name.length() == 0)
				throw new RuntimeException(
						"Connection - FAILURE! (filename not correctly uploaded)");
			// Display files (one per line)
			System.out.println((i+1) + ". "+name +", ("+file_size+" bytes), " +num_peers+" peers.");
			retrieved_files.put(i+1, buf_id);
			retrieved_file_sizes.put(buf_id, file_size);
		}
		System.out.println("Done");
	}
	
	/**
	 * How to use this program message.
	 */
	private static void Usage(){
		System.out.println("USAGE:"+
				"\n\tjava Client <server location> [option]"+
				"\n\nExample: java Client 12235 attu1@cs.washington.edu" +
				"\n\nOptions:" +
				"\n\tport\tspecify a port to contact the server on. (Default is "+Constants.DEFAULT_PORT+")" +
				"\n\t-help\tview usage");
		System.exit(1);
	}
	
	/**
	 * 
	 * Reads the String at position index from the given buffer. All strings sent over
	 * network MUST end with a null terminator '\0'.
	 * 
	 * @param index Index in buffer representing the start of the string to be read.
	 * @param buf The buffer to read from.
	 * @return the String contained in buf at index.
	 */
	private static String getNameFromBuf(int index, ByteBuffer buf){
		String name = "";
		for (int j = 0; j < buf.capacity(); j++){
			if (buf.get(index +j) == '\0')
				break;
			name += (char)buf.get(index + j);
		}
		return name;
	}
}

/**
 * Keeps our connection alive with the server by sending an ack periodically.
 * 
 * @author Justin Robb
 *
 */
class Acker extends TimerTask{
	OutputStream out;	// out connection to server
	int id;				// id of this client
	
	/**
	 * Constructor for this timer.
	 * 
	 * @param out The stream to write to for communicating with the server.
	 * @param tcpSocket The socket to use for communication with server.
	 * @param id The id of the client sending the ack.
	 */
	public Acker(OutputStream out, Socket tcpSocket, int id){
		super();
		this.out = out;
		this.id = id;
	}
	
	/**
	 * Sends an ACK to the server.
	 */
	@Override
	public void run(){
		ByteBuffer buf = Utility.addHeader(Constants.ACK, 0, id);
		try {
			out.write(buf.array());
			out.flush();
		} catch (IOException e){
			this.cancel();
		}
	}
}