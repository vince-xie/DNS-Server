import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;	// for ByteBuffer, ByteOrder

public class Server {
	
	private final static int DEFAULT_PORT = 7652;
	private final static String DEFAULT_FILE = "hosts.txt";
	private static int port;
	private static String filename;
<<<<<<< HEAD
	private static byte[] input = new byte[256]; 	
	private static byte[] output = new byte[256];
	private static DatagramPacket packet;	
=======
	static byte[] receive = new byte[256]; //check sizes
>>>>>>> 5d39702340b531b22dedfd68d0346253e4a7a8c6
	static DatagramSocket socket;
	private static byte[] queryString;
	
	protected void finalize(){
		socket.close();
	} 
	
	// With our Arraylist of lines, we parse each line to create a hashmap of <key,value> -> <hostname, address> objects
	public static HashMap<String, String> parseLines(ArrayList<String> lines){
		HashMap<String, String> output = new HashMap<String, String>();
		for(int i = 0; i < lines.size(); i++){
			if(lines.get(i).length() > 0){
				if(lines.get(i).charAt(0) != '#'){
					int index = 0;
					int end = 0;
					String remaining = "";
					for(int j = 0; j < lines.get(i).length(); j++){
						if(Character.isWhitespace(lines.get(i).charAt(j))){
							remaining = lines.get(i).substring(j).trim();
							if(remaining.indexOf('#') > 0){
								remaining = remaining.substring(0, remaining.indexOf('#'));
							}
							remaining = remaining.trim().toLowerCase();
							index = j;
							break;
						}
					}
					output.put(remaining, lines.get(i).substring(0, index));
				}
			}
		}
		return output;
	}
	
	// get Big Endian Shorts Array
	public static short[] getBEShortsArr(byte[] pbuf, int plen){
		
		// generate a short array (as shown in Professor K's notes)
		short[] shorts = new short[plen/2];
		ByteBuffer.wrap(pbuf).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(shorts);
		return shorts;
	}
	
	// Check if query is valid!
	public static boolean isQuery(short[] header){
		
		// 1. check QR = 0, 16th bit in DNS header
		// 2. check RD = 1, 23rd bit in DNS header
		// 3. check QD = 1, 3rd short in DNS header
		// 4. check last 3 shorts are 0 
		
		// check 1 & 2 (left to right)
		if( ( (header[1] >> 15) & 1) != 0 || ( (header[1] >> 8) & 1) != 1){
			return false;
		}
		
		// check 3
		if( (header[2] != 1) ){
			return false;
		}
		
		// check 4
		if( header[3] != 0 || header[4] != 0 || header[5] != 0 ){
			return false;
		}
		
		return true;
	}
	
	// To be used when parsing the hostname. Check if the next byte is a null terminating character or period
	public static int checkOffset(boolean shift, int offset, short[] header){
		if(shift){
			return  (int) ( (header[offset] >> 8) & 0xff);
		}
		
		return  (int) ( (header[offset] & 0xff) );
	}
	
	// Retreive the hostname by manipulating the short array
	// In addition, we would like to record the Query bytes for the Answer portion of the header.
		// To achieve this we incrementally record a list of Bytes
	
