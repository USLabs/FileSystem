package raft;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;

import gash.router.server.PrintUtil;
import gash.router.server.edges.EdgeInfo;
import pipe.common.Common.Chunk;
import pipe.common.Common.Header;
import pipe.common.Common.Request;
import pipe.common.Common.TaskType;
import pipe.common.Common.WriteBody;
import pipe.election.Election.LeaderStatus;
import pipe.work.Work.Commit;
import pipe.work.Work.Heartbeat;
import pipe.work.Work.WorkMessage;
import pipe.work.Work.WorkState;
import routing.Pipe;
import routing.Pipe.CommandMessage;

public class LeaderState implements RaftState {
	private RaftManager Manager;
	LinkedBlockingDeque<WorkMessage> chunkMessageQueue = new LinkedBlockingDeque<WorkMessage>();
	LinkedBlockingDeque<WorkMessage> TemporaryMessageQueue = new LinkedBlockingDeque<WorkMessage>();
	Map<String, Map<Integer, Integer>> chunkResponseMap = new HashMap<String, Map<Integer, Integer>>();
	Map<Integer, Integer> nodeCountMap = new HashMap<Integer, Integer>();
	Map<String, Integer> commitMap = new HashMap<String, Integer>();
	Map<String, ArrayList<WorkMessage>> fileChunkMap = new HashMap<String, ArrayList<WorkMessage>>();

	double clusterSize = 1;

