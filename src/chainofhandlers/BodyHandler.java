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
import routing.Pipe;

public class BodyHandler extends Handler {
    public BodyHandler(ServerState state) {
		super(state);
		// TODO Auto-generated constructor stub
	}


	Logger logger = LoggerFactory.getLogger(BodyHandler.class);
    

    @Override
    public void processWorkMessage(Work.WorkMessage message, Channel channel) {
        if (message.hasBody()) {
        	Body bd=message.getBody();
        	PrintUtil.printBody(bd);
        } else {
            next.processWorkMessage(message, channel);
        }
    }
/*
    @Override
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
