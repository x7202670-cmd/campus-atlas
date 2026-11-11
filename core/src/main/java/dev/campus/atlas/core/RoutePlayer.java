package dev.campus.atlas.core;

/** Service-owned state machine. The injected clock must be monotonic. */
public final class RoutePlayer {
    public interface Clock { long nowMillis(); }
    public enum State { IDLE, RUNNING, PAUSED, COMPLETED, ERROR }
    public static final class Snapshot {
        public final State state;
        public final boolean hasPlan, restored;
        public final long elapsedMillis, durationMillis, intervalMillis;
        public final double distanceMeters;
        public final Point position;
        public final String error;
        private Snapshot(State state, RoutePlan plan, long elapsed, Point point, String error, boolean restored) {
            this.state=state;hasPlan=plan!=null;this.restored=restored;
            elapsedMillis=elapsed;durationMillis=plan==null?0:plan.durationMillis;
            intervalMillis=plan==null?1000:plan.intervalMillis;
            distanceMeters=plan==null?0:plan.distanceAt(elapsed);position=point;this.error=error;
        }
    }
    private final Clock clock;
    private final LocationOutput output;
    private RoutePlan plan;
    private State state=State.IDLE;
    private long elapsed,anchor,publishedElapsed;
    private Point position;
    private String error="";
    private boolean restored=true;
    public RoutePlayer(Clock clock,LocationOutput output){this.clock=clock;this.output=output;}
    public synchronized void start(RoutePlan next) {
        if(state==State.RUNNING||state==State.PAUSED||!restored)throw new IllegalStateException("请先结束并还原当前任务");
        if(next==null)throw new IllegalArgumentException("路线不能为空");
        plan=next;elapsed=0;publishedElapsed=0;position=null;error="";state=State.RUNNING;restored=false;anchor=clock.nowMillis();
        tick();
    }
    public synchronized void tick() {
        if(state!=State.RUNNING)return;
        elapsed=currentElapsed();anchor=clock.nowMillis();
        try {
            Point next=plan.positionAt(elapsed);output.publish(next);position=next;publishedElapsed=elapsed;
            if(elapsed>=plan.durationMillis){output.reset();restored=true;state=State.COMPLETED;}
        } catch(Exception failure){failAndRestore(failure);}
    }
    /** Refresh a paused coordinate without advancing route time or distance. */
    public synchronized void hold() {
        if(state!=State.PAUSED||position==null)return;
        try {output.publish(position);}catch(Exception failure){failAndRestore(failure);}
    }
    public synchronized void pause() {
        if(state!=State.RUNNING)return;
        // A paused route holds the last published point, so its progress must
        // describe that same point rather than an unpublished future position.
        elapsed=publishedElapsed;state=State.PAUSED;
    }
    public synchronized void resume() {
        if(state!=State.PAUSED)throw new IllegalStateException("当前没有可继续的暂停任务");
        anchor=clock.nowMillis();state=State.RUNNING;tick();
    }
    public synchronized void stop(boolean reset) {
        if(state==State.RUNNING)elapsed=currentElapsed();
        state=State.PAUSED;
        if(reset) {
            try {output.reset();restored=true;}
            catch(Exception failure){state=State.ERROR;restored=false;error="还原失败："+detail(failure);throw failure;}
        }
        plan=null;elapsed=0;position=null;error="";state=State.IDLE;
        // reset=false hands ownership to a fixed point writer, not to a new route.
        if(!reset)restored=true;
    }
    public synchronized void systemFailure(String message) {
        if(state==State.RUNNING)elapsed=currentElapsed();
        failAndRestore(new IllegalStateException(message));
    }
    public synchronized void requireRecovery(String message) {state=State.ERROR;restored=false;error=message;}
    public synchronized Snapshot snapshot(){return new Snapshot(state,plan,state==State.RUNNING?currentElapsed():elapsed,position,error,restored);}
    private long currentElapsed(){return plan==null?0:Math.min(plan.durationMillis,elapsed+Math.max(0,clock.nowMillis()-anchor));}
    private void failAndRestore(Exception failure){
        state=State.ERROR;error=detail(failure);
        try {output.reset();restored=true;}
        catch(Exception resetFailure){restored=false;error+="；还原失败："+detail(resetFailure);}
    }
    private static String detail(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
}
