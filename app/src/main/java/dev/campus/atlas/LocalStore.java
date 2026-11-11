package dev.campus.atlas;
import android.content.Context;
import android.content.SharedPreferences;
import dev.campus.atlas.core.Point;
import dev.campus.atlas.core.TimetableParser;
import org.json.*;
import java.util.*;

final class LocalStore {
    static final int MAX_ROUTE_BACKUP_BYTES=64_000_000;
    static final class SavedRoute {
        final String id,name;final List<Point> points;
        SavedRoute(String id,String name,List<Point> points){this.id=id;this.name=name;this.points=points;}
    }
    final SharedPreferences prefs;
    LocalStore(Context c){prefs=c.getSharedPreferences("atlas",Context.MODE_PRIVATE);ensureRouteLibrary();}
    private JSONArray encodePoints(List<Point> points){JSONArray a=new JSONArray();for(Point p:points)a.put(new JSONArray(Arrays.asList(p.lat,p.lon)));return a;}
    private List<Point> decodePoints(JSONArray a){List<Point> out=new ArrayList<>();if(a==null)return out;for(int i=0;i<a.length();i++){try{JSONArray p=a.getJSONArray(i);out.add(new Point(p.getDouble(0),p.getDouble(1)));}catch(Exception ignored){}}return out;}
    private void ensureRouteLibrary(){
        if(prefs.contains("saved_routes"))return;
        List<Point> legacy=readLegacyRoute();String id=UUID.randomUUID().toString();JSONArray items=new JSONArray();
        try{items.put(new JSONObject().put("id",id).put("name","我的路线").put("points",encodePoints(legacy)));}catch(JSONException ignored){}
        prefs.edit().putString("saved_routes",items.toString()).putString("active_route",id).commit();
    }
    private List<Point> readLegacyRoute(){List<Point> points=new ArrayList<>();try{points=decodePoints(new JSONArray(prefs.getString("route","[]")));}catch(Exception ignored){}return points;}
    List<SavedRoute> routes(){
        List<SavedRoute> out=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString("saved_routes","[]"));for(int i=0;i<a.length();i++){try{JSONObject o=a.getJSONObject(i);String id=o.optString("id","");if(!id.isEmpty())out.add(new SavedRoute(id,o.optString("name","路线 "+(i+1)),decodePoints(o.optJSONArray("points")==null?new JSONArray():o.getJSONArray("points"))));}catch(Exception ignored){}}}catch(Exception ignored){}return out;
    }
    SavedRoute activeRoute(){String id=prefs.getString("active_route","");for(SavedRoute r:routes())if(r.id.equals(id))return r;List<SavedRoute> all=routes();return all.isEmpty()?new SavedRoute("","我的路线",readLegacyRoute()):all.get(0);}
    List<Point> route(){
        return new ArrayList<>(activeRoute().points);
    }
    void saveRoute(List<Point> points){
        prefs.edit().putString("route",encodePoints(points).toString()).apply();
        List<SavedRoute> all=routes();String id=prefs.getString("active_route","");JSONArray updated=new JSONArray();
        boolean found=false;
        try{
            for(SavedRoute r:all){
                if(r.id.equals(id))found=true;
                updated.put(new JSONObject().put("id",r.id).put("name",r.name).put("points",r.id.equals(id)?encodePoints(points):encodePoints(r.points)));
            }
            if(!found){
                if(id.isEmpty())id=UUID.randomUUID().toString();
                updated.put(new JSONObject().put("id",id).put("name","我的路线").put("points",encodePoints(points)));
                prefs.edit().putString("active_route",id).apply();
            }
        }catch(JSONException ignored){}
        prefs.edit().putString("saved_routes",updated.toString()).apply();
    }
    List<Point> createRoute(String name){
        String id=UUID.randomUUID().toString();List<SavedRoute> all=routes();JSONArray updated=new JSONArray();
        try{for(SavedRoute r:all)updated.put(new JSONObject().put("id",r.id).put("name",r.name).put("points",encodePoints(r.points)));updated.put(new JSONObject().put("id",id).put("name",name).put("points",new JSONArray()));}catch(JSONException ignored){}
        prefs.edit().putString("saved_routes",updated.toString()).putString("active_route",id).putString("route","[]").apply();return new ArrayList<>();
    }
    void saveAsRoute(String name,List<Point> points){
        List<SavedRoute> all=routes();JSONArray updated=new JSONArray();String id=UUID.randomUUID().toString();
        try{for(SavedRoute r:all)updated.put(new JSONObject().put("id",r.id).put("name",r.name).put("points",encodePoints(r.points)));updated.put(new JSONObject().put("id",id).put("name",name).put("points",encodePoints(points)));}catch(JSONException ignored){}
        prefs.edit().putString("saved_routes",updated.toString()).putString("active_route",id).putString("route",encodePoints(points).toString()).apply();
    }
    void renameRoute(String name){
        String id=prefs.getString("active_route","");JSONArray updated=new JSONArray();try{for(SavedRoute r:routes())updated.put(new JSONObject().put("id",r.id).put("name",r.id.equals(id)?name:r.name).put("points",encodePoints(r.points)));}catch(JSONException ignored){}prefs.edit().putString("saved_routes",updated.toString()).apply();
    }
    void renameRoute(String id,String name){
        JSONArray updated=new JSONArray();try{for(SavedRoute r:routes())updated.put(new JSONObject().put("id",r.id).put("name",r.id.equals(id)?name:r.name).put("points",encodePoints(r.points)));}catch(JSONException e){throw new IllegalArgumentException("路线名称保存失败",e);}prefs.edit().putString("saved_routes",updated.toString()).apply();
    }
    void activateRoute(String id){for(SavedRoute r:routes())if(r.id.equals(id)){prefs.edit().putString("active_route",id).putString("route",encodePoints(r.points).toString()).apply();return;}}
    void deleteRoute(String id){
        String active=prefs.getString("active_route","");if(id.equals(active)){deleteActiveRoute();return;}
        JSONArray updated=new JSONArray();try{for(SavedRoute r:routes())if(!r.id.equals(id))updated.put(new JSONObject().put("id",r.id).put("name",r.name).put("points",encodePoints(r.points)));}catch(JSONException ignored){}
        prefs.edit().putString("saved_routes",updated.toString()).apply();
    }
    String exportRoutes(){
        JSONObject root=new JSONObject();JSONArray items=new JSONArray();
        try{root.put("format","campus-atlas-routes-v1").put("activeRoute",prefs.getString("active_route",""));for(SavedRoute r:routes())items.put(new JSONObject().put("name",r.name).put("points",encodePoints(r.points)));root.put("routes",items);return root.toString(2);}catch(JSONException e){throw new IllegalStateException("无法生成路线备份",e);}
    }
    int importRoutes(String json){
        if(json==null||json.length()>MAX_ROUTE_BACKUP_BYTES)throw new IllegalArgumentException("文件为空或超过 64 MB");
        try{
            JSONObject root=new JSONObject(json);if(!"campus-atlas-routes-v1".equals(root.optString("format")))throw new IllegalArgumentException("不支持的路线备份格式");
            JSONArray incoming=root.getJSONArray("routes");if(incoming.length()<1)throw new IllegalArgumentException("备份中没有路线");
            List<JSONObject> prepared=new ArrayList<>();
            for(int i=0;i<incoming.length();i++){
                JSONObject item=incoming.getJSONObject(i);String name=item.getString("name").trim();if(name.isEmpty()||name.length()>40)throw new IllegalArgumentException("路线名称无效");
                JSONArray raw=item.getJSONArray("points");JSONArray points=new JSONArray();
                for(int j=0;j<raw.length();j++){JSONArray p=raw.getJSONArray(j);Point valid=new Point(p.getDouble(0),p.getDouble(1));points.put(new JSONArray(Arrays.asList(valid.lat,valid.lon)));}
                prepared.add(new JSONObject().put("id",UUID.randomUUID().toString()).put("name",name).put("points",points));
            }
            List<SavedRoute> existing=routes();JSONArray merged=new JSONArray();for(SavedRoute r:existing)merged.put(new JSONObject().put("id",r.id).put("name",r.name).put("points",encodePoints(r.points)));for(JSONObject item:prepared)merged.put(item);
            String selectedId=prepared.get(0).getString("id");JSONArray activePoints=prepared.get(0).getJSONArray("points");
            if(!prefs.edit().putString("saved_routes",merged.toString()).putString("active_route",selectedId).putString("route",activePoints.toString()).commit())throw new IllegalStateException("导入失败，无法保存路线库");
            return prepared.size();
        }catch(JSONException|IllegalArgumentException e){throw new IllegalArgumentException("路线备份读取失败："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()),e);}
    }
    void deleteActiveRoute(){
        List<SavedRoute> all=routes();String id=prefs.getString("active_route","");JSONArray updated=new JSONArray();SavedRoute first=null;
        try{for(SavedRoute r:all)if(!r.id.equals(id)){updated.put(new JSONObject().put("id",r.id).put("name",r.name).put("points",encodePoints(r.points)));if(first==null)first=r;}}catch(JSONException ignored){}
        if(first==null){first=new SavedRoute(UUID.randomUUID().toString(),"我的路线",new ArrayList<>());try{updated.put(new JSONObject().put("id",first.id).put("name",first.name).put("points",new JSONArray()));}catch(JSONException ignored){}}
        prefs.edit().putString("saved_routes",updated.toString()).putString("active_route",first.id).putString("route",encodePoints(first.points).toString()).apply();
    }
    JSONObject bindings(){try{return new JSONObject(prefs.getString("bindings","{}"));}catch(Exception e){return new JSONObject();}}
    List<String> manualPlaces(){List<String> names=new ArrayList<>();try{JSONArray list=new JSONArray(prefs.getString("manual_places","[]"));for(int i=0;i<list.length();i++)names.add(list.getString(i));}catch(Exception ignored){}return names;}
    void addManualPlace(String name){List<String> names=manualPlaces();if(!names.contains(name))names.add(name);prefs.edit().putString("manual_places",new JSONArray(names).toString()).apply();}
    void removeBinding(String name){JSONObject b=bindings();b.remove(name);List<String> manual=manualPlaces();manual.remove(name);prefs.edit().putString("bindings",b.toString()).putString("manual_places",new JSONArray(manual).toString()).apply();}
    void renameBinding(String old,String name,Point point)throws JSONException{if(!TimetableParser.placeKey(old).equals(TimetableParser.placeKey(name))&&bound(name)!=null)throw new IllegalArgumentException("已有同名地点，请使用其他名称");boolean manual=manualPlaces().contains(old);bind(name,point);if(!old.equals(name))removeBinding(old);if(manual)addManualPlace(name);}
    void bind(String name,Point p)throws JSONException{
        JSONObject b=bindings();String identity=TimetableParser.placeKey(name);
        List<String> aliases=new ArrayList<>();Iterator<String> keys=b.keys();
        while(keys.hasNext()){String key=keys.next();if(!name.equals(key)&&identity.equals(TimetableParser.placeKey(key)))aliases.add(key);}
        // Explicitly saving a reviewed coordinate also replaces its formatting aliases.
        for(String alias:aliases)b.remove(alias);
        b.put(name,new JSONArray().put(p.lat).put(p.lon));prefs.edit().putString("bindings",b.toString()).apply();
    }
    Point bound(String name){
        JSONObject all=bindings();
        if(all.has(name))return bindingPoint(all,name);
        String key=TimetableParser.placeKey(name);Point match=null;
        Iterator<String> keys=all.keys();
        while(keys.hasNext()){
            String old=keys.next();if(!key.equals(TimetableParser.placeKey(old)))continue;
            Point point=bindingPoint(all,old);if(point==null)continue;
            // Formatting aliases with conflicting coordinates need explicit review.
            if(match!=null&&match.distanceTo(point)>0.05)return null;
            match=point;
        }
        return match;
    }
    private Point bindingPoint(JSONObject all,String name){try{JSONArray a=all.getJSONArray(name);return new Point(a.getDouble(0),a.getDouble(1));}catch(Exception e){return null;}}
}
