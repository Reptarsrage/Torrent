/**
 * Justin Robb
 * 12/13/13
 * File object
 */

/**
 * A file object used by the server.
 * 
 * @author Justin Robb
 *
 */
public class ServerFile {
	private String name;	// Name of this file (not path)
	private long size;		// The size of this file
	private int id;			// The id of this file
	
	/**
	 * Constructs a file.
	 * 
	 * @param name Name of this file (not path).
	 * @param size The size of this file.
	 * @param id A unique identifier for this file.
	 */
	public ServerFile(String name, long size, int id){
		this.name = name;
		this.size = size;
		this.id = id;
	}
	
	/**
	 * 
	 * @return The name of this file.
	 */
	public String name(){
		return this.name;
	}
	
	/**
	 * 
	 * @return The size of this file.
	 */
	public long size(){
		return size;
	}
	
	/**
	 * 
	 * @return The id of this file.
	 */
	public int id(){
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
      ServerFile otherF = (ServerFile)other;
      return (name.equals(otherF.name()) && size == otherF.size());
	}
}
