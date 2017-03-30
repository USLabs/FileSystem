package gash.router.server;

import gash.router.container.RoutingConf;
import gash.router.server.edges.EdgeMonitor;
import gash.router.server.tasks.TaskList;

public class ServerState {
    private RoutingConf conf;
    private EdgeMonitor emon;
    private TaskList tasks;
    private StateTypes stateType = StateTypes.follower;
    private long timeOut;
    private int currentLeader;
    private int currentTerm;

    public void setTimeOut(long to) {
        this.timeOut = to;
    }

    public long getTimeOut() {
        return timeOut;
    }

    public void setStateType(StateTypes st) {
        this.stateType = st;
    }

    public StateTypes getStateType() {
        return stateType;
    }

    public RoutingConf getConf() {
        return conf;
    }

    public void setCurrentLeader(int currentLeader) {
        this.currentLeader = currentLeader;
    }

    public int getCurrentLeader() {
        return currentLeader;
    }

    public void setCurrentTerm(int currentLeader) {
        this.currentTerm = currentTerm;
    }

    public int getCurrentTerm() {
        return currentTerm;
    }

    public void setConf(RoutingConf conf) {
        this.conf = conf;
    }

    public EdgeMonitor getEmon() {
        return emon;
    }

    public void setEmon(EdgeMonitor emon) {
        this.emon = emon;
    }

    public TaskList getTasks() {
        return tasks;
    }

    public void setTasks(TaskList tasks) {
        this.tasks = tasks;
    }

}
