package raft;


import java.util.concurrent.LinkedBlockingDeque;

import gash.router.server.PrintUtil;
import gash.router.server.edges.EdgeInfo;
import pipe.common.Common.Chunk;
import pipe.common.Common.Header;
import pipe.common.Common.Request;
import pipe.common.Common.TaskType;
import pipe.common.Common.WriteBody;
import pipe.election.Election.LeaderStatus;
import pipe.work.Work.Heartbeat;
import pipe.work.Work.WorkMessage;
import pipe.work.Work.WorkState;
import routing.Pipe.CommandMessage;


public class LeaderState implements RaftState {
 private RaftManager Manager;
 LinkedBlockingDeque<WorkMessage> chunkMessageQueue= new LinkedBlockingDeque<WorkMessage>();
	
 @Override
 	public synchronized void process() { 
		//System.out.println("In leaders process method"); 
	 	try{
			 for(EdgeInfo ei:Manager.getEdgeMonitor().getOutBoundEdges().map.values())
			 {
				if(ei.isActive()&&ei.getChannel()!=null)
				{							
					Manager.getEdgeMonitor().sendMessage(createHB());
					System.out.println("sent hb to"+ei.getRef());
				}				
			 }
			
		 }
		 catch(Exception e){
			 e.printStackTrace();
		 }
		 
	 }
 	
	//CREATE HEARTBEAT
	public WorkMessage createHB() {
	WorkState.Builder sb = WorkState.newBuilder();
	sb.setEnqueued(-1);
	sb.setProcessed(-1);
	
	Heartbeat.Builder bb = Heartbeat.newBuilder();
	bb.setState(sb);
	
	Header.Builder hb = Header.newBuilder();
	hb.setNodeId(Manager.getNodeId());
	hb.setDestination(-1);
	hb.setTime(System.currentTimeMillis());
	
	LeaderStatus.Builder lb=LeaderStatus.newBuilder();
	lb.setLeaderId(Manager.getNodeId());
	lb.setLeaderTerm(Manager.getTerm());
	
	WorkMessage.Builder wb = WorkMessage.newBuilder();		
	wb.setHeader(hb);		
	wb.setBeat(bb);
	wb.setLeader(lb);
	wb.setSecret(10);				
	return wb.build();	
	}
	 
 //received hearbeat no need to implement here
 public synchronized void receivedHeartBeat(WorkMessage msg)
 {
	 Manager.randomizeElectionTimeout();		
		System.out.println("received hearbeat from the Leader: "+msg.getLeader().getLeaderId());
		PrintUtil.printWork(msg);		
		Manager.setCurrentState(Manager.Follower);
		Manager.setLastKnownBeat(System.currentTimeMillis());
 }
 
   @Override
	public synchronized void setManager(RaftManager Mgr) {
		this.Manager = Mgr;
	}

	@Override
	public synchronized RaftManager getManager() {
		return Manager;
	}

	 @Override
	 public void onRequestVoteReceived(WorkMessage msg) {
	 	// TODO Auto-generated method stub
	 	
	 }	
	 @Override
	 public void receivedVoteReply(WorkMessage msg) {
	 	// TODO Auto-generated method stub
		 return;
	 	
	 }	
	  public void receivedLogToWrite(CommandMessage msg)
		{
			System.out.println("reached leader now ");
			System.out.println("building work message");
			
			WorkMessage wm=buildWorkMessage(msg); 
			try {
				chunkMessageQueue.put(wm);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			//PrintUtil.printWork(wm);
			for(EdgeInfo ei:Manager.getEdgeMonitor().getOutBoundEdges().map.values())
			{
				if(ei.getChannel()!=null&&ei.isActive())
				{
					Manager.getEdgeMonitor().sendMessage(wm);
					
				}
			}
			
		}
	  
	  public WorkMessage buildWorkMessage(CommandMessage msg)
	  {
		System.out.println("wmsg fun");
		   
		    Header.Builder hb = Header.newBuilder();
			hb.setNodeId(Manager.getLeaderId());
			hb.setTime(System.currentTimeMillis());
			hb.setDestination(-1);
			
			Chunk.Builder chb=Chunk.newBuilder();
			chb.setChunkId(msg.getRequest().getRwb().getChunk().getChunkId());
			chb.setChunkData(msg.getRequest().getRwb().getChunk().getChunkData());
			chb.setChunkSize(msg.getRequest().getRwb().getChunk().getChunkSize());
			
			WriteBody.Builder wb=WriteBody.newBuilder();
			
			wb.setFilename(msg.getRequest().getRwb().getFilename());
			wb.setChunk(chb);
			wb.setNumOfChunks(msg.getRequest().getRwb().getNumOfChunks());
			
			Request.Builder rb = Request.newBuilder();
			//request type, read,write,etc				
			rb.setRwb(wb);	
			rb.setRequestType(TaskType.WRITEFILE);
			WorkMessage.Builder wbs = WorkMessage.newBuilder();
			// Prepare the CommandMessage structure
		
			wbs.setHeader(hb);
			wbs.setRequest(rb);
			wbs.setSecret(10);
			System.out.println("retunr fun");
			return wbs.build();
	  }
	  
	  
	  public void chunkReceived(WorkMessage msg)
	  {
		  return;
	  }
	 
}