	public static String getHostname(short[] header){
		// create Byte list of Query bytes
		ArrayList<Byte> list = new ArrayList<Byte>();
		
		String hostname = "";
		
		// parameters for building the String
		int i, offset;
		boolean shift = true;
		boolean first = true;
		offset = 6;
		
		// check if the remaining number of bytes left until a period or null character > 0
		while( (i = checkOffset(shift,offset,header) ) > 0 ){
			
			list.add((byte) i);
			
			// add periods and necessary bookmarking 
			if(first){
				first = !first;
			}else{
				hostname += '.';
				if(!shift){
					offset++;
				}
			}
			
			// checks if bytes in the short must be shifted
			shift = !shift;
			
			while(i > 0){
				if(shift){
					list.add(   (byte) (( header[offset] >> 8)  &  0xff) ) ;
					hostname += (char) ( (header[offset] >> 8) & 0xff);
				}else{
					list.add(   (byte) (( header[offset])  &  0xff) ) ;
					hostname  += (char) (header[offset] & 0xff);
					offset++;
				}
				shift = !shift;
				i--;
			}
				
		}
		
		// check the 
		if(!shift){
			offset++;
		}
		
		shift = !shift;
	
		// If query type or query class is invalid (not 1), then return null because we cannot service this request
		if(shift){
			if(input[offset*2] != 0 || input[offset*2 + 1] != 1 || input[offset*2 + 2] != 0 || input[offset*2 +3] != 1){
				return null;
			}
		}else{
			if(input[offset*2+1] != 0 || input[offset*2 + 2] != 1 || input[offset*2 + 3] != 0 || input[offset*2 +4] != 1){
				return null;
			}
		}
	
		
		// from the Arraylist, create a regular array for later use
		int j;
		queryString = new byte[list.size()+1];
		for( j = 0 ; j < list.size() ; j++){
			queryString[j] = list.get(j);
		}
		queryString[j] = (byte) 0; // add null terminating character
		
		return hostname.toLowerCase();
		
	}
	
	// Convert a String address to a Byte Array 
	public static byte[] stringToByteArray(String address){
		int pIndex1, pIndex2, pIndex3;
		pIndex1 = address.indexOf(".",0); 
		pIndex2 = address.indexOf(".",pIndex1+1); 
		pIndex3 = address.indexOf(".",pIndex2+1);
		
		byte[] result = new byte[4];
		result[0] = (byte) Integer.parseInt(address.substring(0,pIndex1));
		result[1] = (byte) Integer.parseInt(address.substring(pIndex1+1,pIndex2));
		result[2] = (byte) Integer.parseInt(address.substring(pIndex2+1,pIndex3));
		result[3] = (byte) Integer.parseInt(address.substring(pIndex3+1));
		
		return result;
	}
	

