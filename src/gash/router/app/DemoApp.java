/**
 * Copyright 2016 Gash.
 *
 * This file and intellectual content is protected under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package gash.router.app;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import gash.router.client.CommConnection;
import gash.router.client.CommListener;
import gash.router.client.MessageClient;
import gash.router.server.PrintUtil;
import routing.Pipe;
import routing.Pipe.CommandMessage;

public class DemoApp implements CommListener {
	private MessageClient mc;
	private String leaderHost="";
	private int leaderPort=0;
	private Map<String, ArrayList<CommandMessage>> fileBlocksList = new HashMap<String, ArrayList<CommandMessage>>();
	protected static Logger logger = LoggerFactory.getLogger(DemoApp.class);
	public DemoApp(MessageClient mc) {
		init(mc);
	}

	private void init(MessageClient mc) {
		this.mc = mc;
		this.mc.addListener(this);
	}

	private void ping(int N) {
		// test round-trip overhead (note overhead for initial connection)
		final int maxN = 10;
		long[] dt = new long[N];
		long st = System.currentTimeMillis(), ft = 0;
		for (int n = 0; n < N; n++) {
			mc.ping();
			ft = System.currentTimeMillis();
			dt[n] = ft - st;
			st = ft;
		}

		System.out.println("Round-trip ping times (msec)");
		for (int n = 0; n < N; n++)
			System.out.print(dt[n] + " ");
		System.out.println("");
	}
	
	 private ArrayList<ByteString> divideFileChunks(File file) throws IOException {
	        ArrayList<ByteString> chunkedFile = new ArrayList<ByteString>();
	        int sizeOfFiles = 1024 * 1024; // equivalent to 1 Megabyte
	        byte[] buffer = new byte[sizeOfFiles];

	        try {
	            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
	            int tmp = 0;
	            while ((tmp = bis.read(buffer)) > 0) {
	                ByteString byteString = ByteString.copyFrom(buffer, 0, tmp);
	                chunkedFile.add(byteString);
	            }
	            return chunkedFile;
	        } catch (Exception e) {
	            e.printStackTrace();
	            return null;
	        }
	    }

	@Override
	public String getListenerID() {
		return "demo";
	}

	@Override
	public void onMessage(CommandMessage msg) {
		if (msg == null) {
			// TODO add logging
			System.out.println("ERROR: Unexpected content - " + msg);
			return;
		}
		
		if(msg.hasLeaderroute()){
			System.out.println("leader host : "+msg.getLeaderroute().getHost());
			System.out.println("leader port :"+msg.getLeaderroute().getPort());
		}
		else 
			if(msg.getResponse().hasReadResponse()){
				System.out.println("i've recieved file in pieces");
				//PrintUtil.printChunkResponseDetails(msg);	
		        System.out.println("The file has been arrived in bytes");
		        logger.info("Printing msg from server" + msg.getHeader().getNodeId());
		        if (!fileBlocksList.containsKey(msg.getResponse().getReadResponse().getFilename())) {
		            fileBlocksList.put(msg.getResponse().getReadResponse().getFilename(), new ArrayList<Pipe.CommandMessage>());
		            System.out.println("Created Chunk list");
		        }
		        
		            fileBlocksList.get(msg.getResponse().getReadResponse().getFilename()).add(msg);
		           
		           if (fileBlocksList.get(msg.getResponse().getReadResponse().getFilename()).size() == msg.getResponse().getReadResponse().getNumOfChunks()) {
		                try {

		                    File file = new File(msg.getResponse().getReadResponse().getFilename());
		                    file.createNewFile();
		                    List<ByteString> byteString = new ArrayList<ByteString>();
		                    FileOutputStream outputStream = new FileOutputStream(file);
		                    
		                    
		                    for (int j = 0; j < fileBlocksList.get(msg.getResponse().getReadResponse().getFilename()).size(); j++) {
		                      
		                       System.out.println("Inside the for loop ");		                         
		                       System.out.println("Added chunk to file " + j);
		                       byteString.add(fileBlocksList.get(msg.getResponse().getReadResponse().getFilename()).get(j).getResponse().getReadResponse().getChunk().getChunkData());		                                		                               
		                    
		                    }
		                        
		                    System.out.println("out of the loop");
		                    ByteString bs = ByteString.copyFrom(byteString);
		                    System.out.println(bs.size());
		                    outputStream.write(bs.toByteArray());
		                    System.out.println("file built");
		                    outputStream.flush();
		                    outputStream.close();

		                } catch (IOException e) {
		                    e.printStackTrace();
		                }

		            }
		            System.out.flush();
			}
	}

	/**
	 * sample application (client) use of our messaging service
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		String host = "localhost";
		int port = 4468;

		try {
			MessageClient mc = new MessageClient(host, port);			
			DemoApp da = new DemoApp(mc);
			mc.ping();
			
			int choice = 0;
			Scanner s = new Scanner(System.in);
            while (true) {
                System.out.println("Enter your option \n1. WRITE a file. \n2. READ a file. \n3. Update a File. \n4. Delete a File\n 5 Ping(Global)\n 6 Exit");
                choice = s.nextInt();
                switch (choice) {
                    case 1: 
                        System.out.println("Enter the full pathname of the file to be written ");
                        //String currFileName = s.next();
                        String currFileName = "C:\\users\\ilabhesh\\desktop\\c.pdf";
                        File file = new File(currFileName);
                        if (file.exists()) {
                            ArrayList<ByteString> chunkedFileList = da.divideFileChunks(file);
                            String name = file.getName();
                            int i = 0;                            
                            for (ByteString string : chunkedFileList) {
                                System.out.println(string);
                            	mc.writeFile(name, string, chunkedFileList.size(), i++);
                            }
                        } else {
                            throw new FileNotFoundException("File does not exist in this path ");
                        } 
                    break;
                    case 2: {
                        System.out.println("Enter the file name to be read : ");
                        String fileName = s.next();
                        mc.readFile(fileName);
                        // Thread.sleep(1000 * 100);
                    }
                    break;
                    default:
                    	System.out.println("Invalid option");                   
                }
            }          
		} catch (Exception e) {
			e.printStackTrace();
		} /*finally {
			CommConnection.getInstance().release();
		}*/
	}
}
