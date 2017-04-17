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
package gash.router.server;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gash.router.container.RoutingConf.RoutingEntry;
import gash.router.server.edges.EdgeInfo;
import gash.router.server.edges.EdgeList;
import gash.router.server.edges.EdgeMonitor;
//import handlerschian.BodyHandler;
/*import handlerschian.ErrorHandler;
import handlerschian.Handler;*/
//import handlerschian.HeartbeatHandler;
//import handlerschian.RequestVoteHandler;
//import handlerschian.VoteHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import pipe.common.Common.Failure;
import pipe.common.Common.Header;
import pipe.election.Election.LeaderStatus;
import pipe.work.Work.Heartbeat;
import pipe.work.Work.Task;
import pipe.work.Work.WorkMessage;
import pipe.work.Work.WorkState;
import raft.FollowerState;
import raft.InterfaceState;

/**
 * The message handler processes json messages that are delimited by a 'newline'
 * 
 * TODO replace println with logging!
 * 
 * @author gash
 * 
 */
public class WorkHandler extends SimpleChannelInboundHandler<WorkMessage> {
	protected static Logger logger = LoggerFactory.getLogger("work");

	protected ServerState state;
	protected boolean debug = false;
	// private Handler handler;
	private EdgeList outboundEdges;
	// plan to shift this to vote handler

	public WorkHandler(ServerState state) {
		if (state != null) {
			this.state = state;
			/*
			 * this.handler=new ErrorHandler(state); Handler heartbeatHandler=
			 * new HeartbeatHandler(state); Handler bodyHandler= new
			 * BodyHandler(state); Handler voteHandler= new VoteHandler(state);
			 * Handler reqVoteHandler= new RequestVoteHandler(state);
			 * 
			 * handler.setNext(heartbeatHandler);
			 * heartbeatHandler.setNext(bodyHandler);
			 * bodyHandler.setNext(voteHandler);
			 * voteHandler.setNext(reqVoteHandler);
			 */

		}

	}

	/**
	 * override this method to provide processing behavior. T
	 * 
	 * @param msg
	 */

	public void handleMessage(WorkMessage msg, Channel channel) {
		if (msg == null) {
			// TODO add logging
			System.out.println("ERROR: Unexpected content - " + msg);
			return;
		}

		// TODO How can you implement this without if-else statements?
		// USE HANDLERS
		try {
			// System.out.println("im printing work now using handlers chain");
			// handler.processWorkMessage(msg, channel);
			// System.out.println("im in try");
			if (state.getManager().getCurrentState().getClass() == InterfaceState.class) {
				if (msg.hasLeader()) {
					System.out.println(msg.getLeader().getLeaderId());
					state.getManager().setLeaderId(msg.getLeader().getLeaderId());
				} else if (msg.hasWhoIsLeader()) {

					Header.Builder hb = Header.newBuilder();
					hb.setNodeId(4);
					hb.setDestination(-1);
					hb.setTime(System.currentTimeMillis());

					LeaderStatus.Builder lb = LeaderStatus.newBuilder();
					lb.setLeaderId(state.getManager().getLeaderId());
					lb.setLeaderTerm(state.getManager().getTerm());

					WorkMessage.Builder wb = WorkMessage.newBuilder();
					wb.setHeader(hb);
					wb.setLeader(lb);
					wb.setSecret(10);
					state.getEmon().sendMessage(wb.build());
				}
			} else if (msg.hasReqvote()) {
				state.getManager().getCurrentState().onRequestVoteReceived(msg);
			} else if (msg.hasVote()) {
				state.getManager().getCurrentState().receivedVoteReply(msg);
			} else if (msg.hasLeader() && !msg.hasRequest()) {
				state.getManager().getCurrentState().receivedHeartBeat(msg);
				System.out.println("after has leader recv hb");
			} else if (msg.hasErr()) {
				Failure err = msg.getErr();
				logger.error("failure from " + msg.getHeader().getNodeId());
				PrintUtil.printFailure(err);
			} else if (msg.hasAddnewnode()) {
				state.getManager().getEdgeMonitor().createOutBoundIfNew(msg.getHeader().getNodeId(),
						msg.getAddnewnode().getHost(), msg.getAddnewnode().getPort());
			} else if (msg.getRequest().hasRwb()) {
				System.out.println("is it here in workhandler");
				state.getManager().getCurrentState().chunkReceived(msg);
			} else if (msg.getResponse().hasWriteResponse()) {
				System.out.println("got the log response from follower");
				state.getManager().getCurrentState().responseToChuckSent(msg);
			}

			/*
			 * else if (msg.hasTask()) { Task t = msg.getTask(); } else if
			 * (msg.hasState()) { WorkState s = msg.getState(); }else if
			 * (msg.hasBody()) { PrintUtil.printBody(msg.getBody()); }
			 */

		} catch (NullPointerException e) {
			logger.error("Null pointer has occured from work handler logic" + e.getMessage());
		} catch (Exception e) {
			// TODO add logging
			Failure.Builder eb = Failure.newBuilder();
			eb.setId(state.getConf().getNodeId());
			eb.setRefId(msg.getHeader().getNodeId());
			eb.setMessage(e.getMessage());
			WorkMessage.Builder rb = WorkMessage.newBuilder(msg);
			rb.setErr(eb);
			channel.write(rb.build());
		}

		System.out.flush();

	}

	/**
	 * a message was received from the server. Here we dispatch the message to
	 * the client's thread pool to minimize the time it takes to process other
	 * messages.
	 * 
	 * @param ctx
	 *            The channel the message was received from
	 * @param msg
	 *            The message
	 */
	@Override
	protected void channelRead0(ChannelHandlerContext ctx, WorkMessage msg) throws Exception {
		handleMessage(msg, ctx.channel());
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error("Unexpected exception from downstream.", cause);
		ctx.close();
	}

}