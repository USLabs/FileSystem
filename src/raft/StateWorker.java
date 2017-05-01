package raft;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingDeque;

import com.google.protobuf.ByteString;

import gash.router.server.edges.EdgeInfo;
import pipe.common.Common.Chunk;
import pipe.common.Common.Header;
import pipe.common.Common.ReadBody;
import pipe.common.Common.ReadResponse;
import pipe.common.Common.Request;
import pipe.common.Common.Response;
import pipe.common.Common.TaskType;
import pipe.work.Work.AskQueueSize;
import pipe.work.Work.WorkMessage;
import routing.Pipe.CommandMessage;

public class StateWorker extends Thread {

	RaftManager Manager;

	public boolean start = true;

	public boolean processRequest = true;

	public boolean startTracking = false;

	public long startTime;

	public boolean newReq = true;

	public StateWorker(RaftManager m) {
		Manager = m;
	}

	// New Request = true handling
	public void run() {
		boolean flag = true;
		int chunks = 0;
		while (true) {
			LinkedBlockingDeque<WorkMessage> readMessageQueue = Manager.getCurrentState().getMessageQueue();
			if (!readMessageQueue.isEmpty()) {
					try {
						if (Manager.getCurrentState().getClass() == LeaderState.class) {
							if (this.newReq) {
								System.out.println("Picked from Leader queue");
								if (!readMessageQueue.isEmpty()) {
									WorkMessage wm = readMessageQueue.take();
									LeaderState leader = (LeaderState) Manager.getCurrentState();
									System.out.println("NewReq: " + newReq);
									Manager.getEdgeMonitor().sendMessage(createQueueSizeRequest());
								// System.out.println("Queue Size Request sent
								// to" + ei.getRef());

									newReq = false;
								}
								else
								{
									continue;
								}
							} else {
								if (leader.workStealingNodes.size() != 0) {

									if (flag) {
										flag = false;
										// Query database
										try {

											System.out.println("Starting DB query for chunksize");

											Class.forName("com.mysql.jdbc.Driver");
											Connection con = DriverManager
													.getConnection("jdbc:mysql://localhost:3306/mydb", "root", "abcd");
											PreparedStatement statement = con.prepareStatement(
													"select numberofchunks from filetable where chunkid=0 && filename = ?");

											statement.setString(1, wm.getRequest().getRrb().getFilename());
											ResultSet rs = statement.executeQuery();

											while (rs.next()) {
												chunks = rs.getInt("numberofchunks");
												break;
											}
											System.out.println("Chunks = " + chunks);

										} catch (Exception e) {
											e.printStackTrace();
										}
									}

									// send to worker

									int chunkCount = 0;
									while (chunkCount < chunks) {
										for (int nodeId : leader.workStealingNodes) {
											EdgeInfo ei = Manager.getEdgeMonitor().getOutBoundEdges().map.get(nodeId);
											if (ei.isActive() && ei.getChannel() != null) {
												ei.getChannel().writeAndFlush(createReadReq(
														wm.getRequest().getRrb().getFilename(), chunkCount));
											}
											System.out.println("Read request sent to " + ei.getRef());
											chunkCount++;
										}
									}
									newReq = true;
									flag = true;
									chunks = 0;

								} else {
									if (startTracking && System.currentTimeMillis() - startTime > 7000) {
										// Read from leader only
										System.out.println("Read from Leader");

										fetchChunkFromLeader(wm);
										newReq = true;
										flag = true;
										chunks = 0;
									}
								}
							}
						}

						if (Manager.getCurrentState().getClass() == FollowerState.class) {
							if (Manager.getCurrentState().getMessageQueue().size() != 0) {
								FollowerState follower = (FollowerState) (Manager.getCurrentState());
								follower.fetchChunk(Manager.getCurrentState().getMessageQueue().take());
							}
						}
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				
			}
		}
	}

	public WorkMessage createQueueSizeRequest() {

		WorkMessage.Builder wbr = WorkMessage.newBuilder();
		Header.Builder hbr = Header.newBuilder();
		hbr.setDestination(-1);
		hbr.setTime(System.currentTimeMillis());

		AskQueueSize.Builder ask = AskQueueSize.newBuilder();
		ask.setAskqueuesize(true);

		// wbr.setHeader(hbr);
		wbr.setSecret(10);
		wbr.setAskqueuesize(ask);
		WorkMessage wm = wbr.build();
		return wm;
	}

	public synchronized void fetchChunk(WorkMessage msg) {
		try {
			Manager.randomizeElectionTimeout();
			Manager.setCurrentState(Manager.Follower);
			Manager.setLastKnownBeat(System.currentTimeMillis());
			Class.forName("com.mysql.jdbc.Driver");
			Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "root", "abcd");
			PreparedStatement statement = con
					.prepareStatement("select * from filetable where chunkid=? && filename = ?");
			statement.setLong(1, msg.getRequest().getRrb().getChunkId());
			statement.setString(2, msg.getRequest().getRrb().getFilename());
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {
				Header.Builder hb = Header.newBuilder();
				hb.setNodeId(Manager.getNodeId());
				hb.setTime(System.currentTimeMillis());
				hb.setDestination(-1);

				Chunk.Builder chb = Chunk.newBuilder();
				chb.setChunkId(rs.getInt(2));
				ByteString bs = ByteString.copyFrom(rs.getBytes(3));
				System.out.println("byte string " + bs);
				chb.setChunkData(bs);
				chb.setChunkSize(rs.getInt(4));

				ReadResponse.Builder rrb = ReadResponse.newBuilder();
				rrb.setFilename(rs.getString(1));
				rrb.setChunk(chb);
				rrb.setNumOfChunks(rs.getInt(5));

				Response.Builder rb = Response.newBuilder();
				// request type, read,write,etc
				rb.setResponseType(TaskType.RESPONSEREADFILE);
				rb.setReadResponse(rrb);
				WorkMessage.Builder cb = WorkMessage.newBuilder();
				// Prepare the CommandMessage structure
				cb.setHeader(hb);
				cb.setSecret(10);
				cb.setResponse(rb);
				int toNode = msg.getHeader().getNodeId();
				int fromNode = Manager.getNodeId();
				EdgeInfo ei = Manager.getEdgeMonitor().getOutBoundEdges().map.get(toNode);
				if (ei.isActive() && ei.getChannel() != null) {
					ei.getChannel().writeAndFlush(cb.build());
				}
			}
		} catch (Exception e) {
		}
	}

