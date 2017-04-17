package raft;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gash.router.server.PrintUtil;
import gash.router.server.ServerState;
import gash.router.server.edges.EdgeInfo;
import gash.router.server.edges.EdgeMonitor;
import pipe.common.Common.AddNewNode;
import pipe.common.Common.Chunk;
import pipe.common.Common.Header;
import pipe.common.Common.Response;
import pipe.common.Common.TaskType;
import pipe.common.Common.WriteResponse;
import pipe.election.Election.Vote;
import pipe.work.Work.WorkMessage;
import pipe.work.Work.WorkState;
import routing.Pipe.CommandMessage;

public class FollowerState implements RaftState {
	protected static Logger logger = LoggerFactory.getLogger("Follower State");
	private RaftManager Manager;
	private int votedFor=-1;
	private boolean initial=true;
	Map<String, ArrayList<WorkMessage>> fileChunkMap= new HashMap<String, ArrayList<WorkMessage>>();
	public synchronized void process()
	{
		
		
		try {
			if (Manager.getElectionTimeout() <= 0 && (System.currentTimeMillis() - Manager.getLastKnownBeat() > Manager.getHbBase())) {
				Manager.setCurrentState(Manager.Candidate);
				System.out.println("state changed to candidate... all set for leader election"); 
				return;
			} else {
				Thread.sleep(200);
				long dt = Manager.getElectionTimeout() - (System.currentTimeMillis() - Manager.getTimerStart());
				System.out.println("election timeout value "+dt); 		
				Manager.setElectionTimeout(dt);				
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	@Override
	public synchronized void setManager(RaftManager Mgr) {
		this.Manager = Mgr;
	}

	@Override
	public synchronized RaftManager getManager() {
		return Manager;
	}
	
	
	//giving vote after receiving request votes
	public synchronized void onRequestVoteReceived(WorkMessage msg){
		System.out.println("got a election request vote "); 
		Manager.setCurrentState(Manager.Follower);
		System.out.println("state is follower");
		Manager.randomizeElectionTimeout();
    	if(Manager.getTerm()<msg.getReqvote().getCurrentTerm() && votedFor==-1)
    	{    	    		
    		 //changed from node id to candidate id 
    		votedFor=msg.getReqvote().getCandidateID();
    			
    			//changed term value
    			Manager.setTerm(msg.getReqvote().getCurrentTerm());
    			System.out.println(System.currentTimeMillis() +": "+Manager.getNodeId() +" voted for " + votedFor + " in term "
						+ Manager.getTerm() + ". Timeout is : " + Manager.getElectionTimeout());
				replyVote(msg, true);    			    		
				votedFor=-1;
    		
    	}else
    	{
    		replyVote(msg,false);
    	}
    		
    	
    }
	
	
	//replyvote
	
	public synchronized void replyVote(WorkMessage msg,boolean sendVote)
	{
		if(sendVote==true){
			int toNode=msg.getReqvote().getCandidateID();
			int fromNode=Manager.getNodeId();
			EdgeInfo ei=Manager.getEdgeMonitor().getOutBoundEdges().map.get(toNode);
			if(ei.isActive()&&ei.getChannel()!=null)
			{
				System.out.println("Im giving my vote to "+toNode);
				ei.getChannel().writeAndFlush(Vote(fromNode, toNode));
				
			}
		}
	}
	
	
	
	public synchronized WorkMessage Vote(int NodeId,int CandidateId) {		
		Vote.Builder vb=Vote.newBuilder();		
		vb.setVoterID(NodeId);
		vb.setCandidateID(CandidateId);
		WorkMessage.Builder wb = WorkMessage.newBuilder();	
		wb.setVote(vb);
		wb.setSecret(10);
		return wb.build();
	}
	
	@Override
	public synchronized void receivedVoteReply(WorkMessage msg) {
		System.out.println("Im in follower recvVOteReply method.... doing nothing");
		// TODO Auto-generated method stub
		return;
		
	}
	
	@Override
	public synchronized void receivedHeartBeat(WorkMessage msg)
	{
		Manager.randomizeElectionTimeout();		
		System.out.println("received ehearbeat from the Leader: "+msg.getLeader().getLeaderId());
		PrintUtil.printWork(msg);		
		Manager.setCurrentState(Manager.Follower);
		Manager.setLastKnownBeat(System.currentTimeMillis());
	}	
	
	public void receivedLogToWrite(CommandMessage msg)
	{
		return;
	}
	public void chunkReceived(WorkMessage msg)
	  {
		  System.out.println("i received a chunk from leader");		
			PrintUtil.printWork(msg);
			Manager.randomizeElectionTimeout();			
			Manager.setCurrentState(Manager.Follower);
			Manager.setLastKnownBeat(System.currentTimeMillis());
			
				  
		  //adding chunk for a file to map for delayed commiting
			if (!fileChunkMap.containsKey(msg.getRequest().getRwb().getFilename())) {
				  fileChunkMap.put(msg.getRequest().getRwb().getFilename(), new ArrayList<WorkMessage>());		            
		        }
			fileChunkMap.get(msg.getRequest().getRwb().getFilename()).add(msg);
			System.out.println("added a chunk to map" +fileChunkMap.get(msg.getRequest().getRwb().getFilename()).size());
			//building write response message 
			Header.Builder hb = Header.newBuilder();
			hb.setNodeId(Manager.getNodeId());
			hb.setDestination(-1);
			hb.setTime(System.currentTimeMillis());
			
			Chunk.Builder chub=Chunk.newBuilder();
			chub.setChunkId(msg.getRequest().getRwb().getChunk().getChunkId());
			chub.setChunkData(msg.getRequest().getRwb().getChunk().getChunkData());
			chub.setChunkSize(msg.getRequest().getRwb().getChunk().getChunkSize());
			
			WriteResponse.Builder wrb=WriteResponse.newBuilder();
			wrb.setFileName(msg.getRequest().getRwb().getFilename());
			wrb.setChunk(chub);			
			wrb.setNumOfChunks(msg.getRequest().getRwb().getNumOfChunks());
			
			Response.Builder rb=Response.newBuilder();
			rb.setResponseType(TaskType.WRITEFILE);
			rb.setWriteResponse(wrb);
			WorkMessage.Builder wbs = WorkMessage.newBuilder();	
			wbs.setHeader(hb);
			wbs.setSecret(10);
			wbs.setResponse(rb);		
			int toNode=msg.getHeader().getNodeId();
			System.out.println("to node is"+toNode);
			PrintUtil.printWork(wbs.build());
			int fromNode=Manager.getNodeId();
			EdgeInfo ei=Manager.getEdgeMonitor().getOutBoundEdges().map.get(toNode);
			if(ei.isActive()&&ei.getChannel()!=null)
		   {
				System.out.println("Im responding to leader that i received chunk "+msg.getRequest().getRwb().getChunk().getChunkId());
				ei.getChannel().writeAndFlush(wbs.build());
				
		   }		  
		  
	  }
	public void responseToChuckSent(WorkMessage msg)
	  {
		return;  
	  }
	
	public void receivedCommitChunkMessage(WorkMessage msg)
	{
		System.out.println("going to commit now");
		String fileName=msg.getCommit().getFilename();
		int numOfChunks=msg.getCommit().getNumOfChunks();
		System.out.println(numOfChunks);
		System.out.println(fileName);
		 try{
			  Class.forName("com.mysql.jdbc.Driver");  
				Connection con=DriverManager.getConnection(  
				"jdbc:mysql://localhost:3306/mydb","root","abcd");  		   
				// create the mysql insert preparedstatement
				for (int j = 0; j < numOfChunks; j++) {                                  	                        
	            System.out.println("Added chunk to DB " + j);
	            String query = " insert into filetable (filename, chunkid, chunkdata, chunksize, numberofchunks)"
	  			      + " values (?, ?, ?, ?, ?)";
	            PreparedStatement preparedStmt = con.prepareStatement(query);
			    preparedStmt.setString (1, fileName);		   		   
			    preparedStmt.setInt(2, fileChunkMap.get(fileName).get(j).getRequest().getRwb().getChunk().getChunkId());				    				   				  
			    preparedStmt.setBytes(3,(fileChunkMap.get(fileName).get(j).getRequest().getRwb().getChunk().getChunkData()).toByteArray());				    
			    preparedStmt.setInt(4, fileChunkMap.get(fileName).get(j).getRequest().getRwb().getChunk().getChunkSize());
			    preparedStmt.setInt(5, numOfChunks);
			    preparedStmt.execute();			
				}
				con.close();
				 System.out.println("commited");
		 }
		
			  catch(Exception e){
				  e.printStackTrace();
			  }
	}
	
 
}

