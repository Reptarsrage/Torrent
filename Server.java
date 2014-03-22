/**
 * Justin Robb
 * 12/13/13
 * Server Main
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Main program for running a torrent server. Clients will connect to this server
 * to share files over a p2p network.
 * 
 * @author Justin Robb
 *
 */
public class Server {
	static int port = Constants.DEFAULT_PORT; 		// Port to listen on
	
	/**
	 * The main portion of this file just sets up a listener to listen for incoming connections
	 * on a seperate thread, while this thread checks if the server should shut down 
	 * (by user input). View Usage for instructions. 
	 * NOTE: if the given port is unusable, the default is used.
	 * 
	 * @param args 
	 * 			<port> (optional) The local port to run the server on.
	 * 			-help view Usage.
	 */
	public static void main(String args[]) {
		if (args.length > 0) {
			if (args.length > 1 || args[0].equals("-help"))
				Usage();
			else
				try {
					port = Integer.parseInt(args[0]);
					if (port < 1)
						port = Constants.DEFAULT_PORT;
				} catch (NumberFormatException e) {
					Usage();
				}
		}
		BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
		ServerListener listen = new ServerListener(port);
		System.out.println("Server - Type \"exit\" to shut down server at any time.");
		listen.start();
		String userInput;
		try {
			while (listen.isAlive()){ 
				// while we're still listening for connections
				// check to see if user wants to shut us down
					if ((userInput = stdIn.readLine()) != null && userInput.equalsIgnoreCase("exit")){
						// they do! shut down.
						listen.interrupt();
						break;
					}
			}
		} catch (IOException e){
			// Issue reading std in
			System.out.println("Server - Internal error!\nAborting...");
			System.exit(1);
		}
	}
	
	/**
	 * How to use this program message.
	 */
	private static void Usage() {
		System.out.println("USAGE:"+
				"\n\tjava Server [option]"+
				"\n\nExample: java ProjectTwo 12235" +
				"\n\nOptions:" +
				"\n\tport\tspecify a port to run on. (Default is 12235)" +
				"\n\t-help\tview usage");
		System.exit(1);
	}
}