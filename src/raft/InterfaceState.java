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
	public static LinkedBlockingDeque<CommandMessage> interfaceMessageQueue = new LinkedBlockingDeque<CommandMessage>();
	double clusterSize = 1;

	@Override
	public synchronized void setManager(RaftManager Mgr) {
		this.Manager = Mgr;
	}

	@Override
	public synchronized RaftManager getManager() {
		return Manager;
	}

	public void process() {
		System.out.println("In Interface process method");
		try {
			// checking cluster size
			for (EdgeInfo ei : Manager.getEdgeMonitor().getOutBoundEdges().map.values()) {
				clusterSize = 1;
				if (ei.isActive() && ei.getChannel() != null) {
					clusterSize++;
				}
			}

			for (int i = 0; i < 5 && !interfaceMessageQueue.isEmpty(); i++) {
				System.out.println("before taking message " + interfaceMessageQueue.size());
				Manager.getEdgeMonitor().sendCmdMessageToNode(interfaceMessageQueue.take(), Manager.getLeaderId());
			}
			
			Thread.sleep(3000);
		} catch (Exception e) {
			e.printStackTrace();
		}
	};

	// latest implementation
	// public void receivedVote(WorkMessage msg);
	//
	// public void replyVote(WorkMessage msg);
	//
	public void onRequestVoteReceived(WorkMessage msg) {
	};

	public void receivedVoteReply(WorkMessage msg) {
	};

	public void receivedHeartBeat(WorkMessage msg) {
	};

	public void receivedLogToWrite(CommandMessage msg) {
	};

	public void chunkReceived(WorkMessage msg) {
	};

	public void responseToChuckSent(WorkMessage msg) {
	};
}