package gash.router.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import gash.router.container.RoutingConf;
import gash.router.server.edges.EdgeInfo;
import gash.router.server.edges.EdgeList;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import pipe.common.Common.Chunk;
import pipe.common.Common.Failure;
import pipe.common.Common.Header;
import pipe.common.Common.ReadResponse;
import pipe.common.Common.Response;
import pipe.common.Common.TaskType;
import pipe.work.Work.WorkMessage;
import raft.RaftManager;
import raft.InterfaceState;
import routing.Pipe.CommandMessage;

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

	public CommandHandler(ServerState state, RoutingConf conf) {
		this.state = state;
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

				logger.info("ping from " + msg.getHeader().getNodeId() + " to " + msg.getHeader().getDestination());
				PrintUtil.printCommand(msg);

			} else if (msg.getRequest().hasRwb()) {
				state.getManager().setCmdChannel(channel);
				System.out.println("has write request");

				if (state.getManager().getCurrentState().getClass() == InterfaceState.class) {
					System.out.println("Write Req received. Leader : " + state.getManager().getLeaderId());
					InterfaceState.interfaceMessageQueue.put(msg);
					// state.getManager().Interface.interfaceMessageQueue.put(msg);
					// state.getEmon().sendCmdMessageToNode(msg,
					// state.getManager().getLeaderId());

				} 
				/*
				else if (state.getManager().getLeaderId() == state.getManager().getNodeId()) {
					state.getManager().getCurrentState().receivedLogToWrite(msg);
				}
				*/

			} else if (msg.getRequest().hasRrb()) {

				System.out.println("Read Req received. Leader : " + state.getManager().getLeaderId());

				if (state.getManager().getCurrentState().getClass() == InterfaceState.class) {
					InterfaceState.interfaceMessageQueue.put(msg);
				} else {
					System.out.println("request taken");

					Class.forName("com.mysql.jdbc.Driver");
					Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/mydb", "root", "abcd");
					PreparedStatement statement = con.prepareStatement("select * from filetable where filename = ?");
					statement.setString(1, msg.getRequest().getRrb().getFilename());
					ResultSet rs = statement.executeQuery();
					while (rs.next()) {
						Header.Builder hb = Header.newBuilder();
						hb.setNodeId(999);
						hb.setTime(System.currentTimeMillis());
						hb.setDestination(-1);

						Chunk.Builder chb = Chunk.newBuilder();
						chb.setChunkId(rs.getInt(4));
						ByteString bs = ByteString.copyFrom(rs.getBytes(5));
						System.out.println("byte string " + bs);
						chb.setChunkData(bs);
						chb.setChunkSize(rs.getInt(6));

						ReadResponse.Builder rrb = ReadResponse.newBuilder();
						rrb.setFilename(rs.getString(1));
						rrb.setChunk(chb);
						rrb.setNumOfChunks(rs.getInt(7));

						Response.Builder rb = Response.newBuilder();
						// request type, read,write,etc
						rb.setResponseType(TaskType.READFILE);
						rb.setReadResponse(rrb);
						CommandMessage.Builder cb = CommandMessage.newBuilder();
						// Prepare the CommandMessage structure
						cb.setHeader(hb);
						cb.setResponse(rb);
						channel.writeAndFlush(cb.build());
					}
				}
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