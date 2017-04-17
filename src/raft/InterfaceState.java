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


public class InterfaceState implements RaftState {
 private RaftManager Manager;
 LinkedBlockingDeque<WorkMessage> chunkMessageQueue= new LinkedBlockingDeque<WorkMessage>();
 	  
   @Override
	public synchronized void setManager(RaftManager Mgr) {
		this.Manager = Mgr;
	}

	@Override
	public synchronized RaftManager getManager() {
		return Manager;
	}
	
	public void process(){};
	
	//latest implementation
//	public void receivedVote(WorkMessage msg);
//
//	public void replyVote(WorkMessage msg);
//
	public void onRequestVoteReceived(WorkMessage msg){};
	public void receivedVoteReply(WorkMessage msg){};
	public void receivedHeartBeat(WorkMessage msg){};
	public void receivedLogToWrite(CommandMessage msg){};
	public void chunkReceived(WorkMessage msg){};
	
}









