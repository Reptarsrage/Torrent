/**
 * Justin Robb
 * 12/13/13
 * Client Object used by the sever
 */

import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents one client (connected to server).
 * 
 * @author Justin Robb
 *
 */
public class ClientObj {
	private List<ServerFile> uploaded;	// List of files uploaded by client
	private InetAddress IPAddress;		// Remote address of client
	private int portNumber;				// Remote port number of client
	private Socket connection_socket;	// Connection to clients listener
	private int id;						// id of client
	
	/**
	 * Constructs a client.
	 * 
	 * @param IPAddress Remote address of client.
	 * @param portNumber Remote port number of client.
	 * @param id Unique identifier for client.
	 */
	public ClientObj(InetAddress IPAddress, int portNumber, int id){
		this.IPAddress = IPAddress;
		this.portNumber = portNumber;
		this.id = id;
		uploaded = new ArrayList<ServerFile>();
		connection_socket = null;
	}
	
	/**
	 * Get the list of all files uploaded by this client.
	 * 
	 * @return A list of uploaded files.
	 */
	public List<ServerFile> getFiles(){
		return uploaded;
	}
	
	/**
	 * Adds a file to this client's list of uploaded files.
	 * 
	 * @param file The file to to to uploads.
	 */
	public void addUpload(ServerFile file){
		uploaded.add(file);
	}
	
	/**
	 * Checks to see if client has uploaded a file.
	 * 
	 * @param id The id of the file to check for.
	 * @return True if this client has uploaded a file with the given id.
	 */
	public boolean hasFile(int id){
		for (ServerFile f : uploaded){
			if (f.id() == id)
				return true;
		}
		return false;
	}
	
	/**
	 * Gets the address of this client.
	 * 
	 * @return Address of the client connection.
	 */
	public InetAddress getAddress(){
		return IPAddress;
	}
	
	/**
	 * Gets the connection to the client's listener.
	 * 
	 * @return A socket connected to clients listener.
	 */
	public Socket getListenerSocket(){
		return connection_socket;
	}
	
	/**
	 * Sets up a connection to the client's listener.
	 */
	public void setListenerSocket(Socket connection_socket) throws SocketException{
		this.connection_socket = connection_socket;
		this.connection_socket.setSoTimeout(0);
		this.connection_socket.setKeepAlive(true);
	}
	
	/**
	 * Gets the remote port for a client connection.
	 * 
	 * @return Client's port number.
	 */
	public int getPort(){
		return portNumber;
	}
	
	/**
	 * Updates the address for this client.
	 * 
	 * @param addr The address of the client.
	 */
	public void setAddress(InetAddress addr){
		IPAddress = addr;
	}
	
	/**
	 * Updates the port number for this client.
	 * 
	 * @param prt The remote port number of the client.
	 */
	public void setPort(int port){
		portNumber = port;
	}
	
	/**
	 * Gets the id for the client.
	 * 
	 * @return The unique identifier for this client.
	 */
	public int getId(){
		return id;
	}
	
	/**
	 * Tests for equality between this and another object.
	 * 
	 * @param other The object to be compared with.
	 * @return True if this == other.
	 */
	public boolean Equals(Object other){
	if (other == null)
		return false;
      if (!this.getClass().equals(other.getClass()))
    	  return false;
      ClientObj otherF = (ClientObj)other;
      return (id == otherF.getId());
	}
}
