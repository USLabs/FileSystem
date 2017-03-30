/**
 * @author Labhesh
 * @since 25 Mar,2017.
 */
package chainofhandlers;
import gash.router.server.MessageServer;
import gash.router.server.PrintUtil;
import gash.router.server.ServerState;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pipe.common.Common.Body;
import pipe.work.Work;
import pipe.work.Work.Heartbeat;
import routing.Pipe;

public class HeartbeatHandler extends Handler {
    Logger logger = LoggerFactory.getLogger(BodyHandler.class);
    public HeartbeatHandler(ServerState state) {
        super(state);
    }

    @Override
    public void processWorkMessage(Work.WorkMessage message, Channel channel) {
        if (message.hasBeat()) {
        	//Heartbeat hb = message.getBeat();
        	/*logger.debug("heartbeat from " + message.getHeader().getNodeId());*/
        	System.out.println(" im in beat");
        	PrintUtil.printWork(message);
        	logger.debug("heartbeat from " + message.getHeader().getNodeId());
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
