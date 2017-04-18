package raft;

import gash.router.server.PrintUtil;
import gash.router.server.edges.EdgeInfo;
import pipe.common.Common.Header;
import pipe.election.Election.LeaderStatus;
import pipe.election.Election.RequestVote;
import pipe.work.Work.WorkMessage;
import routing.Pipe.CommandMessage;

public class CandidateState implements RaftState {
	private RaftManager Manager;
	private double voteCount = 0;
	private int candidateId;
	private int votedFor = -1;
	private double clusterSize = 0;

	public void process() {
		System.out.println("reached candidate State");
		try {
			// if (Manager.getElectionTimeout() <= 0 &&
			// (System.currentTimeMillis() - Manager.getLastKnownBeat() >
			// Manager.getHbBase())) {
			System.out.println("Node : " + Manager.getNodeId() + " timed out");
			// be followers method impl should come here
			requestVote();
			Manager.randomizeElectionTimeout();
			Thread.sleep(200);
			long dt = Manager.getElectionTimeout() - (System.currentTimeMillis() - Manager.getTimerStart());
			Manager.setElectionTimeout(dt);
			return;
			// }else{
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	// CREATE REQUEST VOTE MESSAGE
	public WorkMessage buildRequestVote() {

		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(Manager.getNodeId());
		hb.setDestination(-1);

		RequestVote.Builder rvb = RequestVote.newBuilder();
		rvb.setCandidateID(Manager.getNodeId());
		rvb.setCurrentTerm(Manager.getTerm());

		WorkMessage.Builder wb = WorkMessage.newBuilder();
		wb.setHeader(hb);
		wb.setReqvote(rvb);
		wb.setSecret(10);

		return wb.build();
	}

	public synchronized void requestVote() {

		System.out.println("reached requestVote method of candidate");
		Manager.setTerm(Manager.getTerm() + 1);
		clusterSize = -1;
		for (EdgeInfo ei : Manager.getEdgeMonitor().getOutBoundEdges().map.values()) {
			if (ei.isActive() && ei.getChannel() != null) {
				clusterSize++;
			}
		}
		if (clusterSize == 0) {
			Manager.randomizeElectionTimeout();
			System.out.println(
					"Leader Elected and the Node Id is " + Manager.getNodeId() + " total active nodes is" + clusterSize);
			Manager.setLeaderId(Manager.getNodeId());
			Manager.setCurrentState(Manager.Leader);
			System.out.println("Have to send leader to interface");
			Manager.getEdgeMonitor().sendWorkMessageToNode(createWM(), 4);
		} else
			clusterSize++;
		System.out.println("active count is" + clusterSize);
		voteCount = 0;
		voteCount++;
		System.out.println("voted for self");
		for (EdgeInfo ei : Manager.getEdgeMonitor().getOutBoundEdges().map.values()) {
			if (ei.isActive() && ei.getChannel() != null) {
				System.out.println("voteRequest sent to" + ei.getRef());
				Manager.getEdgeMonitor().sendMessage(buildRequestVote());
			}
		}
		return;
	}

	// if somebody requests vote of candidate
	@Override
	public synchronized void onRequestVoteReceived(WorkMessage msg) {
		// TODO Auto-generated method stub
		/*
		 * System.out.println("Candidates Vote requested by "+msg.getHeader().
		 * getNodeId()); if (msg.getReqvote().getCurrentTerm() >
		 * Manager.getTerm()) { votedFor = -1;
		 * Manager.randomizeElectionTimeout();
		 * Manager.setCurrentState(Manager.Follower);
		 * Manager.getCurrentState().onRequestVoteReceived(msg);
		 * 
		 * }
		 */
	}

	// received vote
	@Override
	public synchronized void receivedVoteReply(WorkMessage msg) {
		System.out.println("received vote from: " + msg.getVote().getVoterID() + " to me");
		voteCount++;

		System.out.println("required votes to win :" + clusterSize / 2);
		if (voteCount >= (clusterSize / 2)) {
			Manager.randomizeElectionTimeout();
			System.out.println(
					"Leader Elected and the Node Id is " + Manager.getNodeId() + "total active nodes is" + clusterSize);
			Manager.setLeaderId(Manager.getNodeId());
			votedFor = -1;
			clusterSize = 0;
			Manager.setCurrentState(Manager.Leader);
			Manager.getEdgeMonitor().sendWorkMessageToNode(createWM(), 4);
		}
	}
	
	public WorkMessage createWM() {	
		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(Manager.getNodeId());
		hb.setDestination(-1);
		hb.setTime(System.currentTimeMillis());
		
		LeaderStatus.Builder lb=LeaderStatus.newBuilder();
		lb.setLeaderId(Manager.getNodeId());
		lb.setLeaderTerm(Manager.getTerm());
		
		WorkMessage.Builder wb = WorkMessage.newBuilder();		
		wb.setHeader(hb);		
		wb.setLeader(lb);
		wb.setSecret(10);				
		return wb.build();	
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
	public synchronized void receivedHeartBeat(WorkMessage msg) {
		Manager.randomizeElectionTimeout();
		System.out.println("received hearbeat from the Leader: " + msg.getLeader().getLeaderId());
		PrintUtil.printWork(msg);
		Manager.setCurrentState(Manager.Follower);
		Manager.setLastKnownBeat(System.currentTimeMillis());
	}

	public void receivedLogToWrite(WorkMessage msg) {
		return;
	}

	public void chunkReceived(WorkMessage msg) {
		return;
	}

	public void responseToChuckSent(WorkMessage msg) {
		return;
	}

	@Override
	public void receivedCommitChunkMessage(WorkMessage msg) {
		// TODO Auto-generated method stub

	}

}
