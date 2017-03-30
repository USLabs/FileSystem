package chainofhandlers;


/**
 * @author Labhesh
 * @since 29 Mar,2017.
 */
import gash.router.server.MessageServer;
import gash.router.server.PrintUtil;
import gash.router.server.ServerState;
import io.netty.channel.Channel;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pipe.common.Common.Body;
import pipe.work.Work;
import pipe.work.Work.Heartbeat;
import routing.Pipe;

public class VoteHandler extends Handler {
    Logger logger = LoggerFactory.getLogger(BodyHandler.class);
    public int requiredVotes=1;
    Map<Integer,Integer> voteMap=new HashMap<Integer,Integer>();
    public VoteHandler(ServerState state) {
        super(state);
    }

    @Override
    public void processWorkMessage(Work.WorkMessage message, Channel channel) {
        if (message.hasVote()) {        	
        	System.out.println(" im handling vote");
        	/*@Override
            public void onVoteReceived(Work.WorkMessage workMessage, Channel channel) {*/
                
                int voterId = message.getVote().getVoterID();
                logger.info("Vote Received from "+voterId );
                int receivedCurrentTerm = message.getVote().getCurrentTerm();
                voteMap.put(voterId, receivedCurrentTerm);
                if (voteMap.size() >= requiredVotes) {
                    state.setCurrentLeader(state.getConf().getNodeId());
                }
                System.out.println("Present Leader is "+state.getCurrentLeader());
            //}
        	
        } else {
            //next.processWorkMessage(message, channel);
        	System.out.println("I honestly dont know where to go ");
        }
    }


    /*@Override
    public void processCommandMessage(Pipe.CommandMessage message, Channel channel) {
        if (message.hasDuty()) {
            server.onDutyMessage(message, channel);
        } else {
            next.processCommandMessage(message, channel);
        }
    }

    @Override
    public void processGlobalMessage(Global.GlobalMessage message, Channel channel) {
        if (message.getGlobalHeader().getDestinationId() == server.getGlobalConf().getClusterId()) {
            logger.info("I got back my request");
        } else {
            if (message.hasRequest()) {
                server.onGlobalDutyMessage(message, channel);
            } else {
                next.processGlobalMessage(message, channel);
            }
        }

    }
*/

}