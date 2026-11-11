package dev.campus.atlas.core;

/** Reset the old target before switching; preserve state when reset fails. */
public final class RoutedOutput implements LocationOutput {
    private final LocationOutput.Preview preview=new LocationOutput.Preview();
    private LocationOutput destination=preview;
    private Point last;
    public synchronized String description(){return destination.description();}
    public synchronized void publish(Point p){destination.publish(p);last=p;}
    public synchronized void reset(){destination.reset();last=null;}
    public synchronized Point last(){return last;}
    public synchronized void select(LocationOutput next){
        if(next==null)throw new IllegalArgumentException("位置输出目标不能为空");
        reset();destination=next;
    }
    public synchronized void preview(){select(preview);}
}
