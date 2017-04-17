package raft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gash.router.server.PrintUtil;
import gash.router.server.ServerState;
import gash.router.server.edges.EdgeInfo;
import gash.router.server.edges.EdgeMonitor;
import pipe.common.Common.AddNewNode;
import pipe.common.Common.Header;
import pipe.election.Election.Vote;
import pipe.work.Work.WorkMessage;
import routing.Pipe.CommandMessage;

public class FollowerState implements RaftState {
	protected static Logger logger = LoggerFactory.getLogger("Follower State");
	private RaftManager Manager;
	private int votedFor=-1;
	private boolean initial=true;
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
				//System.out.println("election timeout value "+dt); 		
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
	
	/*public synchronized void newNodePing(){
		for(EdgeInfo ei:Manager.getEdgeMonitor().getOutBoundEdges().map.values())
		 {
			System.out.println("in new node for");
			if(ei.isActive()&&ei.getChannel()!=null)
			{	
				System.out.println("in new node if");
				Header.Builder hb = Header.newBuilder();
				hb.setNodeId(Manager.getNodeId());
				hb.setTime(System.currentTimeMillis());
				hb.setDestination(-1);
				
				AddNewNode.Builder ab=AddNewNode.newBuilder();
				ab.setHost(Manager.getSelfHost());
				ab.setPort(Manager.getSelfPort());
				
				WorkMessage.Builder wb = WorkMessage.newBuilder();
				wb.setHeader(hb);				
				wb.setAddnewnode(ab);
				wb.setSecret(10);
				Manager.getEdgeMonitor().sendMessage(wb.build());
				System.out.println("sent ping request to"+ei.getRef());				
			}				
		 }
		initial=false;
	}*/
	
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
		System.out.println("received hearbeat from the Leader: "+msg.getLeader().getLeaderId());
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
	  }
	 
 
}

