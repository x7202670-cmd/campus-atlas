package dev.campus.atlas.core;
/** A future container adapter must fail explicitly until it owns a working guest session. */
public interface LocationOutput {
    String description();
    void publish(Point point);
    void reset();
    final class Preview implements LocationOutput {
        private Point last;
        public String description(){return "仅应用内预览 · 未改变系统或其他应用位置";}
        public void publish(Point point){last=point;}
        public void reset(){last=null;}
        public Point last(){return last;}
    }
}