	public static void main(String[] args){
		// Initialization and Parameter Checking
		port = DEFAULT_PORT;
		filename = DEFAULT_FILE;
		if(args.length % 2 != 0 || args.length > 4){
			System.out.println("Incorrect number of arguments");
			return;
		}
		if(args.length > 0){
			if(args[0].equals("-p")){
				try{
					port = Integer.parseInt((args[1]));
				} catch(NumberFormatException e){
					System.out.println("Port is not valid.");
					return;
				}
				if(args.length > 2){
					if(args[2].equals("-f")){
						filename = args[3];
						if(filename.length() == 0){
							System.out.println("Invalid path to file");
							return;
						}
					}
				}
			}
			if(args[0].equals("-f")){
				filename = args[1];
				if(filename.length() == 0){
					System.out.println("Invalid path to file");
					return;
				}
				if(args.length > 2){
					if(args[2].equals("-p")){
						try{
							port = Integer.parseInt((args[3]));
						} catch(NumberFormatException e){
							System.out.println("Port is not valid.");
							return;
						}
					}
				}
			}
		}
		// Reading the file and parsing it
		ArrayList<String> lines = new ArrayList<String>();
		port = (port == 7652) ? DEFAULT_PORT: port;
		filename = (filename.equals(DEFAULT_FILE)) ? DEFAULT_FILE : filename;
		BufferedReader file;
		try {
			file = new BufferedReader(new FileReader(new File(filename)));
			String line = "";
			while((line = file.readLine()) != null){
				lines.add(line.trim());
			}
			file.close();
		} catch (FileNotFoundException e) {
			System.out.println("File not found");
			return;
		} catch (Exception e){
			System.out.println("Error reading file");
			return;
		}
		HashMap<String, String> hostToAddress = parseLines(lines);
		
		try{
			socket = new DatagramSocket(port);
			System.out.println("Server started on port " + port + ".");
			while(true){
<<<<<<< HEAD
				
				// create Datagram packet
				packet = new DatagramPacket(input, input.length);
				// block until packet is received
				socket.receive(packet);
				// print out metadata
				System.out.println("received " + packet.getLength() +
						" bytes from " + packet.getAddress().getHostName());
								
				short[] shorts = getBEShortsArr(packet.getData(),packet.getLength());
							
				// validate Datagram as a DNS query
				if(!isQuery(shorts) ){
					System.out.println("Error: This Datagram is not a DNS query");
					output = input;
					
					// response qr = 1
					output[2] = (byte) (output[2] | (byte) 0x80 );
					
					// rcode = 4 NOT IMPLEMENTED response
					output[3] = (byte) ((output[3] & (byte) 0xf0) | (byte) 0x4);
					socket.send(new DatagramPacket(output, packet.getLength(), packet.getAddress(),packet.getPort()));
					
				}else{
					String hostname = getHostname(shorts);
					
					// if null, then there was a problem with the type and class of the query
					if(hostname == null){
						
						output = input;
						output[2] = (byte) (output[2] | (byte) 0x80 );	// qr = 1
						output[2] = (byte) (output[2] | (byte) 0x04);	// aa = 1
						output[2] = (byte) (output[2] & (byte) 0xfc);	// tc = 0 and ra = 0
						
						// rcode = 3 NAME ERROR response
						output[3] = (byte) ((output[3] & (byte) 0xf0) | (byte) 0x3);
						socket.send(new DatagramPacket(output, packet.getLength(), packet.getAddress(),packet.getPort()));
					}else{						
						if(hostToAddress.containsKey(hostname)){

							// get address from hostname
							String address = hostToAddress.get(hostname);
							
							// convert string to byte array
							byte[] addressArr = stringToByteArray(address);
							
							// built output byte array
							output = new byte[packet.getLength()+queryString.length+2+2+4+2+4];
							

							// copy contents 
								//original portion
							System.arraycopy(input, 0, output, 0, packet.getLength());	
								// append query name
							System.arraycopy(queryString,0,output,packet.getLength(),queryString.length);
							int pos = packet.getLength()+queryString.length;

								// append query type
							output[pos] = (byte) 0x0;
							output[++pos] = (byte) 0x1;
								// append query class
							output[++pos] = (byte) 0x0;
							output[++pos] = (byte) 0x1;
								// append TTL 
							output[++pos] = (byte) 0x0;
							output[++pos] = (byte) 0x0;
							output[++pos] = (byte) 0x0;
							output[++pos] = (byte) 0x0;
								// append answer length
							output[++pos] = (byte) 0x0;
							output[++pos] = (byte) 0x4;

								// append the address itself
							System.arraycopy(addressArr, 0, output, ++pos, addressArr.length);

							
							// set flags
							output[2] = (byte) (output[2] | (byte) 0x80);	// qr = 1
							output[2] = (byte) (output[2] | (byte) 0x04);	// aa = 1
							output[2] = (byte) (output[2] & (byte) 0xfc);	// tc = 0 and ra = 0
							
							// set answer count = 1
							output[6] = (byte) 0x00;
							output[7] = (byte) 0x01;
							
							
							socket.send(new DatagramPacket(output, output.length, packet.getAddress(),packet.getPort()));

						}else{
							output = input;
							output[2] = (byte) (output[2] | (byte) 0x80 );	// qr = 1
							output[2] = (byte) (output[2] | (byte) 0x04);	// aa = 1
							output[2] = (byte) (output[2] & (byte) 0xfc);	// tc = 0 and ra = 0
							// rcode = 3 NAME ERROR response
							output[3] = (byte) ((output[3] & (byte) 0xf0) | (byte) 0x3);
							socket.send(new DatagramPacket(output, packet.getLength(), packet.getAddress(),packet.getPort()));
						}
						
					}
					
					
				}
				
				
=======
				DatagramPacket received = new DatagramPacket(receive, receive.length);
				socket.receive(received);
				String input = new String(received.getData());
				//parse input
				//go to hashtable
				InetAddress returnAddress = received.getAddress();                   
				int returnPort = received.getPort();
				//build output
				byte[] output = new byte[256];
				output = input.getBytes();
				DatagramPacket response = new DatagramPacket(output, output.length, returnAddress, returnPort);
				socket.send(response);
>>>>>>> 5d39702340b531b22dedfd68d0346253e4a7a8c6
			}
		} catch (SocketException e) {
			System.out.println("Could not create socket on port " + port + ".");
		} catch (Exception e){
			System.out.println("Error");
		}
	}

}