	@Override
	public synchronized void process() {
		// System.out.println("In leaders process method");
		try {
			// checking cluster size
			for (EdgeInfo ei : Manager.getEdgeMonitor().getOutBoundEdges().map.values()) {
				clusterSize = 1;
				if (ei.isActive() && ei.getChannel() != null) {
					clusterSize++;
				}
			}
			if (chunkMessageQueue.isEmpty()) {
				for (EdgeInfo ei : Manager.getEdgeMonitor().getOutBoundEdges().map.values()) {
					if (ei.isActive() && ei.getChannel() != null) {
						Manager.getEdgeMonitor().sendMessage(createHB());
						System.out.println("sent hb to" + ei.getRef());
						clusterSize++;
					}

				}
			}
			if (!chunkMessageQueue.isEmpty()) {
				System.out.println("before taking message " + chunkMessageQueue.size());
				for (EdgeInfo ei : Manager.getEdgeMonitor().getOutBoundEdges().map.values()) {
					if (ei.isActive() && ei.getChannel() != null) {
						Manager.getEdgeMonitor().sendMessage(createAppendandHeartbeat());
						System.out.println("after taking message queue size " + chunkMessageQueue.size());

					}
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	// CREATE HEARTBEAT
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

		LeaderStatus.Builder lb = LeaderStatus.newBuilder();
		lb.setLeaderId(Manager.getNodeId());
		lb.setLeaderTerm(Manager.getTerm());

		WorkMessage.Builder wb = WorkMessage.newBuilder();
		wb.setHeader(hb);
		wb.setBeat(bb);
		wb.setLeader(lb);
		wb.setSecret(10);
		return wb.build();
	}

	// creating heartbeat and appendentry
	public synchronized WorkMessage createAppendandHeartbeat() {
		if (!chunkMessageQueue.isEmpty()) {
			WorkMessage msg = null;
			try {
				msg = chunkMessageQueue.take();
				addToTemporaryQueue(msg);

			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			WorkState.Builder sb = WorkState.newBuilder();
			sb.setEnqueued(-1);
			sb.setProcessed(-1);

			Heartbeat.Builder bb = Heartbeat.newBuilder();
			bb.setState(sb);

			Header.Builder hb = Header.newBuilder();
			hb.setNodeId(Manager.getNodeId());
			hb.setDestination(-1);
			hb.setTime(System.currentTimeMillis());

			LeaderStatus.Builder lb = LeaderStatus.newBuilder();
			lb.setLeaderId(Manager.getNodeId());
			lb.setLeaderTerm(Manager.getTerm());
			Chunk.Builder chb = Chunk.newBuilder();
			chb.setChunkId(msg.getRequest().getRwb().getChunk().getChunkId());
			chb.setChunkData(msg.getRequest().getRwb().getChunk().getChunkData());
			chb.setChunkSize(msg.getRequest().getRwb().getChunk().getChunkSize());

			WriteBody.Builder wb = WriteBody.newBuilder();

			wb.setFilename(msg.getRequest().getRwb().getFilename());
			wb.setChunk(chb);
			wb.setNumOfChunks(msg.getRequest().getRwb().getNumOfChunks());

			Request.Builder rb = Request.newBuilder();
			// request type, read,write,etc
			rb.setRwb(wb);
			rb.setTaskType(TaskType.WRITEFILE);

			WorkMessage.Builder wbs = WorkMessage.newBuilder();
			wbs.setHeader(hb);
			wbs.setBeat(bb);
			wbs.setLeader(lb);
			wbs.setSecret(10);
			wbs.setRequest(rb);
			System.out.println("i am working fine from appendentry method");
			PrintUtil.printWork(wbs.build());

			return wbs.build();
		} else {
			return createHB();
		}

	}

	// adding to temporary queue
	public void addToTemporaryQueue(WorkMessage msg) {
		try {
			TemporaryMessageQueue.put(msg);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	// received hearbeat no need to implement here
	public synchronized void receivedHeartBeat(WorkMessage msg) {
		Manager.randomizeElectionTimeout();
		System.out.println("received hearbeat from the Leader: " + msg.getLeader().getLeaderId());
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

	public void receivedLogToWrite(WorkMessage wm) {
		System.out.println("reached leader now ");
		System.out.println("building work message");

		try {
			chunkMessageQueue.put(wm);
			System.out.println("size of queue is " + chunkMessageQueue.size());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("queue size is " + chunkMessageQueue.size());
		// PrintUtil.printWork(wm);
		// for(EdgeInfo
		// ei:Manager.getEdgeMonitor().getOutBoundEdges().map.values())
		// {
		// if(ei.getChannel()!=null& &ei.isActive())
		// {
		// Manager.getEdgeMonitor().sendMessage(wm);
		//
		// }
		// }

	}

	public WorkMessage buildWorkMessage(CommandMessage msg) {

		Header.Builder hb = Header.newBuilder();
		hb.setNodeId(Manager.getLeaderId());
		hb.setTime(System.currentTimeMillis());
		hb.setDestination(-1);

		Chunk.Builder chb = Chunk.newBuilder();
		chb.setChunkId(msg.getRequest().getRwb().getChunk().getChunkId());
		chb.setChunkData(msg.getRequest().getRwb().getChunk().getChunkData());
		chb.setChunkSize(msg.getRequest().getRwb().getChunk().getChunkSize());

		WriteBody.Builder wb = WriteBody.newBuilder();

		wb.setFilename(msg.getRequest().getRwb().getFilename());
		wb.setChunk(chb);
		wb.setNumOfChunks(msg.getRequest().getRwb().getNumOfChunks());

		Request.Builder rb = Request.newBuilder();
		// request type, read,write,etc
		rb.setRwb(wb);
		rb.setTaskType(TaskType.WRITEFILE);
		WorkMessage.Builder wbs = WorkMessage.newBuilder();
		// Prepare the CommandMessage structure

		wbs.setHeader(hb);
		wbs.setRequest(rb);
		wbs.setSecret(10);
		System.out.println("retunr fun");
		return wbs.build();
	}

	public void chunkReceived(WorkMessage msg) {
		return;
	}

	public void commitToDatabase(Map<String, ArrayList<WorkMessage>> fileChunkMap, String fileName, int numOfChunks) {
		try {
			Class.forName("com.mysql.jdbc.Driver");
			Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "root", "abcd");
			// create the mysql insert preparedstatement
			for (int j = 0; j < numOfChunks; j++) {
				System.out.println("Added chunk to DB " + j);
				String query = " insert into filetable (filename, chunkid, chunkdata, chunksize, numberofchunks)"
						+ " values (?, ?, ?, ?, ?)";
				PreparedStatement preparedStmt = con.prepareStatement(query);
				preparedStmt.setString(1, fileName);
				preparedStmt.setInt(2,
						fileChunkMap.get(fileName).get(j).getResponse().getWriteResponse().getChunk().getChunkId());
				preparedStmt.setBytes(3,
						(fileChunkMap.get(fileName).get(j).getResponse().getWriteResponse().getChunk().getChunkData())
								.toByteArray());
				preparedStmt.setInt(4,
						fileChunkMap.get(fileName).get(j).getResponse().getWriteResponse().getChunk().getChunkSize());
				preparedStmt.setInt(5, numOfChunks);
				preparedStmt.execute();
			}
			con.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public WorkMessage askFollowersToCommitFile(WorkMessage msg) {

		Commit.Builder cb = Commit.newBuilder();
		cb.setFilename(msg.getResponse().getWriteResponse().getFileName());
		cb.setNumOfChunks(msg.getResponse().getWriteResponse().getNumOfChunks());

		WorkMessage.Builder wbs = WorkMessage.newBuilder();
		wbs.setCommit(cb);
		wbs.setSecret(10);
		System.out.println("retunr fun");
		return wbs.build();
	}

	public void responseToChuckSent(WorkMessage msg) {
		System.out.println("in responsetochunk method");
		System.out.println("adding to chunk map");
		if (!fileChunkMap.containsKey(msg.getResponse().getWriteResponse().getFileName())) {
			fileChunkMap.put(msg.getResponse().getWriteResponse().getFileName(), new ArrayList<WorkMessage>());
		}
		fileChunkMap.get(msg.getResponse().getWriteResponse().getFileName()).add(msg);
		System.out.println("chunks for file" + msg.getResponse().getWriteResponse().getFileName() + " added to map");
		System.out.println(fileChunkMap.get(msg.getResponse().getWriteResponse().getFileName()).size());
		if (!chunkResponseMap.containsKey(msg.getResponse().getWriteResponse().getFileName())) {
			chunkResponseMap.put(msg.getResponse().getWriteResponse().getFileName(), new HashMap<Integer, Integer>());
		}
		if (!chunkResponseMap.get(msg.getResponse().getWriteResponse().getFileName())
				.containsKey(msg.getHeader().getNodeId())) {
			chunkResponseMap.get(msg.getResponse().getWriteResponse().getFileName()).put(msg.getHeader().getNodeId(),
					1);
		} else {
			int i = chunkResponseMap.get(msg.getResponse().getWriteResponse().getFileName())
					.get(msg.getHeader().getNodeId());
			chunkResponseMap.get(msg.getResponse().getWriteResponse().getFileName()).put(msg.getHeader().getNodeId(),
					++i);
			System.out.println("Node " + msg.getHeader().getNodeId() + " commited " + chunkResponseMap
					.get(msg.getResponse().getWriteResponse().getFileName()).get(msg.getHeader().getNodeId()));
		}
		System.out.println("chunk reponse maps value " + (chunkResponseMap
				.get(msg.getResponse().getWriteResponse().getFileName()).get(msg.getHeader().getNodeId())));

		if ((chunkResponseMap.get(msg.getResponse().getWriteResponse().getFileName())
				.get(msg.getHeader().getNodeId())) == msg.getResponse().getWriteResponse().getNumOfChunks()) {
			System.out.println("in committing now ");
			if (!commitMap.containsKey(msg.getResponse().getWriteResponse().getFileName())) {
				commitMap.put(msg.getResponse().getWriteResponse().getFileName(), 1);
				System.out.println(
						"i have commited, value " + commitMap.get(msg.getResponse().getWriteResponse().getFileName()));
			} else {
				int i = commitMap.get(msg.getResponse().getWriteResponse().getFileName());
				commitMap.put(msg.getResponse().getWriteResponse().getFileName(), i++);
				System.out.println("For this file " + msg.getResponse().getWriteResponse().getFileName()
						+ " commit count " + commitMap.get(msg.getResponse().getWriteResponse().getFileName()));
			}

			if (commitMap.get(msg.getResponse().getWriteResponse().getFileName()) >= (clusterSize / 2)) {
				System.out.println("going to add to db  : " + msg.getResponse().getWriteResponse().getFileName());
				// db add
				try {
					// commitToDatabase(fileChunkMap,msg.getResponse().getWriteResponse().getFileName(),fileChunkMap.get(msg.getResponse().getWriteResponse().getFileName()).size());

					// write message to send to follows to commit a particular
					// file
					System.out.println("completed db");
					Set<Integer> keys = chunkResponseMap.get(msg.getResponse().getWriteResponse().getFileName())
							.keySet();
					for (Integer key : keys) {
						if (key == msg.getHeader().getNodeId()) {
							EdgeInfo ei = Manager.getEdgeMonitor().getOutBoundEdges().map.get(key);
							if (ei.isActive() && ei.getChannel() != null) {
								System.out.println("Im sending request to commit to "
										+ msg.getRequest().getRwb().getChunk().getChunkId());
								ei.getChannel().writeAndFlush(askFollowersToCommitFile(msg));

							}
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		} else {
			// map list add

		}
	}

	@Override
	public void receivedCommitChunkMessage(WorkMessage msg) {
		// TODO Auto-generated method stub

	}

}
