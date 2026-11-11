package dev.campus.atlas;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.ViewParent;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.TextureMapView;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.amap.api.maps.model.BitmapDescriptorFactory;

import dev.campus.atlas.core.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** AMap view wrapper that keeps app data in WGS84 and converts only at the map boundary. */
@SuppressLint("ViewConstructor") // Created programmatically with the current WGS84 center.
final class AtlasMapView extends TextureMapView {
    private static final float INITIAL_SELECTION_ZOOM = 18f;
    private final AMap controller;
    private Marker marker;
    private Marker selectionMarker;
    private final List<Marker> routeNodes = new ArrayList<>();
    private Polyline routeLine;
    AtlasMapView(Activity activity, Point initialCenter) {
        super(activity);
        onCreate(null);
        controller = getMap();
        controller.getUiSettings().setZoomControlsEnabled(true);
        controller.getUiSettings().setScaleControlsEnabled(true);
        controller.getUiSettings().setZoomGesturesEnabled(true);
        controller.getUiSettings().setScrollGesturesEnabled(true);
        controller.getUiSettings().setRotateGesturesEnabled(false);
        controller.getUiSettings().setTiltGesturesEnabled(false);
        setClickable(true);
        LatLng initialPosition = CoordinateTransform.toGcj02(initialCenter);
        controller.moveCamera(CameraUpdateFactory.newLatLngZoom(initialPosition, INITIAL_SELECTION_ZOOM));
    }

    void setPointTapListener(Consumer<Point> listener) {
        controller.setOnMapClickListener(point -> listener.accept(CoordinateTransform.toWgs84(point)));
    }

    void center(Point point, boolean animate) {
        LatLng position = CoordinateTransform.toGcj02(point);
        float zoom = controller.getCameraPosition() == null ? INITIAL_SELECTION_ZOOM : controller.getCameraPosition().zoom;
        if (zoom < 3f) zoom = INITIAL_SELECTION_ZOOM;
        if (animate) controller.animateCamera(CameraUpdateFactory.newLatLngZoom(position, zoom));
        else controller.moveCamera(CameraUpdateFactory.newLatLngZoom(position, zoom));
    }

    Point cameraCenterWgs84() {
        com.amap.api.maps.model.CameraPosition camera = controller.getCameraPosition();
        return camera == null ? new Point(30.263, 120.122) : CoordinateTransform.toWgs84(camera.target);
    }

    void showMarker(Point point, String title) {
        LatLng position = CoordinateTransform.toGcj02(point);
        if (marker == null) marker = controller.addMarker(new MarkerOptions().position(position).title(title).anchor(.5f,.5f).icon(BitmapDescriptorFactory.fromBitmap(nodeBitmap("",AtlasUi.BLUE))));
        else {
            marker.setPosition(position);
            marker.setTitle(title);
        }
    }

    void showSelection(Point point,String title){
        LatLng position=CoordinateTransform.toGcj02(point);
        if(selectionMarker==null)selectionMarker=controller.addMarker(new MarkerOptions().position(position).title(title).icon(BitmapDescriptorFactory.fromBitmap(pinBitmap())).anchor(.5f,1));
        else{selectionMarker.setPosition(position);selectionMarker.setTitle(title);}
    }

    private Bitmap nodeBitmap(String label,int color){
        int size=AtlasUi.dp(getContext(),30);Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);float s=size/30f;canvas.scale(s,s);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(0xffdbeafe);canvas.drawCircle(15,15,15,paint);paint.setColor(Color.WHITE);canvas.drawCircle(15,15,12,paint);paint.setColor(color);canvas.drawCircle(15,15,10,paint);paint.setColor(Color.WHITE);paint.setTextSize(10);paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(label,15,18.5f,paint);return bitmap;
    }
    private Bitmap pinBitmap(){
        int w=AtlasUi.dp(getContext(),30),h=AtlasUi.dp(getContext(),40);Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);c.scale(w/30f,h/40f);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(AtlasUi.INK);Path path=new Path();path.moveTo(15,39);path.cubicTo(10,30,2,23,2,15);path.cubicTo(2,-2,28,-2,28,15);path.cubicTo(28,23,20,30,15,39);c.drawPath(path,p);p.setColor(Color.WHITE);c.drawCircle(15,14,5,p);return bitmap;
    }

    void removeMarker() {
        if (marker != null) {
            marker.remove();
            marker = null;
        }
    }

    void setRoute(List<Point> points, int color, float widthPx) {
        for(Marker node:routeNodes)node.remove();routeNodes.clear();
        if (routeLine != null) {
            routeLine.remove();
            routeLine = null;
        }
        if (points == null || points.isEmpty()) return;
        List<LatLng> converted = new ArrayList<>(points.size());
        for (Point point : points) converted.add(CoordinateTransform.toGcj02(point));
        if(points.size()>1)routeLine = controller.addPolyline(new PolylineOptions().addAll(converted).color(color).width(widthPx));
        int count=points.size();if(count>1&&points.get(0).distanceTo(points.get(count-1))<.01)count--;
        for(int i=0;i<count;i++){if(count>100&&i!=0&&i!=count-1)continue;routeNodes.add(controller.addMarker(new MarkerOptions().position(converted.get(i)).anchor(.5f,.5f).icon(BitmapDescriptorFactory.fromBitmap(nodeBitmap(Integer.toString(i+1),i==0?AtlasUi.INK:color)))));}
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        ViewParent parent = getParent();
        if (parent != null && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)) {
            // TextureMapView forwards gestures to an internal texture child, so
            // onTouchEvent on this wrapper is not guaranteed to see ACTION_DOWN.
            // Disallow interception here, before ScrollView can steal a move.
            parent.requestDisallowInterceptTouchEvent(true);
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (parent != null && (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)) {
            parent.requestDisallowInterceptTouchEvent(false);
        }
        return handled;
    }
}
