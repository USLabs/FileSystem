package raft;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Random;

import gash.router.container.RoutingConf;
import gash.router.server.ServerState;
import gash.router.server.edges.EdgeInfo;
import gash.router.server.edges.EdgeMonitor;
import io.netty.channel.Channel;
import pipe.common.Common.AddNewNode;
import pipe.common.Common.Header;
import pipe.work.Work.WorkMessage;

public class RaftManager implements Runnable {
		private ServerState state;
		private int nodeId = -1;
		private int leaderId = -1;
		private String leaderHost = "";
		private int leaderPort = -1;
		public Channel clientChannel;

		private String selfHost;
		private int selfPort;
		private RoutingConf conf;
		private EdgeMonitor emon;

		private long timerStart = 0;
		// This servers states
		private volatile RaftState CurrentState;
		public RaftState Leader;
		public RaftState Candidate;
		public RaftState Follower;
		public RaftState Interface;

		private int heartBeatBase = 3000;
		private volatile long electionTimeout = 3000;
		private volatile long lastKnownBeat = 0;
		private Random rand;
		private int term = 0;
		// private int commitIndex = 0;

		public RaftManager(ServerState state) {
			this.state = state;
		}

		public void init() throws UnknownHostException {
			selfHost = Inet4Address.getLocalHost().getHostAddress();
			selfPort=state.getConf().getWorkPort();
			rand = new Random();
			
			Candidate = new CandidateState();
			Candidate.setManager(this);  
			
			Follower = new FollowerState();
			Follower.setManager(this);

			Leader =new LeaderState();
			Leader.setManager(this);
			
			Interface = new InterfaceState();
			Interface.setManager(this);
			
			this.conf = state.getConf();
			this.emon = state.getEmon();

			lastKnownBeat = System.currentTimeMillis();
			heartBeatBase = conf.getHeartbeatDt();
			nodeId = conf.getNodeId();

			randomizeElectionTimeout();
			electionTimeout += 1000;

			CurrentState = Follower;
			//CurrentState = Interface;
			
		}
		
		

		@Override
		public void run() {
			System.out.println("in raft");
			System.out.println("hearbeat initially is"+heartBeatBase);
			System.out.println("elec timeout initially is"+electionTimeout);
			
			while (true) {
				
				timerStart = System.currentTimeMillis();				
				System.out.println("in raftmanager, present state "+CurrentState);
				CurrentState.process();
			}

		}
		
		public synchronized void randomizeElectionTimeout() {

			int temp = rand.nextInt(heartBeatBase);
			temp = temp + heartBeatBase;
			electionTimeout = (long) temp;

			// System.out.println("Randomized Timeout value is : "+electionTimeout);
		}
		public synchronized void setElectionTimeout(long et) {
			electionTimeout = et;
		}
		public synchronized long getElectionTimeout() {
			return electionTimeout;
		}

		public synchronized int getHbBase() {
			return heartBeatBase;
		}
		public synchronized long getLastKnownBeat() {
			return lastKnownBeat;
		}

		public synchronized void setLastKnownBeat(long beatTime) {
			lastKnownBeat = beatTime;
		}
		public synchronized long getTimerStart() {
			return timerStart;
		}

		public synchronized void setTimerStart(long t) {
			timerStart = t;
		}
		
		public synchronized int getNodeId() {
			return nodeId;
		}
		public synchronized int getSelfPort() {
			return selfPort;
		}
		public synchronized String getSelfHost() {
			return selfHost;
		}	
		public synchronized int getLeaderId() {
			return leaderId;
		}
		public synchronized int getLeaderPort() {
			for (EdgeInfo ei : emon.getOutBoundEdges().map.values()) {				
				if (ei.isActive() && ei.getChannel() != null) {  					   
                   	if(ei.getRef()==leaderId)
                   		leaderPort=ei.getPort();
				}
			}
			return leaderPort;	
		}
		public synchronized String getLeaderHost() {
			for (EdgeInfo ei : emon.getOutBoundEdges().map.values()) {				
				if (ei.isActive() && ei.getChannel() != null) {  					   
                   	if(ei.getRef()==leaderId)
                   		leaderHost=ei.getHost();
				}
			}
			return leaderHost;			
		}
		public synchronized void setCurrentState(RaftState st) {
			CurrentState = st;
		}
		public synchronized RaftState getCurrentState() {
			return CurrentState;
		}
		
		public synchronized EdgeMonitor getEdgeMonitor()
		{
			return emon;
		}
		
		public synchronized void setTerm(int val)
		{
			term=val;
		}
		public synchronized int getTerm()
		{
			return term;
		}
		
		public synchronized void setLeaderId(int id)
		{
			leaderId=id;
		}



}
