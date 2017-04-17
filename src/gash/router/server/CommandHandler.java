package gash.router.server;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import gash.router.app.DemoApp;
import gash.router.client.CommConnection;
import gash.router.client.MessageClient;
import gash.router.container.RoutingConf;
import gash.router.server.edges.EdgeInfo;
import gash.router.server.edges.EdgeList;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import pipe.common.Common;
import pipe.common.Common.Chunk;
import pipe.common.Common.Failure;
import pipe.common.Common.Header;
import pipe.common.Common.ReadResponse;
import pipe.common.Common.Request;
import pipe.common.Common.Response;
import pipe.common.Common.TaskType;
import pipe.common.Common.WriteBody;
import pipe.work.Work.WorkMessage;
import raft.CandidateState;
import raft.FollowerState;
import raft.InterfaceState;
import raft.LeaderState;
import raft.RaftManager;
import routing.Pipe.CommandMessage;
import routing.Pipe.LeaderRoute;

/**
 * The message handler processes json messages that are delimited by a 'newline'
 * 
 * TODO replace println with logging!
 * 
 * @author gash
 * 
 */
public class CommandHandler extends SimpleChannelInboundHandler<CommandMessage> {
	protected static Logger logger = LoggerFactory.getLogger("cmd");
	protected RoutingConf conf;
	private RaftManager Manager;
	ServerState state;
	public CommandHandler(ServerState state,RoutingConf conf) {
		this.state=state;
		if (conf != null) {
			this.conf = conf;
		}
	}

	/**
	 * override this method to provide processing behavior. This implementation
	 * mimics the routing we see in annotating classes to support a RESTful-like
	 * behavior (e.g., jax-rs).
	 * 
	 * @param msg
	 */
	public void handleMessage(CommandMessage msg, Channel channel) {
		if (msg == null) {
			// TODO add logging
			System.out.println("ERROR: Unexpected content - " + msg);
			return;
		}

		try {
			// TODO How can you implement this without if-else statements?
			if (msg.hasPing()) {
				logger.info("ping from " + msg.getHeader().getNodeId()+" to "+msg.getHeader().getDestination());
				PrintUtil.printCommand(msg);
			}			
			else if (msg.getRequest().hasRwb()){
				if(state.getManager().getCurrentState().getClass()==InterfaceState.class){
					String host = state.getManager().getLeaderHost();
					int port = state.getManager().getLeaderPort();
					System.out.println("Sending to : "+host+" : "+port);
					state.getEmon().sendCmdMessageToNode(msg, host, port);
				}
			} else 
			if(msg.getRequest().hasRrb()){
				System.out.println("request taken");
				
				if(state.getManager().getCurrentState().getClass()==InterfaceState.class){
					String host = state.getManager().getLeaderHost();
					int port = state.getManager().getLeaderPort();
					System.out.println("Sending to : "+host+" : "+port);
					state.getEmon().sendCmdMessageToNode(msg, host, port);
				}
				
				if(state.getManager().getCurrentState().getClass()==LeaderState.class){
					//Distribute reading among other nodes					
				}
				
				if(state.getManager().getCurrentState().getClass()==FollowerState.class || state.getManager().getCurrentState().getClass()==CandidateState.class){
					//Read chunks from it's own database
				}
				
				
				Class.forName("com.mysql.jdbc.Driver");  
				Connection con=DriverManager.getConnection(  
				"jdbc:mysql://localhost:3306/mydb","root","abcd");  			    
				PreparedStatement statement = con.prepareStatement("select * from filetable where filename = ?");    
				statement.setString(1, msg.getRequest().getRrb().getFilename());    
				ResultSet rs = statement.executeQuery();  				  
				while(rs.next()){ 
					Header.Builder hb = Header.newBuilder();
					hb.setNodeId(999);
					hb.setTime(System.currentTimeMillis());
					hb.setDestination(-1);
					
					Chunk.Builder chb=Chunk.newBuilder();
					chb.setChunkId(rs.getInt(4));
					ByteString bs=ByteString.copyFrom(rs.getBytes(5));
					System.out.println("byte string "+bs);
					chb.setChunkData(bs);
					chb.setChunkSize(rs.getInt(6));
					
					ReadResponse.Builder rrb=ReadResponse.newBuilder();
					rrb.setFileId(rs.getLong(2));
					rrb.setFilename(rs.getString(1));
					rrb.setChunk(chb);
					rrb.setNumOfChunks(rs.getInt(7));
					
					Response.Builder rb = Response.newBuilder();
					//request type, read,write,etc				
					rb.setResponseType(TaskType.READFILE);
					rb.setReadResponse(rrb);
					CommandMessage.Builder cb = CommandMessage.newBuilder();
					// Prepare the CommandMessage structure
					cb.setHeader(hb);
					cb.setResponse(rb);		
					channel.writeAndFlush(cb.build());
				}  
			}
			else
			if (msg.hasWhoisleader()) {				
					System.out.println("asked for who is leader");	
					/*LeaderRoute.Builder lrb=LeaderRoute.newBuilder();
					lrb.setHost(Manager.getLeaderHost());
					lrb.setPort(Manager.getLeaderPort());
					CommandMessage.Builder cmb=CommandMessage.newBuilder();
					cmb.setLeaderroute(lrb);				 
					channel.writeAndFlush(cmb.build());*/
			}						
			else {
			}
			

		} catch (Exception e) {
			// TODO add logging
			Failure.Builder eb = Failure.newBuilder();
			eb.setId(conf.getNodeId());
			eb.setRefId(msg.getHeader().getNodeId());
			eb.setMessage(e.getMessage());
			CommandMessage.Builder rb = CommandMessage.newBuilder(msg);
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
	protected void channelRead0(ChannelHandlerContext ctx, CommandMessage msg) throws Exception {
		handleMessage(msg, ctx.channel());
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		logger.error("Unexpected exception from downstream.", cause);
		ctx.close();
	}

}