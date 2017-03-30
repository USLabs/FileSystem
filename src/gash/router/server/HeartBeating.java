package gash.router.server;

import gash.router.server.edges.EdgeMonitor;

/**
 * Created by techmint on 3/29/17.
 */
public class HeartBeating extends Thread {
    private EdgeMonitor emon;
    private long heartBeatTimeout;
    public HeartBeating(ServerState state, long timeout) {
        emon = state.getEmon();
        heartBeatTimeout = timeout;
    }

    public void run() {
        while (true) {
            try {
                emon.sendMessage(emon.createHB());
                Thread.sleep(heartBeatTimeout);
            }
            catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    }

}