	public WorkMessage createReadReq(String fileName, int chunkId) {
		Header.Builder hb = Header.newBuilder();

		hb.setNodeId(Manager.getLeaderId());
		hb.setTime(System.currentTimeMillis());
		hb.setDestination(-1);

		// prepare the read body request Structure
		ReadBody.Builder rrb = ReadBody.newBuilder();
		rrb.setFilename(fileName);
		rrb.setChunkId(chunkId);

		Request.Builder rb = Request.newBuilder();
		rb.setRequestType(TaskType.REQUESTREADFILE);
		rb.setRrb(rrb);

		WorkMessage.Builder wb = WorkMessage.newBuilder();
		wb.setHeader(hb);
		wb.setSecret(10);
		wb.setRequest(rb);
		return wb.build();
	}

	public synchronized void fetchChunkFromLeader(WorkMessage msg) {
		try {
			Manager.randomizeElectionTimeout();
			Manager.setCurrentState(Manager.Follower);
			Manager.setLastKnownBeat(System.currentTimeMillis());
			Class.forName("com.mysql.jdbc.Driver");
			Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "root", "abcd");
			PreparedStatement statement = con.prepareStatement("select * from filetable where filename = ?");
			statement.setString(1, msg.getRequest().getRrb().getFilename());
			ResultSet rs = statement.executeQuery();
			while (rs.next()) {

				Header.Builder hb = Header.newBuilder();
				hb.setNodeId(Manager.getNodeId());
				hb.setTime(System.currentTimeMillis());
				hb.setDestination(-1);

				Chunk.Builder chb = Chunk.newBuilder();
				chb.setChunkId(rs.getInt(2));
				ByteString bs = ByteString.copyFrom(rs.getBytes(3));
				System.out.println("byte string " + bs);
				chb.setChunkData(bs);
				chb.setChunkSize(rs.getInt(4));

				ReadResponse.Builder rrb = ReadResponse.newBuilder();
				rrb.setFilename(rs.getString(1));
				rrb.setChunk(chb);
				rrb.setNumOfChunks(rs.getInt(5));

				Response.Builder rb = Response.newBuilder();
				// request type, read,write,etc
				rb.setResponseType(TaskType.RESPONSEREADFILE);
				rb.setReadResponse(rrb);
				CommandMessage.Builder cb = CommandMessage.newBuilder();
				// Prepare the CommandMessage structure
				cb.setHeader(hb);
				cb.setResponse(rb);
				Manager.getClientChannel().writeAndFlush(cb.build());
			}
		} catch (Exception e) {
		}
	}

}
