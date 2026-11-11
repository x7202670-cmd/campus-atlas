package dev.campus.atlas;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import com.amap.api.maps.MapsInitializer;
import com.amap.api.maps.model.LatLng;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.core.ServiceSettings;
import com.amap.api.services.core.SuggestionCity;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import dev.campus.atlas.core.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int GREEN=AtlasUi.BLUE, INK=AtlasUi.INK, MUTED=AtlasUi.MUTED, BG=AtlasUi.BG;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private RoutedOutput output;
    private PlaybackRuntime runtime;
    private BlackBoxEngine engine;
    private LocalStore store;
    private ApkPresentationLoader apkPresentationLoader;
    private LinearLayout root,body,nav;
    private AtlasMapView map;
    private TextView locationLabel,progress;
    private TextView globalBadge,globalDetail,globalMode,selectedTitle,coordSummary,routeClock,routeMileage,routeGeometry;
    private Button routePrimary,routeStop,applyButton,stopButton;
    private Button[] routePresets;
    private ProgressBar routeProgress;
    private View recoveryBanner;
    private TextView feedbackLabel,systemStatus,systemSetupStatus,mediaPermissionStatus;
    private LinearLayout miniBar;
    private TextView miniTitle,miniDetail;
    private Button miniPause;
    private boolean scheduleOcrPanel,spacePermissionsPanel;
    private String selectedName="地图选点";
    private boolean instantApply;
    private LinearLayout savedPlacesList,candidateList;
    private AtlasMapView sheetMap;
    private Dialog activeSheet;
    private boolean deferredPageRefresh;
    private int candidateFilter;
    private String candidateKeyword="";
    private final Deque<List<Point>> routeUndo=new ArrayDeque<>(),routeRedo=new ArrayDeque<>();
    private EditText latInput,lonInput,paceInput,durationInput,intervalInput,ocrInput;
    private EditText placeSearchInput,placeCityInput;
    private volatile int poiSearchRequestId;
    private boolean poiSearching;
    private Button poiSearchButton,ocrButton;
    private CheckBox loopInput;
    private Point selected=new Point(30.263,120.122);
    private List<Point> route=new ArrayList<>();
    private int tab=0;
    private boolean pendingRouteStart;
    private boolean activityResumed;
    private Point lastDisplayedOutput;
    private long routeLibraryGeneration;
    private TimetableRecognition timetableRecognition;
    private final Runnable timetableChanged=()->{if(isDestroyed())return;if(tab==2)refreshPageWhenSafe();String result=timetableRecognition.takeMessage();if(!result.isEmpty())message(result);};
    private final Runnable refreshPlayback=new Runnable(){public void run(){
        syncImportedRoutes();
        updateProgress();Point p=output.last();
        if(!Objects.equals(p,lastDisplayedOutput)){
            if(p!=null)showMarker(p);
            else if(map!=null)map.removeMarker();
            lastDisplayedOutput=p;
        }
        updateGlobalStatus();
        handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);store=new LocalStore(this);apkPresentationLoader=new ApkPresentationLoader(this);runtime=PlaybackRuntime.get(this);route=store.route();routeLibraryGeneration=runtime.routeLibraryGeneration();output=runtime.output;engine=new BlackBoxEngine(this);tab=getIntent().hasExtra("open_tab")?getIntent().getIntExtra("open_tab",0):b!=null?b.getInt("tab",0):0;getIntent().removeExtra("open_tab");tab=Math.max(0,Math.min(3,tab));
        try{selected=new Point(Double.parseDouble(store.prefs.getString("lat","30.263")),Double.parseDouble(store.prefs.getString("lon","120.122")));}catch(Exception ignored){}
        selectedName=store.prefs.getString("selected_name","地图选点");instantApply=store.prefs.getBoolean("instant_apply",false);
        spaceSettingsExpanded=b!=null&&b.getBoolean("space_settings_expanded",false);
        timetableRecognition=TimetableRecognition.get(this);
        configureAmapSdk(false);
        render();
    }
    private int dp(int x){return Math.round(x*getResources().getDisplayMetrics().density);}
    private FrameLayout headerGlass(View content){
        FrameLayout shell=new FrameLayout(this);
        GradientDrawable surface=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xb8ffffff,0x68ffffff,0x90ffffff});
        surface.setCornerRadius(dp(28));shell.setBackground(surface);
        shell.addView(content,new FrameLayout.LayoutParams(-2,-2));
        GlassRimView rim=new GlassRimView(this);shell.getOverlay().add(rim);
        shell.addOnLayoutChangeListener((v,left,top,right,bottom,oldLeft,oldTop,oldRight,oldBottom)->rim.layout(0,0,right-left,bottom-top));
        return shell;
    }
    private LinearLayout column(){return AtlasUi.column(this);}
    private LinearLayout row(){return AtlasUi.row(this);}
    private GradientDrawable background(int color){return AtlasUi.shape(this,color,0,14);}
    private TextView text(String s,int size,int color){TextView t=AtlasUi.text(this,s,size,color);t.setPadding(0,dp(4),0,dp(4));return t;}
    private Button button(String label,Runnable action){return AtlasUi.button(this,label,0,action);}
    private Button primary(String label,Runnable action){return AtlasUi.button(this,label,1,action);}
    private Button danger(String label,Runnable action){return AtlasUi.button(this,label,2,action);}
    private LinearLayout card(String title){LinearLayout c=AtlasUi.panel(this);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(12),0,0);body.addView(c,p);if(!title.isEmpty()){c.addView(AtlasUi.strong(this,title,14,INK));AtlasUi.space(c,12);}return c;}
    private EditText input(LinearLayout parent,String label,String value){parent.addView(text(label,11,MUTED));boolean number=label.contains("纬度")||label.contains("经度")||label.contains("配速")||label.contains("时长")||label.contains("间隔");EditText e=AtlasUi.field(this,value,"",number);e.setContentDescription(label);parent.addView(e,new LinearLayout.LayoutParams(-1,dp(40)));return e;}
    private void twoButtons(LinearLayout parent,Button first,Button second){LinearLayout r=row();AtlasUi.equal(r,first,40);AtlasUi.equal(r,second,40);parent.addView(r);}
    private LinearLayout fold(LinearLayout parent,String title){LinearLayout content=column();content.setPadding(0,dp(10),0,0);content.setVisibility(View.GONE);Button toggle=button(title+"  ▾",()->{});toggle.setOnClickListener(v->{boolean show=content.getVisibility()!=View.VISIBLE;content.setVisibility(show?View.VISIBLE:View.GONE);toggle.setText(title+(show?"  ▴":"  ▾"));});parent.addView(toggle,new LinearLayout.LayoutParams(-1,-2));parent.addView(content);return content;}
    private void watch(EditText edit,Runnable changed){edit.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){changed.run();}public void afterTextChanged(Editable e){}});}
    private void message(String s){if(feedbackLabel==null){Toast.makeText(this,s,Toast.LENGTH_LONG).show();return;}feedbackLabel.setText(s+"  ×");feedbackLabel.setTextColor(GREEN);feedbackLabel.setBackgroundColor(AtlasUi.SOFT);feedbackLabel.setVisibility(View.VISIBLE);}
    private void error(Exception e){message(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());if(feedbackLabel!=null){feedbackLabel.setTextColor(AtlasUi.RED);feedbackLabel.setBackgroundColor(0xfffef2f2);}updateGlobalStatus();}
    private void render(){
        lastDisplayedOutput=output.last();
        if(map!=null){map.onPause();map.onDestroy();map=null;}
        progress=null;locationLabel=null;routeClock=null;routeMileage=null;routeGeometry=null;routeProgress=null;routePrimary=null;routeStop=null;applyButton=null;stopButton=null;selectedTitle=null;coordSummary=null;
        latInput=null;lonInput=null;paceInput=null;durationInput=null;intervalInput=null;loopInput=null;routePresets=null;ocrInput=null;placeSearchInput=null;placeCityInput=null;poiSearchButton=null;ocrButton=null;savedPlacesList=null;candidateList=null;systemStatus=null;mediaPermissionStatus=null;
        root=column();root.setBackgroundColor(BG);setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});root.requestApplyInsets();
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        LinearLayout header=column();header.setPadding(dp(16),dp(10),dp(16),dp(4));header.setBackgroundColor(BG);
        LinearLayout titleRow=row();LinearLayout targetChip=row();targetChip.setPadding(dp(12),dp(7),dp(12),dp(7));targetChip.setContentDescription("切换位置接收目标");targetChip.setOnClickListener(v->chooseTarget());LinearLayout targetText=column();targetText.addView(AtlasUi.text(this,"位置接收目标",10,MUTED));globalMode=AtlasUi.strong(this,"仅预览模式",12,INK);globalMode.setMaxLines(1);globalMode.setEllipsize(android.text.TextUtils.TruncateAt.END);int chipMax=Math.max(dp(90),(getResources().getDisplayMetrics().widthPixels-dp(32))*55/100-dp(54));globalMode.setMaxWidth(chipMax);targetText.addView(globalMode);targetChip.addView(targetText);TextView targetChevron=AtlasUi.text(this,"⌄",14,MUTED);targetChevron.setPadding(dp(8),0,0,0);targetChip.addView(targetChevron);titleRow.addView(headerGlass(targetChip),new LinearLayout.LayoutParams(-2,-2));View titleSpacer=new View(this);titleRow.addView(titleSpacer,new LinearLayout.LayoutParams(0,1,1));
        LinearLayout status=row();status.setPadding(dp(12),dp(8),dp(12),dp(8));globalBadge=AtlasUi.strong(this,"未输出",12,GREEN);status.addView(globalBadge);status.setOnClickListener(v->switchTab(runtime.player.snapshot().hasPlan?1:0));LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(-2,-2);st.setMargins(dp(8),0,0,0);titleRow.addView(headerGlass(status),st);header.addView(titleRow);
        globalDetail=AtlasUi.mono(this,"选择一个地点开始",11,MUTED);globalDetail.setSingleLine(true);globalDetail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        String[] titles={"定位控制","路线回放","课表地点","应用空间"};TextView pageTitle=AtlasUi.strong(this,titles[tab],14,INK);pageTitle.setPadding(dp(2),dp(8),0,dp(3));header.addView(pageTitle);
        LinearLayout recovery=column();recovery.setPadding(0,dp(10),0,0);recovery.addView(text("还原尚未确认成功，请先处理后再开始新任务。",12,AtlasUi.RED));recovery.addView(danger("重试还原",()->{try{stopPlayer(true);render();}catch(Exception e){error(e);}}));recoveryBanner=recovery;header.addView(recovery);
        root.addView(header);
        feedbackLabel=text("",12,GREEN);feedbackLabel.setPadding(dp(18),dp(9),dp(18),dp(9));feedbackLabel.setVisibility(View.GONE);feedbackLabel.setOnClickListener(v->feedbackLabel.setVisibility(View.GONE));root.addView(feedbackLabel);
        FrameLayout contentFrame=new FrameLayout(this);root.addView(contentFrame,new LinearLayout.LayoutParams(-1,0,1));ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);body=column();body.setPadding(dp(14),0,dp(14),dp(86));scroll.addView(body);contentFrame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));LinearLayout bottomOverlay=column();bottomOverlay.setClipChildren(false);contentFrame.setClipChildren(false);contentFrame.addView(bottomOverlay,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        if(tab==0)locationPage();else if(tab==1)routePage();else if(tab==2)timetablePage();else containerPage();
        miniBar=row();miniBar.setPadding(dp(12),dp(6),dp(8),dp(6));miniBar.setBackground(AtlasUi.shape(this,INK,0,16));LinearLayout miniInfo=column();miniTitle=AtlasUi.strong(this,"正在输出",12,Color.WHITE);miniDetail=AtlasUi.mono(this,"",10,0xffd2d5ce);miniInfo.addView(miniTitle);miniInfo.addView(miniDetail);miniInfo.setOnClickListener(v->switchTab(runtime.player.snapshot().hasPlan?1:0));AtlasUi.weight(miniBar,miniInfo);miniPause=button("暂停",()->sendRouteAction(runtime.player.snapshot().state==RoutePlayer.State.PAUSED?RoutePlaybackService.RESUME:RoutePlaybackService.PAUSE));miniBar.addView(miniPause,new LinearLayout.LayoutParams(-2,dp(42)));Button miniStop=danger("停止并还原",()->{try{stopPlayer(true);message("已停止输出并还原");}catch(Exception e){error(e);}});LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-2,dp(42));mp.setMargins(dp(6),0,0,0);miniBar.addView(miniStop,mp);LinearLayout.LayoutParams mb=new LinearLayout.LayoutParams(-1,-2);mb.setMargins(dp(14),0,dp(14),dp(6));bottomOverlay.addView(miniBar,mb);
        nav=row();nav.setPadding(dp(7),dp(6),dp(7),dp(6));GradientDrawable glass=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x55ffffff,0x20ffffff,0x40ffffff});glass.setCornerRadius(dp(28));
        final int navGreen=0xff315e48;String[] tabs={"定位","路线","课表","空间"},icons={"location","route","schedule","space"};for(int i=0;i<tabs.length;i++){final int target=i;LinearLayout item=column();item.setGravity(Gravity.CENTER);item.addView(AtlasUi.icon(this,icons[i],i==tab?navGreen:MUTED,20));AtlasUi.space(item,3);TextView navLabel=AtlasUi.strong(this,tabs[i],11,i==tab?navGreen:MUTED);navLabel.setGravity(Gravity.CENTER);item.addView(navLabel);item.setOnTouchListener((v,event)->{int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);v.animate().cancel();v.animate().scaleX(.94f).scaleY(.94f).setDuration(100).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){v.animate().cancel();v.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();}return false;});item.setOnClickListener(v->{if(target!=tab)switchTab(target);});item.setContentDescription(tabs[i]);item.setClickable(true);item.setFocusable(true);item.setSelected(i==tab);AtlasUi.equal(nav,item,50);}LinearLayout tintedTabs=row();tintedTabs.setPadding(dp(7),dp(6),dp(7),dp(6));tintedTabs.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);for(int i=0;i<tabs.length;i++){LinearLayout copy=column();copy.setGravity(Gravity.CENTER);copy.addView(AtlasUi.icon(this,icons[i],navGreen,20));AtlasUi.space(copy,3);TextView label=AtlasUi.strong(this,tabs[i],11,navGreen);label.setGravity(Gravity.CENTER);copy.addView(label);AtlasUi.equal(tintedTabs,copy,50);}LiquidNavLayout navShell=new LiquidNavLayout(this,tab,scroll,tintedTabs,nav,this::switchTab);GlassBackdrop backdrop=new GlassBackdrop(this,scroll);navShell.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));View glassSurface=new View(this);glassSurface.setBackground(glass);navShell.addView(glassSurface,new FrameLayout.LayoutParams(-1,-1));navShell.addView(new GlassRimView(this),new FrameLayout.LayoutParams(-1,-1));navShell.addRestIndicator();navShell.addView(nav,new FrameLayout.LayoutParams(-1,dp(62)));navShell.addLens();navShell.setElevation(dp(7));scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->backdrop.invalidate());LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(-1,dp(62));navParams.setMargins(dp(14),0,dp(14),dp(10));bottomOverlay.addView(navShell,navParams);updateGlobalStatus();
    }
    private void switchTab(int target){saveForm();tab=target;render();}
    private boolean isSystemMode(){return SystemLocationOutput.DESCRIPTION.equals(output.description());}
    private boolean isPreviewMode(){return output.description().startsWith("仅应用内预览");}
    private String targetLabel(){String d=output.description();if(isSystemMode())return SystemLocationOutput.DESCRIPTION;return d.startsWith("容器位置输出")?d.replace("容器位置输出 → ","").replace("（接收效果待实测）",""):"仅预览模式";}
    private void updateGlobalStatus(){
        if(globalBadge==null)return;RoutePlayer.Snapshot s=runtime.player.snapshot();boolean unresolved=s.state==RoutePlayer.State.ERROR&&!s.restored;
        String label="未输出",detail="选择一个地点开始";int color=GREEN;
        if(unresolved){label="还原异常";detail=s.error;color=AtlasUi.RED;}
        else if(!runtime.serviceError.isEmpty()){label="输出请求失败";detail=runtime.serviceError;color=AtlasUi.RED;}
        else if(s.state==RoutePlayer.State.RUNNING||s.state==RoutePlayer.State.PAUSED){label=s.state==RoutePlayer.State.RUNNING?"路线播放中":"路线已暂停";detail=clock(s.elapsedMillis/1000)+" / "+clock(s.durationMillis/1000);}
        else if(output.last()!=null){label=isSystemMode()?(runtime.systemLocation.verified()?"系统缓存匹配":"等待当前缓存"):isPreviewMode()?"仅本机预览":"已写入容器";detail=coordinate(output.last());}
        else if(s.state==RoutePlayer.State.COMPLETED){label="已完成 · 已还原";}
        globalBadge.setText(label);globalBadge.setTextColor(color);globalDetail.setText(detail);String target=targetLabel();globalMode.setText(apkPresentationLoader.cachedName(target));recoveryBanner.setVisibility(unresolved?View.VISIBLE:View.GONE);
        if(miniBar!=null){boolean active=s.state==RoutePlayer.State.RUNNING||s.state==RoutePlayer.State.PAUSED||output.last()!=null;boolean showMini=active&&!unresolved;miniBar.setVisibility(showMini?View.VISIBLE:View.GONE);int bottomInset=dp(showMini?150:86);if(body!=null&&body.getPaddingBottom()!=bottomInset)body.setPadding(dp(14),0,dp(14),bottomInset);miniTitle.setText(s.state==RoutePlayer.State.RUNNING?"路线播放中":s.state==RoutePlayer.State.PAUSED?"路线已暂停":"正在输出固定位置");miniDetail.setText(s.hasPlan?clock(s.elapsedMillis/1000)+" / "+clock(s.durationMillis/1000):output.last()!=null?coordinate(output.last()):"");miniPause.setVisibility(s.state==RoutePlayer.State.RUNNING||s.state==RoutePlayer.State.PAUSED?View.VISIBLE:View.GONE);miniPause.setText(s.state==RoutePlayer.State.PAUSED?"继续":"暂停");}
        if(applyButton!=null)applyButton.setEnabled(!unresolved);if(stopButton!=null)stopButton.setEnabled(output.last()!=null||s.hasPlan||unresolved);
        if(systemStatus!=null)systemStatus.setText(isSystemMode()?runtime.systemLocation.status()+"\n"+runtime.systemLocation.diagnostics():isPreviewMode()?"当前仅预览，手机系统位置不会改变":"当前输出到容器应用，手机系统位置不变");
        if(locationLabel!=null){locationLabel.setVisibility(isPreviewMode()?View.GONE:View.VISIBLE);if(!isPreviewMode())locationLabel.setText(isSystemMode()?runtime.systemLocation.status():"坐标仅发送到当前容器目标");}
    }
    private String coordinate(Point p){return String.format(Locale.US,"%.6f, %.6f",p.lat,p.lon);}
    private String clock(long seconds){return String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60);}
    private boolean readyForNewOutput(){if(runtime.player.snapshot().state==RoutePlayer.State.ERROR&&!runtime.player.snapshot().restored){message("请先重试还原当前任务");return false;}return true;}
    private void saveForm(){
        if(tab==0&&placeSearchInput!=null&&placeCityInput!=null)store.prefs.edit().putString("place_search",placeSearchInput.getText().toString()).putString("place_search_city",placeCityInput.getText().toString()).apply();
        if(tab==1&&paceInput!=null)store.prefs.edit().putString("pace",paceInput.getText().toString()).putString("duration",durationInput.getText().toString()).putString("interval",intervalInput.getText().toString()).putBoolean("loop",loopInput.isChecked()).apply();
        if(tab==2&&ocrInput!=null)store.prefs.edit().putString("ocr",ocrInput.getText().toString()).apply();
    }
    private LinearLayout addMap(boolean editRoute){
        LinearLayout mapPanel=card("");mapPanel.setPadding(0,0,0,0);mapPanel.setClipToOutline(true);
        if(!hasAmapKey()){
            mapPanel.setPadding(dp(16),dp(16),dp(16),dp(16));mapPanel.addView(text("地图未配置 Key（已安全脱敏）",16,INK));mapPanel.addView(text("本开源发布包未内置任何私有 Key。您可以配置您自己的高德 Key，或直接通过下方经纬度输入框精确选点。",12,MUTED));AtlasUi.space(mapPanel,8);mapPanel.addView(primary("配置高德 Key",this::showKeyConfigDialog));return mapPanel;
        }
        if(!store.prefs.getBoolean("amap_privacy_agreed",false)){
            mapPanel.setPadding(dp(16),dp(16),dp(16),dp(16));mapPanel.addView(text("启用高德地图",16,INK));mapPanel.addView(text("阅读地图与隐私说明后，即可搜索和地图选点。",12,MUTED));mapPanel.addView(primary("查看并启用地图",this::showAmapConsent));return mapPanel;
        }
        MapsInitializer.updatePrivacyShow(this,true,true);
        MapsInitializer.updatePrivacyAgree(this,true);
        Point initialCenter=editRoute&&!route.isEmpty()?route.get(0):selected;map=new AtlasMapView(this,initialCenter);
        FrameLayout viewport=new FrameLayout(this);viewport.addView(map,new FrameLayout.LayoutParams(-1,-1));mapPanel.addView(viewport,new LinearLayout.LayoutParams(-1,dp(editRoute?235:245)));
        Button recenter=button(editRoute?"回到起点":"回到选点",()->map.center(editRoute&&!route.isEmpty()?route.get(0):selected,true));recenter.setTextSize(11);FrameLayout.LayoutParams rp=new FrameLayout.LayoutParams(dp(98),dp(40),Gravity.TOP|Gravity.END);rp.setMargins(0,dp(10),dp(10),0);viewport.addView(recenter,rp);
        map.setPointTapListener(p->{
            if(editRoute){if(routeLocked())return;pushRouteUndo();route.add(p);routeChanged();}
            else choosePoint(p,"地图选点",false);
        });
        if(editRoute)drawRoute();else map.showSelection(selected,selectedName);if(output.last()!=null)showMarker(output.last());
        if(activityResumed)map.onResume();
        LinearLayout hintRow=row();TextView hint=text(editRoute?"  点击添加路线点 · 双指缩放 · 直线段连接":"  轻点选点 · 拖动平移 · 双指缩放",11,MUTED);hint.setPadding(dp(10),dp(9),dp(10),dp(9));AtlasUi.weight(hintRow,hint);TextView keySetting=AtlasUi.strong(this,"Key设置",11,GREEN);keySetting.setPadding(dp(10),dp(9),dp(10),dp(9));keySetting.setOnClickListener(v->showKeyConfigDialog());hintRow.addView(keySetting);mapPanel.addView(hintRow);return mapPanel;
    }
    private boolean hasAmapKey(){
        String customKey=store.prefs.getString("custom_amap_key","").trim();
        if(!customKey.isEmpty())return true;
        try{
            android.content.pm.ApplicationInfo info=getPackageManager().getApplicationInfo(getPackageName(),android.content.pm.PackageManager.GET_META_DATA);
            String key=info.metaData==null?null:info.metaData.getString("com.amap.api.v2.apikey");
            return key!=null&&!key.trim().isEmpty()&&!key.contains("replace_with_your_amap_key");
        }catch(Exception ignored){return false;}
    }
    private String getAmapKey(){
        String customKey=store.prefs.getString("custom_amap_key","").trim();
        if(!customKey.isEmpty())return customKey;
        try{
            android.content.pm.ApplicationInfo info=getPackageManager().getApplicationInfo(getPackageName(),android.content.pm.PackageManager.GET_META_DATA);
            String key=info.metaData==null?null:info.metaData.getString("com.amap.api.v2.apikey");
            if(key!=null&&!key.trim().isEmpty()&&!key.contains("replace_with_your_amap_key")){
                return key.trim();
            }
        }catch(Exception ignored){}
        return "";
    }
    private void showKeyConfigDialog(){
        EditText input=new EditText(this);
        input.setHint("粘贴高德 Android Key");
        input.setText(store.prefs.getString("custom_amap_key",""));
        input.setSingleLine(true);
        FrameLayout container=new FrameLayout(this);
        container.setPadding(dp(20),dp(10),dp(20),0);
        container.addView(input);
        new AlertDialog.Builder(this)
            .setTitle("配置高德地图 API Key")
            .setMessage("Key 仅保存在本机私有存储中。配置后将立即生效。")
            .setView(container)
            .setNegativeButton("取消",null)
            .setNeutralButton("清除 Key",(d,w)->{
                store.prefs.edit().remove("custom_amap_key").apply();
                render();
            })
            .setPositiveButton("保存并生效",(d,w)->{
                String k=input.getText().toString().trim();
                store.prefs.edit().putString("custom_amap_key",k).apply();
                configureAmapSdk(false);
                render();
            }).show();
    }
    private void showAmapConsent(){
        ScrollView scroll=new ScrollView(this);LinearLayout content=column();content.setPadding(dp(22),0,dp(22),0);
        content.addView(text("行迹空间使用高德 Android 地图 SDK 显示地图。根据设备、系统与实际启用能力，SDK 可能在本机采集经纬度、搜索词、网络/Wi-Fi/基站/传感器等设备信息、OAID、应用信息及设备和系统参数，以提供地图服务。具体范围以高德隐私政策为准。\n\n本应用保存的选点与路线保留在本机。你可以拒绝，之后仍可手动输入坐标。",14,INK));
        scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        new AlertDialog.Builder(this).setTitle("地图与隐私说明").setView(scroll)
            .setNegativeButton("暂不启用",(d,w)->{})
            .setNeutralButton("查看高德隐私政策",(d,w)->{
                try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://lbs.amap.com/pages/privacy")));}
                catch(Exception e){message("无法打开高德隐私政策页面");}
            })
            .setPositiveButton("同意并启用地图",(d,w)->{
                store.prefs.edit().putBoolean("amap_privacy_agreed",true).apply();
                configureAmapSdk(false);render();
            }).show();
    }
    private void showMarker(Point p){if(map!=null)map.showMarker(p,"当前输出");}
    private void select(Point p){selected=p;store.prefs.edit().putString("lat",Double.toString(p.lat)).putString("lon",Double.toString(p.lon)).putString("selected_name",selectedName).apply();if(map!=null)map.showSelection(p,selectedName);if(selectedTitle!=null)selectedTitle.setText(selectedName);if(coordSummary!=null)coordSummary.setText(coordinate(p));if(latInput!=null&&tab==0){latInput.setText(String.format(Locale.US,"%.6f",p.lat));lonInput.setText(String.format(Locale.US,"%.6f",p.lon));}}
    private void choosePoint(Point p,String name,boolean animate){selectedName=name;select(p);if(animate&&map!=null)map.center(p,true);if(instantApply&&!routeIsActive()&&readyForNewOutput()){try{applyFixed(p);}catch(Exception e){error(e);}}}
    private void applyFixed(Point p){
        if(!readyForNewOutput())return;select(p);
        if(isSystemMode())FixedLocationService.start(this,p);
        else{stopPlayer(false);output.publish(p);showMarker(p);}
        updateGlobalStatus();
    }
    private void applyCurrentPosition(){try{Point p=new Point(Double.parseDouble(latInput.getText().toString().trim()),Double.parseDouble(lonInput.getText().toString().trim()));Runnable apply=()->{try{applyFixed(p);if(map!=null)map.center(p,true);message(isSystemMode()?"已请求应用位置，请查看定位状态":isPreviewMode()?"已预览坐标，手机系统位置未改变":"已写入容器位置接口");}catch(Exception e){error(e);}};if(runtime.player.snapshot().hasPlan)new AlertDialog.Builder(this).setTitle("切换为固定位置？").setMessage("当前路线将结束，随后应用选中的固定坐标。").setNegativeButton("取消",null).setPositiveButton("应用",(d,w)->apply.run()).show();else apply.run();}catch(Exception e){error(e);}}
    private void drawRoute(){if(map!=null){List<Point> shown=new ArrayList<>(route);if((loopInput==null?store.prefs.getBoolean("loop",true):loopInput.isChecked())&&shown.size()>1)shown.add(shown.get(0));map.setRoute(shown,GREEN,dp(4));}}
    private void locationPage(){
        LinearLayout mode=card("手机系统定位");systemStatus=text("",12,MUTED);mode.addView(systemStatus);mode.addView(isSystemMode()?button("检查授权 / 设置",this::showSystemSetup):primary("启用系统模拟定位",this::chooseSystemMode));
        LinearLayout mapPanel=addMap(false);
        if(store.bindings().length()>0){HorizontalScrollView quickScroll=new HorizontalScrollView(this);quickScroll.setHorizontalScrollBarEnabled(false);LinearLayout quick=row();quick.setPadding(dp(10),dp(3),dp(10),dp(3));Iterator<String> keys=store.bindings().keys();int count=0;while(keys.hasNext()&&count++<4){String name=keys.next();Button chip=button(name,()->{Point p=store.bound(name);if(p!=null)choosePoint(p,name,true);});chip.setTextSize(11);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-2,dp(40));qp.setMargins(0,0,dp(5),0);quick.addView(chip,qp);}quickScroll.addView(quick);mapPanel.addView(quickScroll);}
        LinearLayout search=row();search.setPadding(dp(10),dp(8),dp(10),dp(12));
        placeCityInput=AtlasUi.field(this,store.prefs.getString("place_search_city","杭州"),"全国",false);placeCityInput.setTextSize(12);placeCityInput.setGravity(Gravity.CENTER);search.addView(placeCityInput,new LinearLayout.LayoutParams(dp(65),dp(44)));
        placeSearchInput=AtlasUi.field(this,store.prefs.getString("place_search",""),"搜索楼宇、场馆或地址",false);placeSearchInput.setTextSize(12);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(44),1);sp.setMargins(dp(6),0,dp(6),0);search.addView(placeSearchInput,sp);poiSearchButton=primary(poiSearching?"搜索中":"搜索",this::searchPlaces);poiSearchButton.setEnabled(!poiSearching);search.addView(poiSearchButton,new LinearLayout.LayoutParams(dp(62),dp(44)));placeSearchInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);placeSearchInput.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){searchPlaces();return true;}return false;});mapPanel.addView(search);
        mapPanel.removeView(search);search.setPadding(0,dp(4),0,dp(2));body.addView(search,0);
        LinearLayout c=card("");LinearLayout summary=row();View pin=AtlasUi.icon(this,"location",GREEN,26);summary.addView(pin);LinearLayout selectedInfo=column();selectedInfo.setPadding(dp(10),0,0,0);selectedTitle=AtlasUi.strong(this,selectedName,14,INK);selectedTitle.setSingleLine(true);selectedTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);selectedInfo.addView(selectedTitle);AtlasUi.space(selectedInfo,5);coordSummary=AtlasUi.mono(this,coordinate(selected),11,MUTED);coordSummary.setSingleLine(true);coordSummary.setEllipsize(android.text.TextUtils.TruncateAt.END);selectedInfo.addView(coordSummary);AtlasUi.weight(summary,selectedInfo);
        LinearLayout instantBox=column();instantBox.setGravity(Gravity.CENTER);instantBox.setPadding(dp(10),dp(6),dp(10),dp(6));instantBox.setMinimumWidth(dp(88));instantBox.setBackground(AtlasUi.shape(this,0xfff7f9f7,0xffe8eeea,12));TextView instantLabel=text("点选即生效",10,INK);instantLabel.setPadding(0,0,0,0);instantLabel.setSingleLine(true);instantBox.addView(instantLabel,new LinearLayout.LayoutParams(-2,-2));Switch instant=new Switch(this);instant.setContentDescription("点选即生效");instant.setChecked(instantApply);instant.setThumbTintList(new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{GREEN,0xffa3a6a1}));instant.setTrackTintList(android.content.res.ColorStateList.valueOf(0xffd4dfd8));instant.setOnCheckedChangeListener((b,checked)->{instantApply=checked;store.prefs.edit().putBoolean("instant_apply",checked).apply();message(checked?"已开启：地图与常用地点点选后应用":"已关闭：选点后需点应用位置");});instantBox.addView(instant,new LinearLayout.LayoutParams(-2,dp(36)));instantBox.setOnClickListener(v->instant.toggle());LinearLayout.LayoutParams instantParams=new LinearLayout.LayoutParams(-2,-2);instantParams.setMargins(dp(8),0,0,0);summary.addView(instantBox,instantParams);c.addView(summary);AtlasUi.space(c,12);
        int manualIndex=c.getChildCount();LinearLayout manual=fold(c,"坐标修改");View manualToggle=c.getChildAt(manualIndex);c.removeView(manualToggle);c.removeView(manual);LinearLayout fields=row(),left=column(),right=column();latInput=input(left,"纬度 · WGS84",String.format(Locale.US,"%.6f",selected.lat));lonInput=input(right,"经度 · WGS84",String.format(Locale.US,"%.6f",selected.lon));AtlasUi.equal(fields,left,70);AtlasUi.equal(fields,right,70);manual.addView(fields);AtlasUi.space(manual,8);manual.addView(button("复制坐标",()->copyText("WGS84",latInput.getText()+", "+lonInput.getText())));AtlasUi.space(c,10);
        applyButton=primary(isSystemMode()?"应用到手机":isPreviewMode()?"预览此位置":"应用到容器",this::applyCurrentPosition);stopButton=danger("停止并还原",()->{try{stopPlayer(true);updateGlobalStatus();message("已停止输出并还原");}catch(Exception e){error(e);}});twoButtons(c,applyButton,stopButton);locationLabel=text("选点仅在确认后应用",11,MUTED);c.addView(locationLabel);AtlasUi.space(c,8);c.addView(manualToggle);c.addView(manual);TextView setup=AtlasUi.strong(this,"系统定位授权与设置  →",12,GREEN);setup.setPadding(0,dp(10),0,dp(4));setup.setOnClickListener(v->showSystemSetup());c.addView(setup);
        LinearLayout savedCard=card("");LinearLayout saved=fold(savedCard,"常用地点管理 · "+store.bindings().length());LinearLayout savedHeader=row();AtlasUi.weight(savedHeader,AtlasUi.strong(this,"常用地点",14,INK));savedHeader.addView(button("＋ 导入课表",()->switchTab(2)));saved.addView(savedHeader);AtlasUi.space(saved,8);EditText filter=AtlasUi.field(this,"","筛选常用地点…",false);saved.addView(filter);AtlasUi.space(saved,8);savedPlacesList=column();saved.addView(savedPlacesList);watch(filter,()->renderSavedPlaces(filter.getText().toString()));renderSavedPlaces("");
        body.removeView(mode);body.addView(mode);
    }
    private void copyText(String label,String value){((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(label,value));message("已复制");}
    private void renderSavedPlaces(String keyword){if(savedPlacesList==null)return;savedPlacesList.removeAllViews();Iterator<String> names=store.bindings().keys();int count=0;while(names.hasNext()){String name=names.next();if(!name.contains(keyword.trim()))continue;Point p=store.bound(name);if(p==null)continue;count++;LinearLayout item=column();item.setPadding(0,dp(10),0,dp(10));item.addView(AtlasUi.strong(this,name,13,INK));item.addView(text(coordinate(p),11,MUTED));LinearLayout actions=row();AtlasUi.equal(actions,button("选择",()->choosePoint(p,name,true)),40);AtlasUi.equal(actions,button("编辑",()->editSavedPlace(name)),40);AtlasUi.equal(actions,button("移除",()->new AlertDialog.Builder(this).setTitle("移除常用地点？").setMessage(name).setNegativeButton("取消",null).setPositiveButton("移除",(d,w)->{store.removeBinding(name);renderSavedPlaces(keyword);}).show()),40);item.addView(actions);savedPlacesList.addView(item);}if(count==0)savedPlacesList.addView(text(store.bindings().length()==0?"还没有常用地点，可从课表导入或手动绑定。":"没有匹配的地点",12,MUTED));}
    private void searchPlaces(){
        String query=placeSearchInput==null?"":placeSearchInput.getText().toString().trim();
        String city=placeCityInput==null?"":placeCityInput.getText().toString().trim();
        if(query.isEmpty()){message("请输入地点名称或地址");return;}
        runPoiSearch(query,city,0);
    }
    private boolean configureAmapSdk(boolean showError){
        if(!store.prefs.getBoolean("amap_privacy_agreed",false))return false;
        try{
            String key=getAmapKey();
            if(key.isEmpty()){
                if(showError)message("高德地图 Key 未配置，请先点击配置 Key 或使用坐标输入");
                return false;
            }
            MapsInitializer.setApiKey(key);
            ServiceSettings.getInstance().setApiKey(key);
            ServiceSettings.updatePrivacyShow(this,true,true);
            ServiceSettings.updatePrivacyAgree(this,true);
            MapsInitializer.updatePrivacyShow(this,true,true);
            MapsInitializer.updatePrivacyAgree(this,true);
            return true;
        }catch(Exception e){
            if(showError)message("无法初始化高德地图服务："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
            return false;
        }
    }
    private boolean prepareAmapSearch(){
        if(!store.prefs.getBoolean("amap_privacy_agreed",false)){
            message("请先阅读并同意高德地图 SDK 隐私说明，再使用地点搜索");
            return false;
        }
        return configureAmapSdk(true);
    }
    private void runPoiSearch(String keyword,String city,int page){
        if(!prepareAmapSearch())return;
        if(poiSearching)return;
        final int requestId=++poiSearchRequestId;
        setPoiSearching(true);
        try{
            PoiSearch.Query query=new PoiSearch.Query(keyword,"",city);
            query.setPageSize(10);
            query.setPageNum(page);
            query.setCityLimit(!city.isEmpty());
            if(city.isEmpty()){
                Point center=map==null?selected:map.cameraCenterWgs84();
                LatLng mapPoint=CoordinateTransform.toGcj02(center);
                query.setLocation(new LatLonPoint(mapPoint.latitude,mapPoint.longitude));
                query.setDistanceSort(true);
            }
            PoiSearch search=new PoiSearch(this,query);
            search.setOnPoiSearchListener(new PoiSearch.OnPoiSearchListener(){
                @Override public void onPoiSearched(PoiResult result,int errorCode){
                    runOnUiThread(()->{
                        if(requestId==poiSearchRequestId)setPoiSearching(false);
                        if(requestId!=poiSearchRequestId||isFinishing()||isDestroyed()||tab!=0)return;
                        if(errorCode!=1000||result==null){message("高德地点搜索失败（错误码 "+errorCode+"），请检查网络和 Key 服务配置");return;}
                        ArrayList<PoiItem> pois=result.getPois();
                        if(pois==null||pois.isEmpty()){showPoiSuggestions(keyword,city,result);return;}
                        showPoiResults(keyword,city,page,result,pois);
                    });
                }
                @Override public void onPoiItemSearched(PoiItem item,int errorCode){}
            });
            message("正在搜索高德地点…");
            search.searchPOIAsyn();
        }catch(Exception e){
            if(requestId==poiSearchRequestId){setPoiSearching(false);error(new IllegalStateException("无法启动地点搜索："+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage())));}
        }
    }
    private void setPoiSearching(boolean busy){poiSearching=busy;if(poiSearchButton!=null){poiSearchButton.setEnabled(!busy);poiSearchButton.setText(busy?"搜索中":"搜索");}}
    private void showPoiResults(String keyword,String city,int page,PoiResult result,ArrayList<PoiItem> pois){
        int pages=Math.max(page+1,result.getPageCount());LinearLayout content=column();content.addView(text("第 "+(page+1)+" / "+pages+" 页 · 本页 "+pois.size()+" 个地点",12,MUTED));Dialog[] handle=new Dialog[1];
        for(PoiItem item:pois){LinearLayout itemView=column();itemView.setPadding(dp(12),dp(15),dp(12),dp(15));itemView.setBackground(AtlasUi.shape(this,AtlasUi.SOFT,0,12));TextView name=AtlasUi.strong(this,item.getTitle()==null?"未命名地点":item.getTitle(),14,INK);name.setMaxLines(2);itemView.addView(name);itemView.addView(text(poiAddress(item),12,MUTED));itemView.setOnClickListener(v->{handle[0].dismiss();selectPoiResult(item);});itemView.setClickable(true);itemView.setFocusable(true);AtlasUi.space(content,9);content.addView(itemView);}
        LinearLayout actions=row();Button previous=button("上一页",()->{handle[0].dismiss();runPoiSearch(keyword,city,page-1);});previous.setEnabled(page>0);Button cancel=button("取消",()->handle[0].dismiss());Button next=primary("下一页",()->{handle[0].dismiss();runPoiSearch(keyword,city,page+1);});next.setEnabled(page+1<pages);AtlasUi.equal(actions,previous,46);AtlasUi.equal(actions,cancel,46);AtlasUi.equal(actions,next,46);handle[0]=showSheet("地点搜索结果",content,actions);
    }
    private String poiAddress(PoiItem item){
        String address=item.getSnippet()==null?"":item.getSnippet().trim().replaceAll("\\s+"," ");
        String title=item.getTitle()==null?"":item.getTitle().trim();
        if(!title.isEmpty())address=address.replace(title,"").trim();
        String city=item.getCityName()==null?"":item.getCityName().trim();
        if(!city.isEmpty()&&address.startsWith(city))address=address.substring(city.length()).trim();
        String district=item.getAdName()==null?"":item.getAdName().trim();
        if(address.isEmpty())address=district;
        else if(!district.isEmpty()&&!address.startsWith(district)&&!address.contains(district))address=district+" · "+address;
        return address.replaceAll("^[·,，、\\s]+|[·,，、\\s]+$","").trim();
    }
    private void selectPoiResult(PoiItem item){
        LatLonPoint point=item.getLatLonPoint();
        if(point==null){message("此结果没有可用坐标，请选择其他地点");return;}
        Point wgs=CoordinateTransform.toWgs84(new LatLng(point.getLatitude(),point.getLongitude()));
        choosePoint(wgs,item.getTitle()==null?"搜索地点":item.getTitle(),true);
        message(instantApply&&!routeIsActive()?(isSystemMode()?"已请求应用地点，请查看定位状态":isPreviewMode()?"已预览地点，手机位置未改变":"已写入容器"):"已选择地点，请确认后应用");
    }
    private void showPoiSuggestions(String keyword,String city,PoiResult result){
        List<SuggestionCity> cities=result.getSearchSuggestionCitys();
        List<String> keywords=result.getSearchSuggestionKeywords();
        int cityCount=cities==null?0:cities.size(),keywordCount=keywords==null?0:keywords.size();
        if(cityCount==0&&keywordCount==0){
            AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle("没有找到地点")
                .setMessage(city.isEmpty()?"没有匹配的 POI。可以补充校区、道路或建筑关键词。":"当前城市没有匹配的 POI。可扩大到全国搜索，或补充校区、道路和建筑名称。");
            if(city.isEmpty())builder.setPositiveButton("关闭",null);
            else builder.setNegativeButton("关闭",null).setPositiveButton("搜索全国",(d,w)->{if(placeCityInput!=null)placeCityInput.setText("");runPoiSearch(keyword,"",0);});
            builder.show();
            return;
        }
        ArrayList<String> labels=new ArrayList<>();
        if(cities!=null)for(SuggestionCity item:cities)labels.add("切换到 "+item.getCityName()+"（约 "+item.getSuggestionNum()+" 条）");
        if(keywords!=null)for(String item:keywords)labels.add("试试关键词："+item);
        new AlertDialog.Builder(this).setTitle("没有直接匹配，试试这些建议")
            .setItems(labels.toArray(new String[0]),(d,index)->{
                if(index<cityCount){SuggestionCity item=cities.get(index);String cityName=item.getCityName();if(cityName==null||cityName.trim().isEmpty())cityName=item.getCityCode();if(cityName==null)cityName="";if(placeCityInput!=null)placeCityInput.setText(cityName);runPoiSearch(keyword,cityName,0);}
                else{String suggestion=keywords.get(index-cityCount);if(placeSearchInput!=null)placeSearchInput.setText(suggestion);runPoiSearch(suggestion,city,0);}
            }).setNegativeButton("取消",null).show();
    }
    private void routePage(){
        LocalStore.SavedRoute active=store.activeRoute();
        LinearLayout playback=card("");LinearLayout title=row();AtlasUi.weight(title,AtlasUi.strong(this,active.name,14,INK));progress=AtlasUi.badge(this,"未开始",GREEN,AtlasUi.SOFT);title.addView(progress);playback.addView(title);AtlasUi.space(playback,18);
        LinearLayout metrics=row(),time=column(),distance=column();time.addView(text("回放计时",11,MUTED));routeClock=AtlasUi.mono(this,"00:00 / 20:00",20,INK);time.addView(routeClock);distance.addView(text("模拟里程",11,MUTED));routeMileage=AtlasUi.mono(this,"0.00 km",20,GREEN);distance.addView(routeMileage);AtlasUi.weight(metrics,time);AtlasUi.weight(metrics,distance);playback.addView(metrics);AtlasUi.space(playback,14);
        routeProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);routeProgress.setMax(1000);routeProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));routeProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffdbeafe));playback.addView(routeProgress,new LinearLayout.LayoutParams(-1,dp(6)));AtlasUi.space(playback,14);
        routePrimary=primary("开始播放路线",()->{RoutePlayer.State s=runtime.player.snapshot().state;if(s==RoutePlayer.State.RUNNING)sendRouteAction(RoutePlaybackService.PAUSE);else requestRouteStart();});
        routeStop=danger("结束并还原",()->new AlertDialog.Builder(this).setTitle("结束路线回放？").setMessage("将结束当前任务并关闭位置输出。").setNegativeButton("继续任务",null).setPositiveButton("结束并还原",(d,w)->{try{stopPlayer(true);updateGlobalStatus();}catch(Exception e){error(e);}}).show());twoButtons(playback,routePrimary,routeStop);
        LinearLayout mapPanel=addMap(true);routeGeometry=text("",11,MUTED);routeGeometry.setPadding(dp(14),dp(6),dp(14),dp(8));mapPanel.addView(routeGeometry);LinearLayout edit=row();edit.setPadding(dp(6),0,dp(6),dp(8));Button undo=button("撤销",this::undoRoute),redo=button("重做",this::redoRoute),list=button("点列表",this::showWaypointSheet),clear=danger("清空",()->{if(routeLocked())return;new AlertDialog.Builder(this).setTitle("清空路线？").setMessage("可通过撤销恢复本次清空。").setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{pushRouteUndo();route.clear();routeChanged();}).show();});AtlasUi.equal(edit,undo,42);AtlasUi.equal(edit,redo,42);AtlasUi.equal(edit,list,42);AtlasUi.equal(edit,clear,42);mapPanel.addView(edit);
        LinearLayout settings=card("一键场景模式");LinearLayout scenes=row();routePresets=new Button[]{button("1 公里\n3′00″ / 公里",()->setRoutePreset("3.0","3")),button("5 公里\n4′00″ / 公里",()->setRoutePreset("4.0","20")),button("10 公里\n5′00″ / 公里",()->setRoutePreset("5.0","50"))};for(Button preset:routePresets)AtlasUi.equal(scenes,preset,54);settings.addView(scenes);AtlasUi.space(settings,10);
        loopInput=new CheckBox(this);loopInput.setText("闭合并循环路线");loopInput.setTextSize(13);loopInput.setTextColor(INK);loopInput.setButtonTintList(android.content.res.ColorStateList.valueOf(GREEN));loopInput.setChecked(store.prefs.getBoolean("loop",true));settings.addView(loopInput);AtlasUi.space(settings,6);
        LinearLayout params=fold(settings,"展开参数微调");LinearLayout numbers=row(),pace=column(),duration=column();paceInput=input(pace,"配速 · 分/公里",store.prefs.getString("pace","6.0"));durationInput=input(duration,"总时长 · 分钟",store.prefs.getString("duration","20"));AtlasUi.equal(numbers,pace,80);AtlasUi.equal(numbers,duration,80);params.addView(numbers);
        intervalInput=input(params,"更新间隔 · 秒",store.prefs.getString("interval","1"));LinearLayout intervals=row();for(String value:new String[]{"0.5","1","2","5"})AtlasUi.equal(intervals,button(value+"s",()->{if(routeLocked())return;intervalInput.setText(value);saveForm();}),40);AtlasUi.space(params,8);params.addView(intervals);
        if(isSystemMode())params.addView(text("系统模式每 0.1 秒持续输出；暂停时保持最后位置。",11,MUTED));
        boolean locked=routeIsActive();paceInput.setEnabled(!locked);durationInput.setEnabled(!locked);intervalInput.setEnabled(!locked);loopInput.setEnabled(!locked);loopInput.setOnCheckedChangeListener((b,checked)->{saveForm();drawRoute();updateProgress();});
        watch(paceInput,this::refreshRoutePresets);watch(durationInput,this::refreshRoutePresets);refreshRoutePresets();
        body.removeView(playback);body.addView(playback);
        LinearLayout libraryCard=card("");TextView currentRoute=AtlasUi.strong(this,active.name,15,INK);currentRoute.setSingleLine(true);currentRoute.setEllipsize(android.text.TextUtils.TruncateAt.END);libraryCard.addView(currentRoute);AtlasUi.space(libraryCard,8);libraryCard.addView(button("管理路线",this::showRouteLibrary),new LinearLayout.LayoutParams(-1,-2));body.removeView(libraryCard);body.addView(libraryCard,0);updateProgress();
    }
    private void showRouteLibrary(){
        LinearLayout library=column();LocalStore.SavedRoute active=store.activeRoute();Dialog[] handle=new Dialog[1];
        twoButtons(library,button("＋ 新建空白",()->{if(blockRouteLibrary())return;promptRouteName("新建路线","新路线",name->{if(blockRouteLibrary())return;store.createRoute(name);route=store.route();clearRouteHistory();handle[0].dismiss();render();});}),button("另存为",()->{if(blockRouteLibrary())return;promptRouteName("另存路线",active.name+" 副本",name->{if(blockRouteLibrary())return;store.saveAsRoute(name,route);route=store.route();clearRouteHistory();handle[0].dismiss();render();});}));AtlasUi.space(library,8);
        for(LocalStore.SavedRoute saved:store.routes()){
            LinearLayout item=column();item.setPadding(0,dp(10),0,dp(10));LinearLayout info=row();AtlasUi.weight(info,AtlasUi.strong(this,saved.name,13,INK));info.addView(AtlasUi.badge(this,saved.id.equals(active.id)?"当前使用":saved.points.size()+" 点",saved.id.equals(active.id)?GREEN:MUTED,saved.id.equals(active.id)?AtlasUi.SOFT:0xfff1f5f9));item.addView(info);item.addView(text(String.format(Locale.CHINA,"%.0f 米 · %d 个路线点",routeLength(saved.points,false),saved.points.size()),11,MUTED));
            LinearLayout actions=row();AtlasUi.equal(actions,button("使用",()->{if(blockRouteLibrary())return;saveForm();store.activateRoute(saved.id);route=store.route();clearRouteHistory();handle[0].dismiss();render();}),40);AtlasUi.equal(actions,button("改名",()->{if(blockRouteLibrary())return;promptRouteName("重命名路线",saved.name,name->{if(blockRouteLibrary())return;store.renameRoute(saved.id,name);handle[0].dismiss();render();});}),40);AtlasUi.equal(actions,danger("删除",()->{if(blockRouteLibrary())return;new AlertDialog.Builder(this).setTitle("删除路线？").setMessage(saved.name).setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{if(blockRouteLibrary())return;store.deleteRoute(saved.id);route=store.route();clearRouteHistory();handle[0].dismiss();render();}).show();}),40);item.addView(actions);library.addView(item);
        }
        twoButtons(library,button("导出全部 JSON",()->{if(blockRouteLibrary())return;handle[0].dismiss();exportRouteFile();}),button("导入并合并",()->{if(blockRouteLibrary())return;handle[0].dismiss();pick("application/json",23);}));
        handle[0]=showSheet("管理路线",library,null,true,true);
    }

    private void requestRouteStart(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){pendingRouteStart=true;requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},31);}else startRoutePlayback();}
    private boolean routeIsActive(){RoutePlayer.Snapshot s=runtime.player.snapshot();return runtime.isRouteImporting()||s.state==RoutePlayer.State.RUNNING||s.state==RoutePlayer.State.PAUSED||(s.state==RoutePlayer.State.ERROR&&!s.restored);}
    private boolean routeLocked(){if(routeIsActive()){message("请先结束当前路线，再编辑或管理路线");return true;}return false;}
    private void pushRouteUndo(){routeUndo.push(new ArrayList<>(route));while(routeUndo.size()>40)routeUndo.removeLast();routeRedo.clear();}
    private void clearRouteHistory(){routeUndo.clear();routeRedo.clear();}
    private void undoRoute(){if(routeLocked())return;if(routeUndo.isEmpty()){message("没有可撤销的编辑");return;}routeRedo.push(new ArrayList<>(route));route=routeUndo.pop();routeChanged();}
    private void redoRoute(){if(routeLocked())return;if(routeRedo.isEmpty()){message("没有可重做的编辑");return;}routeUndo.push(new ArrayList<>(route));route=routeRedo.pop();routeChanged();}
    private void routeChanged(){store.saveRoute(route);drawRoute();updateProgress();}
    private double routeLength(List<Point> points,boolean loop){double meters=0;for(int i=1;i<points.size();i++)meters+=points.get(i-1).distanceTo(points.get(i));if(loop&&points.size()>1)meters+=points.get(points.size()-1).distanceTo(points.get(0));return meters;}
    private void setRoutePreset(String pace,String duration){if(routeLocked())return;paceInput.setText(pace);durationInput.setText(duration);saveForm();updateProgress();message("已应用配速和时长");}
    private void refreshRoutePresets(){
        if(routePresets==null||paceInput==null||durationInput==null)return;
        double pace=Double.NaN,duration=Double.NaN;
        try{pace=Double.parseDouble(paceInput.getText().toString());duration=Double.parseDouble(durationInput.getText().toString());}catch(NumberFormatException ignored){}
        double[] paces={3,4,5},durations={3,20,50};
        for(int i=0;i<routePresets.length;i++){boolean selected=pace==paces[i]&&duration==durations[i];Button preset=routePresets[i];preset.setSelected(selected);preset.setBackground(AtlasUi.shape(this,selected?GREEN:0xfff2efe9,0,14));preset.setTextColor(selected?Color.WHITE:INK);}
    }
    private void exportRouteFile(){if(blockRouteLibrary())return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"campus-atlas-routes.json");try{startActivityForResult(i,22);}catch(Exception e){error(e);}}
    private void showWaypointSheet(){
        LinearLayout content=column();if(route.isEmpty())content.addView(text("点击地图添加至少两个路线点。",13,MUTED));
        for(int i=0;i<route.size();i++){final int index=i;LinearLayout item=column();item.addView(text((i+1)+"  ·  "+coordinate(route.get(i)),12,INK));LinearLayout actions=row();AtlasUi.equal(actions,button("上移",()->changeWaypoint(index,-1)),40);AtlasUi.equal(actions,button("下移",()->changeWaypoint(index,1)),40);AtlasUi.equal(actions,danger("删除",()->changeWaypoint(index,0)),40);item.addView(actions);AtlasUi.space(item,10);content.addView(item);}
        Dialog dialog=showSheet("路线点 · "+route.size(),content,null);waypointDialog=dialog;
    }
    private Dialog waypointDialog;
    private void changeWaypoint(int index,int direction){if(routeLocked())return;if(index<0||index>=route.size())return;int next=index+direction;if(direction!=0&&(next<0||next>=route.size()))return;pushRouteUndo();if(direction==0)route.remove(index);else Collections.swap(route,index,next);routeChanged();if(waypointDialog!=null)waypointDialog.dismiss();showWaypointSheet();}
    private boolean blockRouteLibrary(){return routeLocked();}
    private void promptRouteName(String title,String initial,java.util.function.Consumer<String> onSave){
        EditText name=new EditText(this);name.setSingleLine(true);name.setText(initial);
        new AlertDialog.Builder(this).setTitle(title).setView(name).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{
            String value=name.getText().toString().trim();if(value.isEmpty()){message("路线名称不能为空");return;}if(value.length()>40){message("路线名称最多 40 个字符");return;}onSave.accept(value);
        }).show();
    }
    private void updateProgress(){
        if(progress==null)return;RoutePlayer.Snapshot state=runtime.player.snapshot();String status;int color=GREEN;
        switch(state.state){case RUNNING:status="播放中";break;case PAUSED:status="已暂停";color=AtlasUi.AMBER;break;case COMPLETED:status="已完成 · 已还原";break;case ERROR:status=state.restored?"出错 · 已还原":"还原异常";color=AtlasUi.RED;break;default:status="未开始";}
        progress.setText(status);progress.setTextColor(color);long total=state.durationMillis;try{if(!state.hasPlan)total=(long)(Double.parseDouble(durationInput==null?store.prefs.getString("duration","20"):durationInput.getText().toString())*60000);}catch(Exception ignored){total=1200000;}
        if(routeClock!=null)routeClock.setText(clock(state.elapsedMillis/1000)+" / "+clock(total/1000));if(routeMileage!=null)routeMileage.setText(String.format(Locale.US,"%.2f km",state.distanceMeters/1000));if(routeProgress!=null)routeProgress.setProgress(total>0?(int)Math.min(1000,state.elapsedMillis*1000/total):0);
        boolean loop=loopInput==null?store.prefs.getBoolean("loop",true):loopInput.isChecked();if(routeGeometry!=null)routeGeometry.setText(String.format(Locale.CHINA,"%d 个点 · %.0f 米%s",route.size(),routeLength(route,loop),loop?" · 闭合循环":" · 单次路线"));
        boolean locked=routeIsActive();if(routePrimary!=null){routePrimary.setText(state.state==RoutePlayer.State.RUNNING?"暂停回放":state.state==RoutePlayer.State.PAUSED?"继续回放":"开始播放路线");routePrimary.setEnabled(!(state.state==RoutePlayer.State.ERROR&&!state.restored));}if(routeStop!=null)routeStop.setEnabled(locked||output.last()!=null);
        if(paceInput!=null){paceInput.setEnabled(!locked);durationInput.setEnabled(!locked);intervalInput.setEnabled(!locked);loopInput.setEnabled(!locked);}updateGlobalStatus();
    }
    private void startRoutePlayback(){
        try{
            syncImportedRoutes();
            RoutePlayer.Snapshot state=runtime.player.snapshot();
            if(state.state==RoutePlayer.State.RUNNING)return;
            if(state.state==RoutePlayer.State.PAUSED){sendRouteAction(RoutePlaybackService.RESUME);return;}
            if(state.state==RoutePlayer.State.ERROR&&!state.restored)throw new IllegalStateException("请先结束并还原上次任务");
            saveForm();
            double pace=Double.parseDouble(paceInput.getText().toString()),duration=Double.parseDouble(durationInput.getText().toString());
            long interval=Math.round(Double.parseDouble(intervalInput.getText().toString())*1000);
            RoutePlan plan=new RoutePlan(route,pace,duration,interval,loopInput.isChecked());
            long request=runtime.newRouteRequest(plan);
            Intent command=new Intent(this,RoutePlaybackService.class).setAction(RoutePlaybackService.START).putExtra("request",request);
            try{startForegroundService(command);}catch(RuntimeException failure){runtime.cancelRouteRequest(request);throw failure;}
            message("已请求开始后台播放，请查看播放状态与通知");
        }catch(Exception e){error(e);}
    }
    private void sendRouteAction(String action){
        try{
            if(!RoutePlaybackService.alive&&runtime.player.snapshot().state==RoutePlayer.State.IDLE){message("当前没有路线任务");return;}
            startForegroundService(new Intent(this,RoutePlaybackService.class).setAction(action));
        }catch(Exception e){error(e);}
    }
    private void stopPlayer(boolean reset){
        runtime.newRequest();
        FixedLocationService.stopForHandoff(this);
        try{runtime.player.stop(reset);}
        finally{if(RoutePlaybackService.alive)startService(new Intent(this,RoutePlaybackService.class).setAction(RoutePlaybackService.SYNC));}
        if(reset&&map!=null)map.removeMarker();
        updateProgress();updateGlobalStatus();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if((request==30||request==32)&&tab==0)updateGlobalStatus();if(request==32)updateSystemSetupStatus();
        if(request==30&&tab==3)render();
        if(request==31&&pendingRouteStart){pendingRouteStart=false;if(grants.length==0||grants[0]!=android.content.pm.PackageManager.PERMISSION_GRANTED)message("通知未获授权，可在路线页控制播放；系统可能隐藏通知栏入口");startRoutePlayback();}
        if((request==33||request==34)&&tab==3)render();
    }
    private void timetablePage(){
        LinearLayout tabs=row();tabs.setPadding(dp(3),dp(3),dp(3),dp(3));tabs.setBackground(AtlasUi.shape(this,0xfff2efe9,0,16));Button listTab=button("地点坐标绑定",()->{saveForm();scheduleOcrPanel=false;render();});Button ocrTab=button("课表图片 / 文字",()->{saveForm();scheduleOcrPanel=true;render();});listTab.setBackground(AtlasUi.shape(this,scheduleOcrPanel?0xfff2efe9:Color.WHITE,0,12));ocrTab.setBackground(AtlasUi.shape(this,scheduleOcrPanel?Color.WHITE:0xfff2efe9,0,12));AtlasUi.equal(tabs,listTab,44);AtlasUi.equal(tabs,ocrTab,44);body.addView(tabs);
        boolean ocrBusy=timetableRecognition.busy();
        LinearLayout c=card("课表图像提取");ocrButton=primary(ocrBusy?"正在本机识别…":"＋ 选择课表图片（本机识别）",()->{saveForm();pick("image/*",20);});ocrButton.setEnabled(!ocrBusy);c.addView(ocrButton);AtlasUi.space(c,10);
        String timetableOcr=TimetableParser.normalizeOcrText(store.prefs.getString("ocr",""));if(!timetableOcr.equals(store.prefs.getString("ocr","")))store.prefs.edit().putString("ocr",timetableOcr).apply();
        LinearLayout editor=column();c.addView(editor);ocrInput=AtlasUi.field(this,timetableOcr,"可在这里粘贴或修正识别文字；空行可分隔不同课程",false);ocrInput.setContentDescription("识别文本");ocrInput.setSingleLine(false);ocrInput.setMinLines(4);ocrInput.setMaxLines(10);ocrInput.setGravity(Gravity.TOP);ocrInput.setEnabled(!ocrBusy);editor.addView(ocrInput,new LinearLayout.LayoutParams(-1,-2));AtlasUi.space(editor,8);Button extract=primary("提取地点",()->{saveForm();renderCandidates();message("已提取 "+timetableCandidates().size()+" 个地点，同名地点已合并");});extract.setEnabled(!ocrBusy);editor.addView(extract);editor.addView(text("建议使用清晰原图；地点和教室号请核对后绑定坐标。识别失败会保留原结果。",11,MUTED));
        c.setVisibility(scheduleOcrPanel?View.VISIBLE:View.GONE);LinearLayout places=card("");places.setVisibility(scheduleOcrPanel?View.GONE:View.VISIBLE);LinearLayout title=row();AtlasUi.weight(title,AtlasUi.strong(this,"地点候选与坐标核对",14,INK));title.addView(button("＋ 添加",()->bindDialog("")));places.addView(title);AtlasUi.space(places,8);
        LinearLayout filters=row();EditText keyword=AtlasUi.field(this,candidateKeyword,"搜索候选地点…",false);LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(0,dp(40),1);kp.setMargins(0,0,dp(8),0);filters.addView(keyword,kp);Button status=button(new String[]{"全部状态","未绑定","已绑定"}[candidateFilter]+" ▾",()->{});status.setTextSize(11);status.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("筛选绑定状态").setSingleChoiceItems(new String[]{"全部状态","未绑定","已绑定"},candidateFilter,(dialog,index)->{candidateFilter=index;status.setText(new String[]{"全部状态","未绑定","已绑定"}[index]+" ▾");dialog.dismiss();renderCandidates();}).setNegativeButton("取消",null).show());filters.addView(status,new LinearLayout.LayoutParams(dp(96),dp(40)));places.addView(filters);AtlasUi.space(places,8);places.addView(button("复用同名已存坐标",this::reuseKnownBindings));AtlasUi.space(places,4);candidateList=column();places.addView(candidateList);watch(keyword,()->{candidateKeyword=keyword.getText().toString();renderCandidates();});renderCandidates();
    }
    private List<String> timetableCandidates(){List<String> names=new ArrayList<>(store.manualPlaces());names.addAll(TimetableParser.candidates(store.prefs.getString("ocr","")));return TimetableParser.uniquePlaces(names);}
    private void renderCandidates(){
        if(candidateList==null)return;candidateList.removeAllViews();int count=0;
        boolean stacked=getResources().getConfiguration().screenWidthDp<360||getResources().getConfiguration().fontScale>=1.3f;
        for(String name:timetableCandidates()){
            Point p=store.bound(name);if(!name.contains(candidateKeyword.trim())||candidateFilter==1&&p!=null||candidateFilter==2&&p==null)continue;count++;
            LinearLayout item=column();item.setPadding(dp(12),dp(10),dp(12),dp(10));item.setBackground(AtlasUi.shape(this,p==null?0xfff8f2ed:BG,p==null?0xffe8d7cb:AtlasUi.LINE,16));
            LinearLayout content=row(),info=column();TextView nameView=AtlasUi.strong(this,name,12,INK);nameView.setMaxLines(2);info.addView(nameView);TextView detail=AtlasUi.mono(this,p==null?"待核对并绑定坐标":coordinate(p),11,p==null?AtlasUi.AMBER:MUTED);detail.setPadding(0,dp(4),0,0);info.addView(detail);
            Button edit=button(p==null?"绑定坐标":"修改",()->bindDialog(name));edit.setContentDescription(p==null?"核对并绑定坐标":"修改坐标");Button apply=p==null?null:primary("应用",()->confirmPlaceOutput(name,p));if(apply!=null)apply.setContentDescription("应用地点");
            if(stacked){item.addView(info);AtlasUi.space(item,8);if(apply==null)item.addView(edit);else twoButtons(item,edit,apply);}
            else{AtlasUi.weight(content,info);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(dp(p==null?82:58),dp(40));ep.setMargins(dp(8),0,0,0);content.addView(edit,ep);if(apply!=null){LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(58),dp(40));ap.setMargins(dp(6),0,0,0);content.addView(apply,ap);}item.addView(content);}
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);ip.setMargins(0,dp(8),0,0);candidateList.addView(item,ip);
        }
        if(count==0)candidateList.addView(text(timetableCandidates().isEmpty()?"导入课表或手动添加地点后，在这里核对坐标。":"没有符合条件的地点",12,MUTED));
    }
    private void confirmPlaceOutput(String name,Point p){if(!readyForNewOutput())return;new AlertDialog.Builder(this).setTitle("应用「"+name+"」？").setMessage(coordinate(p)+"\n目标："+targetLabel()+(routeIsActive()?"\n当前路线将结束。":"")).setNegativeButton("取消",null).setPositiveButton("应用",(d,w)->{try{selectedName=name;applyFixed(p);tab=0;render();message(isSystemMode()?"已请求应用地点，请查看定位状态":isPreviewMode()?"仅预览地点，手机位置未改变":"地点已写入容器");}catch(Exception e){error(e);}}).show();}
    private void reuseKnownBindings(){int matched=0;for(String name:timetableCandidates())if(store.bound(name)!=null)matched++;renderCandidates();message(matched>0?"已关联 "+matched+" 个同名地点坐标；其余地点请核对后绑定":"没有可直接关联的坐标，请核对后绑定");}
    private void bindDialog(String name){bindDialog(name,false);}
    private void editSavedPlace(String name){bindDialog(name,true);}
    private void bindDialog(String original,boolean rename){
        Point bound=store.bound(original);Point start=bound==null?selected:bound;LinearLayout content=column();EditText name=input(content,"地点名称",original);if(!original.isEmpty()&&!rename)name.setEnabled(false);AtlasUi.space(content,10);
        AtlasMapView localMap=null;if(hasAmapKey()&&store.prefs.getBoolean("amap_privacy_agreed",false)){localMap=new AtlasMapView(this,start);localMap.showSelection(start,original.isEmpty()?"绑定地点":original);FrameLayout frame=new FrameLayout(this);frame.setBackground(AtlasUi.shape(this,Color.WHITE,AtlasUi.LINE,14));frame.setClipToOutline(true);frame.addView(localMap,new FrameLayout.LayoutParams(-1,-1));content.addView(frame,new LinearLayout.LayoutParams(-1,dp(210)));}
        content.addView(text("地图选点或手动输入，保存前请核对地点。",11,MUTED));LinearLayout coordinates=row(),left=column(),right=column();EditText lat=input(left,"纬度 · WGS84",String.format(Locale.US,"%.6f",start.lat));EditText lon=input(right,"经度 · WGS84",String.format(Locale.US,"%.6f",start.lon));AtlasUi.equal(coordinates,left,80);AtlasUi.equal(coordinates,right,80);content.addView(coordinates);
        final AtlasMapView bindingMap=localMap;if(bindingMap!=null){bindingMap.setPointTapListener(p->{lat.setText(String.format(Locale.US,"%.6f",p.lat));lon.setText(String.format(Locale.US,"%.6f",p.lon));bindingMap.showSelection(p,name.getText().toString());});}
        LinearLayout actions=row();Dialog[] handle=new Dialog[1];AtlasUi.equal(actions,button("取消",()->handle[0].dismiss()),46);AtlasUi.equal(actions,primary("保存地点",()->{try{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("请输入地点名称");return;}Point p=new Point(Double.parseDouble(lat.getText().toString()),Double.parseDouble(lon.getText().toString()));if(rename&&!original.equals(value))store.renameBinding(original,value,p);else store.bind(value,p);if(original.isEmpty())store.addManualPlace(value);handle[0].dismiss();saveForm();render();message("地点坐标已保存");}catch(Exception e){lat.setError("请检查名称或经纬度："+e.getMessage());}}),46);
        handle[0]=showSheet(original.isEmpty()?"添加地点":"核对并绑定坐标",content,actions);if(bindingMap!=null){sheetMap=bindingMap;if(activityResumed)bindingMap.onResume();}
    }
    private Dialog showSheet(String title,View content,LinearLayout actions){return showSheet(title,content,actions,false);}
    private Dialog showSheet(String title,View content,LinearLayout actions,boolean compact){
        return showSheet(title,content,actions,compact,false);
    }
    private Dialog showSheet(String title,View content,LinearLayout actions,boolean compact,boolean centeredGlass){
        Dialog dialog=new Dialog(this);LinearLayout panel=column();panel.setPadding(dp(18),dp(10),dp(18),dp(18));panel.setBackground(AtlasUi.shape(this,centeredGlass?0xedf6f8f5:Color.WHITE,centeredGlass?0xb3ffffff:0,24));LinearLayout heading=row();AtlasUi.weight(heading,AtlasUi.strong(this,title,16,INK));Button close=button("关闭",dialog::dismiss);heading.addView(close,new LinearLayout.LayoutParams(dp(64),dp(42)));panel.addView(heading);AtlasUi.space(panel,12);ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(content);
        if(compact){int width=Math.max(1,getResources().getDisplayMetrics().widthPixels-dp(centeredGlass?68:36));content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));int height=Math.min(content.getMeasuredHeight(),(int)(getResources().getDisplayMetrics().heightPixels*.65));panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.max(1,height)));}else panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(actions!=null){AtlasUi.space(panel,12);panel.addView(actions);}dialog.setContentView(panel,new ViewGroup.LayoutParams(-1,compact?-2:(int)(getResources().getDisplayMetrics().heightPixels*.83)));Window window=dialog.getWindow();if(window!=null){if(centeredGlass){window.setBackgroundDrawable(AtlasUi.shape(this,0x01ffffff,0,24));if(Build.VERSION.SDK_INT>=31)window.setBackgroundBlurRadius(dp(60));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attrs=window.getAttributes();attrs.dimAmount=.28f;window.setAttributes(attrs);}else window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(centeredGlass?Gravity.CENTER:Gravity.BOTTOM);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}activeSheet=dialog;if(centeredGlass&&Build.VERSION.SDK_INT>=31)getWindow().getDecorView().setRenderEffect(android.graphics.RenderEffect.createBlurEffect(dp(16),dp(16),android.graphics.Shader.TileMode.CLAMP));dialog.setOnDismissListener(d->{if(centeredGlass&&Build.VERSION.SDK_INT>=31)getWindow().getDecorView().setRenderEffect(null);if(sheetMap!=null){sheetMap.onPause();sheetMap.onDestroy();sheetMap=null;}if(activeSheet==dialog){activeSheet=null;systemSetupStatus=null;}if(deferredPageRefresh&&activityResumed&&!isDestroyed()){deferredPageRefresh=false;render();}});dialog.show();if(window!=null)window.setLayout(centeredGlass?getResources().getDisplayMetrics().widthPixels-dp(32):-1,-2);return dialog;
    }
    private boolean containerBusy,spaceSettingsExpanded;
    private boolean hasPermission(String permission){return checkSelfPermission(permission)==android.content.pm.PackageManager.PERMISSION_GRANTED;}
    private int photoAccess(){
        if(Build.VERSION.SDK_INT>=33){
            if(hasPermission(android.Manifest.permission.READ_MEDIA_IMAGES))return 2;
            if(Build.VERSION.SDK_INT>=34&&hasPermission(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))return 1;
            return 0;
        }
        return hasPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)?2:0;
    }
    private void updateMediaPermissionStatus(){
        if(mediaPermissionStatus==null)return;
        boolean camera=hasPermission(android.Manifest.permission.CAMERA);int photos=photoAccess();
        mediaPermissionStatus.setText("相机："+(camera?"已授权":"未授权")+" · 照片："+(photos==2?"全部照片":photos==1?"部分照片":"未授权"));
    }
    private void requestCameraAndPhotos(){
        ArrayList<String> needed=new ArrayList<>();
        if(!hasPermission(android.Manifest.permission.CAMERA))needed.add(android.Manifest.permission.CAMERA);
        if(photoAccess()<2){
            if(Build.VERSION.SDK_INT>=33){
                needed.add(android.Manifest.permission.READ_MEDIA_IMAGES);
                if(Build.VERSION.SDK_INT>=34)needed.add(android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
            }else needed.add(android.Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if(needed.isEmpty()){message("相机和照片权限已开启");return;}
        requestPermissions(needed.toArray(new String[0]),33);
    }
    private void containerPage(){
        JSONArray apks;try{apks=new JSONArray(store.prefs.getString("apks","[]"));}catch(JSONException e){apks=new JSONArray();}
        LinearLayout tabs=row();tabs.setPadding(dp(3),dp(3),dp(3),dp(3));tabs.setBackground(AtlasUi.shape(this,0xfff2efe9,0,16));Button appsTab=button("空间应用",()->{spacePermissionsPanel=false;render();});Button permissionsTab=button("容器状态与权限",()->{spacePermissionsPanel=true;spaceSettingsExpanded=true;render();});appsTab.setBackground(AtlasUi.shape(this,spacePermissionsPanel?0xfff2efe9:Color.WHITE,0,12));permissionsTab.setBackground(AtlasUi.shape(this,spacePermissionsPanel?Color.WHITE:0xfff2efe9,0,12));AtlasUi.equal(tabs,appsTab,44);AtlasUi.equal(tabs,permissionsTab,44);body.addView(tabs);
        boolean available=engine.isAvailable();LinearLayout state=card("");LinearLayout heading=row();
        state.setVisibility(spacePermissionsPanel?View.VISIBLE:View.GONE);
        AtlasUi.weight(heading,AtlasUi.strong(this,"容器服务状态",14,INK));heading.addView(AtlasUi.badge(this,available?"已连接":"未连接",available?GREEN:AtlasUi.AMBER,available?AtlasUi.SOFT:0xfffffbeb));state.addView(heading);
        AtlasUi.space(state,12);state.addView(text("当前位置接收目标",11,MUTED));TextView targetName=AtlasUi.strong(this,spaceTargetName(apks),15,INK);targetName.setMaxLines(2);targetName.setEllipsize(android.text.TextUtils.TruncateAt.END);state.addView(targetName);for(int i=0;i<apks.length();i++){JSONObject item=apks.optJSONObject(i);if(item!=null&&targetLabel().equals(item.optString("package"))){apkPresentationLoader.bind(item,targetName,null,null,null,"");break;}}
        if(!available)state.addView(text("服务暂未连接，可在下方设置中刷新状态。",12,AtlasUi.AMBER));if(containerBusy){AtlasUi.space(state,8);state.addView(text("正在处理应用，请稍候…",12,GREEN));}
        LinearLayout library=card("");library.setVisibility(spacePermissionsPanel?View.GONE:View.VISIBLE);LinearLayout title=row();AtlasUi.weight(title,AtlasUi.strong(this,"空间应用 · "+apks.length(),16,INK));Button importButton=primary("＋ 导入",()->pick("*/*",21));importButton.setContentDescription("导入 APK");importButton.setEnabled(!containerBusy);title.addView(importButton);library.addView(title);
        if(apks.length()==0){AtlasUi.space(library,18);LinearLayout empty=column();empty.setPadding(dp(16),dp(24),dp(16),dp(24));empty.setGravity(Gravity.CENTER);empty.setBackground(AtlasUi.shape(this,AtlasUi.BG,0,12));empty.addView(AtlasUi.icon(this,"space",GREEN,32));AtlasUi.space(empty,12);TextView hint=text("点击右上角导入 APK 开始",12,MUTED);hint.setGravity(Gravity.CENTER);empty.addView(hint);library.addView(empty);}
        for(int i=0;i<apks.length();i++){
            try{
                JSONObject item=apks.getJSONObject(i);String pkg=item.getString("package"),label=item.optString("label",pkg.substring(pkg.lastIndexOf('.')+1));if(label.trim().isEmpty())label=pkg;
                boolean installed=engine.installed(pkg),target=targetLabel().equals(pkg);AtlasUi.space(library,14);LinearLayout app=column();app.setPadding(dp(12),dp(14),dp(12),dp(12));app.setBackground(AtlasUi.shape(this,AtlasUi.BG,AtlasUi.LINE,14));
                LinearLayout info=row();String avatarText=label.isEmpty()?"?":label.substring(0,label.offsetByCodePoints(0,1));FrameLayout avatarFrame=new FrameLayout(this);TextView avatar=AtlasUi.strong(this,avatarText,20,Color.WHITE);avatar.setGravity(Gravity.CENTER);avatar.setBackground(AtlasUi.gradient(this,12));avatarFrame.addView(avatar,new FrameLayout.LayoutParams(-1,-1));ImageView appIcon=new ImageView(this);appIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);appIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);appIcon.setVisibility(View.GONE);avatarFrame.addView(appIcon,new FrameLayout.LayoutParams(-1,-1));info.addView(avatarFrame,new LinearLayout.LayoutParams(dp(46),dp(46)));
                LinearLayout name=column();name.setPadding(dp(12),0,0,0);TextView appName=AtlasUi.strong(this,label,15,INK);appName.setMaxLines(2);appName.setEllipsize(android.text.TextUtils.TruncateAt.END);name.addView(appName);apkPresentationLoader.bind(item,appName,appIcon,avatar,null,"");AtlasUi.space(name,5);name.addView(text("版本 "+item.optString("version","—"),11,MUTED));AtlasUi.weight(info,name);app.addView(info);
                AtlasUi.space(app,10);LinearLayout badges=row();badges.addView(AtlasUi.badge(this,!available?"等待服务":installed?"已安装":"待安装",installed?GREEN:AtlasUi.AMBER,installed?AtlasUi.SOFT:0xfffffbeb));if(target){TextView selected=AtlasUi.badge(this,"当前目标",GREEN,0xffdbeafe);LinearLayout.LayoutParams selectedParams=new LinearLayout.LayoutParams(-2,-2);selectedParams.setMargins(dp(8),0,0,0);badges.addView(selected,selectedParams);}app.addView(badges);AtlasUi.space(app,12);
                java.io.File original=new java.io.File(new java.io.File(getFilesDir(),"original-apks"),item.getString("file"));Button install=button(installed?"重新安装":"安装到空间",()->containerWork(()->engine.installOriginal(original),"容器安装完成"));install.setEnabled(!containerBusy&&available);
                if(installed){Button receive=button(target?"当前目标":"设为目标",()->changeTarget(pkg));receive.setEnabled(!containerBusy&&!target);Button launch=primary("打开应用",()->containerWork(()->engine.launch(pkg),"已发送启动请求，请观察应用是否正常打开"));launch.setEnabled(!containerBusy&&available);spaceActions(app,receive,launch);}
                else{Button firstInstall=primary("安装到空间",()->containerWork(()->engine.installOriginal(original),"容器安装完成"));firstInstall.setEnabled(!containerBusy&&available);app.addView(firstInstall,new LinearLayout.LayoutParams(-1,-2));}
                AtlasUi.space(app,10);LinearLayout details=fold(app,"应用详情");AtlasUi.space(details,10);details.addView(text("应用包名",11,MUTED));TextView packageName=text(pkg,12,INK);packageName.setTextIsSelectable(true);details.addView(packageName);details.addView(text(String.format(Locale.CHINA,"安装文件 · %.1f MB",item.optLong("bytes")/1024.0/1024),12,MUTED));TextView sha=AtlasUi.mono(this,item.optString("sha256"),10,MUTED);sha.setTextIsSelectable(true);details.addView(sha);AtlasUi.space(details,8);details.addView(button("复制 SHA-256",()->copyText("SHA-256",item.optString("sha256"))),new LinearLayout.LayoutParams(-1,-2));if(installed){AtlasUi.space(details,8);details.addView(install,new LinearLayout.LayoutParams(-1,-2));}AtlasUi.space(details,8);Button remove=danger("卸载并删除应用",()->confirmRemoveApp(pkg));remove.setEnabled(!containerBusy);details.addView(remove,new LinearLayout.LayoutParams(-1,-2));library.addView(app);
            }catch(JSONException e){library.addView(text("一项应用记录无法显示，请重新导入该 APK。",12,AtlasUi.AMBER));}
        }
        spaceSettings(available).setVisibility(spacePermissionsPanel?View.VISIBLE:View.GONE);
    }
    private String spaceTargetName(JSONArray apks){String current=targetLabel();for(int i=0;i<apks.length();i++){JSONObject app=apks.optJSONObject(i);if(app!=null&&current.equals(app.optString("package")))return app.optString("label",current);}return current;}
    private void spaceActions(LinearLayout parent,Button secondary,Button primary){
        // Natural button height avoids clipping when the device uses larger fonts.
        boolean stacked=getResources().getConfiguration().screenWidthDp<360||getResources().getConfiguration().fontScale>=1.3f;
        if(stacked){parent.addView(primary,new LinearLayout.LayoutParams(-1,-2));AtlasUi.space(parent,8);parent.addView(secondary,new LinearLayout.LayoutParams(-1,-2));return;}
        LinearLayout actions=row();LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,-2,1),right=new LinearLayout.LayoutParams(0,-2,1);left.setMargins(0,0,dp(4),0);right.setMargins(dp(4),0,0,0);actions.addView(secondary,left);actions.addView(primary,right);parent.addView(actions);
    }
    private LinearLayout spaceSettings(boolean available){
        LinearLayout settings=card("");LinearLayout heading=row();AtlasUi.weight(heading,AtlasUi.strong(this,"权限与设置",14,INK));boolean granted=hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION);int count=(granted?1:0)+(hasPermission(android.Manifest.permission.CAMERA)?1:0)+(photoAccess()==2?1:0);heading.addView(AtlasUi.badge(this,count==3?"权限已就绪":count+"/3 已开启",count==3?GREEN:AtlasUi.AMBER,count==3?AtlasUi.SOFT:0xfffffbeb));settings.addView(heading);AtlasUi.space(settings,10);
        LinearLayout content=column();content.setVisibility(spaceSettingsExpanded?View.VISIBLE:View.GONE);Button toggle=button(spaceSettingsExpanded?"收起权限与设置  ▴":"查看权限与设置  ▾",()->{});toggle.setOnClickListener(v->{spaceSettingsExpanded=!spaceSettingsExpanded;content.setVisibility(spaceSettingsExpanded?View.VISIBLE:View.GONE);toggle.setText(spaceSettingsExpanded?"收起权限与设置  ▴":"查看权限与设置  ▾");});settings.addView(toggle,new LinearLayout.LayoutParams(-1,-2));settings.addView(content);AtlasUi.space(content,14);
        spacePermissionRow(content,"精确位置",text(granted?"已授权":"尚未授权",12,MUTED),granted?"检查":"授权",()->{if(granted){message("宿主定位权限已开启，空间内应用也需要单独授权");return;}requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},30);});
        mediaPermissionStatus=text("",12,MUTED);updateMediaPermissionStatus();spacePermissionRow(content,"相机与相册",mediaPermissionStatus,hasPermission(android.Manifest.permission.CAMERA)&&photoAccess()==2?"检查":"授权",this::requestCameraAndPhotos);
        content.addView(button("在系统设置中管理权限",()->{try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.fromParts("package",getPackageName(),null)));}catch(Exception e){error(e);}}),new LinearLayout.LayoutParams(-1,-2));AtlasUi.space(content,10);Button preview=button("切回预览",()->changeTarget(null));preview.setEnabled(!containerBusy&&!isPreviewMode());spaceActions(content,button("刷新状态",()->{saveForm();render();}),preview);AtlasUi.space(content,12);
        content.addView(text(available?"空间应用的数据独立保存，首次打开需单独登录。":engine.status(),11,MUTED));LinearLayout technical=fold(content,"查看运行信息");technical.addView(text(engine.status()+"\nARM64 · Android "+Build.VERSION.RELEASE,11,MUTED));
        if(hasProbeEntries()){AtlasUi.space(content,10);Button cleanup=button("删除定位探针测试项",this::confirmProbeCleanup);cleanup.setEnabled(!containerBusy);content.addView(cleanup,new LinearLayout.LayoutParams(-1,-2));}
        return settings;
    }
    private void spacePermissionRow(LinearLayout parent,String title,TextView status,String action,Runnable clicked){LinearLayout item=row(),description=column();description.addView(AtlasUi.strong(this,title,13,INK));description.addView(status);AtlasUi.weight(item,description);Button control=button(action,clicked);LinearLayout.LayoutParams buttonParams=new LinearLayout.LayoutParams(-2,-2);buttonParams.setMargins(dp(12),0,0,0);item.addView(control,buttonParams);parent.addView(item);AtlasUi.space(parent,14);}
    private void chooseTarget(){
        if(containerBusy){message("应用空间正在处理，请稍候");return;}if(!readyForNewOutput())return;
        LinearLayout choices=column();Dialog[] dialog=new Dialog[1];
        addTargetOption(choices,"仅本地预览","仅在本应用内显示位置与路线",isPreviewMode(),()->{dialog[0].dismiss();changeTarget(null);});
        addTargetOption(choices,"手机系统模拟定位","使用系统模拟位置，需要检查授权",isSystemMode(),()->{dialog[0].dismiss();chooseSystemMode();});
        int count=0;try{JSONArray apks=new JSONArray(store.prefs.getString("apks","[]"));for(int i=0;i<apks.length();i++){JSONObject app=apks.getJSONObject(i);String pkg=app.getString("package");if(engine.installed(pkg)){count++;String label=app.optString("label",pkg);if(label.trim().isEmpty())label=pkg;boolean selected=pkg.equals(targetLabel());TextView optionName=addTargetOption(choices,label,"应用空间 · "+pkg,selected,()->{dialog[0].dismiss();changeTarget(pkg);});View option=(View)optionName.getParent().getParent();apkPresentationLoader.bind(app,optionName,null,null,option,selected?"，当前目标":"，选择目标");}}}catch(Exception e){error(e);}
        if(count==0){TextView empty=text("还没有已安装的空间应用，可先前往应用空间。",12,MUTED);empty.setPadding(dp(4),dp(10),dp(4),dp(4));choices.addView(empty);}
        AtlasUi.space(choices,12);Button manage=button("管理应用空间 →",()->{dialog[0].dismiss();switchTab(3);});choices.addView(manage,new LinearLayout.LayoutParams(-1,-2));
        dialog[0]=showSheet("切换位置接收目标",choices,null,true);
    }
    private TextView addTargetOption(LinearLayout parent,String title,String detail,boolean selected,Runnable action){
        LinearLayout option=row();option.setPadding(dp(14),dp(12),dp(14),dp(12));option.setMinimumHeight(dp(64));option.setBackground(AtlasUi.shape(this,selected?AtlasUi.SOFT:Color.WHITE,selected?GREEN:AtlasUi.LINE,16));
        LinearLayout info=column();TextView name=AtlasUi.strong(this,title,13,INK);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);info.addView(name);TextView sub=AtlasUi.text(this,detail,11,MUTED);sub.setMaxLines(2);sub.setEllipsize(android.text.TextUtils.TruncateAt.END);sub.setPadding(0,dp(4),0,0);info.addView(sub);AtlasUi.weight(option,info);
        TextView badge=AtlasUi.badge(this,selected?"当前":"选择",selected?Color.WHITE:INK,selected?GREEN:0xfff2efe9);LinearLayout.LayoutParams badgeParams=new LinearLayout.LayoutParams(-2,-2);badgeParams.setMargins(dp(10),0,0,0);option.addView(badge,badgeParams);option.setOnClickListener(v->action.run());option.setFocusable(true);option.setContentDescription(title+(selected?"，当前目标":"，选择目标"));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);if(parent.getChildCount()>0)params.setMargins(0,dp(8),0,0);parent.addView(option,params);return name;
    }
    private void confirmRemoveApp(String pkg){
        if(pkg.equals(targetLabel())||pkg.equals(getSharedPreferences("playback_recovery",0).getString("pending_target",""))){message("请先切换目标并确认位置已还原");return;}
        new AlertDialog.Builder(this).setTitle("卸载并删除应用？").setMessage("将卸载空间内的 "+pkg+"，删除其空间数据及所有已导入版本的 APK 副本。此操作不可撤销，不影响手机系统安装的应用。")
            .setNegativeButton("取消",null).setPositiveButton("卸载并删除",(d,w)->containerWork(()->{try{synchronized(runtime.player){
                if(pkg.equals(targetLabel())||pkg.equals(getSharedPreferences("playback_recovery",0).getString("pending_target","")))throw new IllegalStateException("请先切换目标并确认位置已还原");
                ApkLibrary.removePackage(getApplicationContext(),pkg,target->{if(!engine.isAvailable())throw new IllegalStateException("容器服务未连接，无法确认卸载状态");if(engine.installed(target)){top.niunaijun.blackbox.BlackBoxCore.get().uninstallPackageAsUser(target,0);if(!engine.isAvailable()||engine.installed(target))throw new IllegalStateException("卸载未确认，请重试");}});
            }}catch(Exception error){throw new IllegalStateException(error.getMessage(),error);}},"应用已卸载，APK 副本已删除")).show();
    }
    private void chooseSystemMode(){if(!readyForNewOutput())return;if(!runtime.systemLocation.unavailableReason().isEmpty()){showSystemSetup();return;}changeTarget(SystemLocationOutput.TARGET);}
    private void showSystemSetup(){
        LinearLayout content=column();TextView instructions=text("1. 开启手机的系统定位开关。\n2. 授予行迹空间精确位置权限。\n3. 在开发者选项 → 选择模拟位置信息应用中，选择行迹空间。\n4. 返回此处检查并启用，再点击应用到手机。",13,INK);instructions.setLineSpacing(dp(3),1f);content.addView(instructions);AtlasUi.space(content,10);
        TextView status=text(runtime.systemLocation.status(),12,0xff315e48);systemSetupStatus=status;status.setPadding(dp(12),dp(10),dp(12),dp(10));status.setBackground(AtlasUi.shape(this,AtlasUi.SOFT,0,12));content.addView(status);AtlasUi.space(content,12);
        content.addView(button("打开系统定位开关",()->{try{startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));}catch(Exception e){try{startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));}catch(Exception ex){error(ex);}}}));
        AtlasUi.space(content,8);content.addView(button("授予精确位置权限",()->{ArrayList<String> needed=new ArrayList<>();needed.add(android.Manifest.permission.ACCESS_FINE_LOCATION);needed.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);if(Build.VERSION.SDK_INT>=33)needed.add(android.Manifest.permission.POST_NOTIFICATIONS);requestPermissions(needed.toArray(new String[0]),32);}));
        AtlasUi.space(content,8);content.addView(button("打开开发者选项",()->{try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(Exception e){startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));}}));AtlasUi.space(content,8);content.addView(text("使用 Android 标准模拟接口；其他应用可识别或忽略模拟数据。停止后会移除模拟提供器，真实定位需要重新刷新。",11,MUTED));
        Dialog[] handle=new Dialog[1];LinearLayout actions=row();AtlasUi.equal(actions,button("取消",()->handle[0].dismiss()),46);AtlasUi.equal(actions,primary("检查并启用",()->{String reason=runtime.systemLocation.unavailableReason();status.setText(reason.isEmpty()?"授权检查通过":reason);if(reason.isEmpty()){handle[0].dismiss();changeTarget(SystemLocationOutput.TARGET);}else message(reason);}),46);handle[0]=showSheet("开启系统模拟定位",content,actions,true,true);
    }
    private void updateSystemSetupStatus(){if(systemSetupStatus!=null&&activeSheet!=null&&activeSheet.isShowing())systemSetupStatus.setText(runtime.systemLocation.status());}
    private void changeTarget(String pkg){
        if(!readyForNewOutput())return;Runnable change=()->{try{LocationOutput next=pkg==null?null:SystemLocationOutput.TARGET.equals(pkg)?runtime.systemLocation:engine.locationOutput(pkg);stopPlayer(true);if(next==null)output.preview();else output.select(next);store.prefs.edit().putString("output_mode",SystemLocationOutput.TARGET.equals(pkg)?"system":"").apply();saveForm();render();message(pkg==null?"已切回仅预览模式":SystemLocationOutput.TARGET.equals(pkg)?"已选择系统模式，点应用到手机开始输出":"已选择目标，应用位置后生效");}catch(Exception e){error(e);}};
        if(routeIsActive()||output.last()!=null)new AlertDialog.Builder(this).setTitle("切换输出目标？").setMessage("将结束当前输出并还原，再切换到"+(pkg==null?"仅预览模式":pkg)+"。").setNegativeButton("取消",null).setPositiveButton("切换",(d,w)->change.run()).show();else change.run();
    }
    private boolean hasProbeEntries(){try{JSONArray a=new JSONArray(store.prefs.getString("apks","[]"));for(int i=0;i<a.length();i++)if("dev.campus.atlas.probe".equals(a.getJSONObject(i).optString("package")))return true;}catch(JSONException ignored){}return false;}
    private void confirmProbeCleanup(){
        if("dev.campus.atlas.probe".equals(targetLabel())){message("请先将定位目标切换到其他应用或预览模式");return;}
        new AlertDialog.Builder(this).setTitle("删除定位探针测试项？").setMessage("将卸载容器内的定位探针并删除它的导入记录及 APK 副本。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->containerWork(()->{try{cleanupProbeEntries();}catch(Exception e){throw new IllegalStateException(e);}},"定位探针测试项已删除")).show();
    }
    private void cleanupProbeEntries() throws Exception{
        final String probe="dev.campus.atlas.probe";
        if(!engine.isAvailable())throw new IllegalStateException("容器服务未连接，暂不能清理测试应用");
        JSONArray old=new JSONArray(store.prefs.getString("apks","[]")), keep=new JSONArray();
        ArrayList<String> remove=new ArrayList<>();HashSet<String> used=new HashSet<>();
        for(int i=0;i<old.length();i++){
            JSONObject item=old.getJSONObject(i);String file=item.optString("file"),sha=item.optString("sha256");
            if(probe.equals(item.optString("package"))){if(sha.matches("[0-9a-f]{64}")&&file.equals(sha+".apk"))remove.add(file);else throw new IllegalStateException("测试 APK 记录格式异常");}
            else{keep.put(item);used.add(file);}
        }
        if(engine.installed(probe)){top.niunaijun.blackbox.BlackBoxCore.get().uninstallPackageAsUser(probe,0);if(engine.installed(probe))throw new IllegalStateException("测试应用卸载未确认");}
        if(!store.prefs.edit().putString("apks",keep.toString()).commit())throw new IllegalStateException("保存应用列表失败");
        java.io.File dir=new java.io.File(getFilesDir(),"original-apks").getCanonicalFile();
        for(String name:remove){if(used.contains(name))continue;java.io.File file=new java.io.File(dir,name).getCanonicalFile();if(!dir.equals(file.getParentFile()))throw new IllegalStateException("测试文件路径异常");if(file.exists()&&!file.delete())throw new IllegalStateException("测试 APK 删除失败");}
    }
    private void containerWork(Runnable action,String success){
        if(containerBusy)return;containerBusy=true;if(tab==3)render();message("正在处理容器操作…");
        worker.execute(()->{try{action.run();runOnUiThread(()->{containerBusy=false;if(!isDestroyed()){if(tab==3)render();message(success);}});}catch(Throwable e){runOnUiThread(()->{containerBusy=false;if(!isDestroyed()){if(tab==3)render();error(new IllegalStateException("容器操作失败："+e.getClass().getSimpleName()+" · "+e.getMessage()));}});}});
    }
    private void pick(String type,int request){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType(type);i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,request);}
    // APK metadata commit runs on the single-thread worker, and failure must be reported before success.
    @android.annotation.SuppressLint("ApplySharedPref")
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request==22){android.net.Uri uri=data.getData();worker.execute(()->{try{byte[] backup=store.exportRoutes().getBytes(java.nio.charset.StandardCharsets.UTF_8);if(backup.length>LocalStore.MAX_ROUTE_BACKUP_BYTES)throw new java.io.IOException("路线备份超过 64 MB，无法导出可恢复的备份");try(java.io.OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new java.io.IOException("无法打开备份文件");out.write(backup);out.flush();}runOnUiThread(()->{if(!isDestroyed())message("路线备份已导出");});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed())error(e);});}});return;}
        if(request==23){if(routeIsActive()){message("路线正在播放，已阻止导入合并");return;}android.net.Uri uri=data.getData();worker.execute(()->{boolean importing=false;try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
            if(in==null)throw new java.io.IOException("无法读取路线文件");java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>LocalStore.MAX_ROUTE_BACKUP_BYTES)throw new java.io.IOException("备份文件超过 64 MB");bytes.write(buffer,0,n);}
            if(!runtime.beginRouteImport())throw new IllegalStateException("路线正在播放或准备启动，已阻止导入合并");importing=true;
            int count=store.importRoutes(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));runOnUiThread(()->{runtime.finishRouteImportWithChanges();if(!isDestroyed()){syncImportedRoutes();message("已导入并合并 "+count+" 条路线");}});importing=false;
        }catch(Exception e){if(importing)runtime.finishRouteImport();runOnUiThread(()->{if(!isDestroyed())error(e);});}});return;}
        if(request==20){saveForm();if(timetableRecognition.start(data.getData()))message("正在本机识别图片…");else message("已有图片正在识别，请稍候");return;}
        if(request==21){message("正在复制并校验 APK…");worker.execute(()->{try{JSONObject item=ApkLibrary.importOriginal(getApplicationContext(),data.getData());JSONArray old=new JSONArray(store.prefs.getString("apks","[]"));boolean exists=false;for(int i=0;i<old.length();i++)if(old.getJSONObject(i).optString("sha256").equals(item.getString("sha256")))exists=true;if(!exists)old.put(item);if(!store.prefs.edit().putString("apks",old.toString()).commit())throw new java.io.IOException("保存 APK 元数据失败");runOnUiThread(()->{if(isDestroyed())return;if(tab==3)render();message("原始 APK 已保存；现在可以安装到容器");});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed())error(e);});}});}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.hasExtra("open_tab")){int target=Math.max(0,Math.min(3,intent.getIntExtra("open_tab",0)));intent.removeExtra("open_tab");switchTab(target);}}
    @Override protected void onPause(){saveForm();timetableRecognition.detach(timetableChanged);handler.removeCallbacks(refreshPlayback);activityResumed=false;if(map!=null)map.onPause();if(sheetMap!=null)sheetMap.onPause();super.onPause();}
    private void refreshPageWhenSafe(){if(activeSheet!=null&&activeSheet.isShowing())deferredPageRefresh=true;else{deferredPageRefresh=false;render();}}
    private void syncImportedRoutes(){long generation=runtime.routeLibraryGeneration();if(generation==routeLibraryGeneration)return;route=store.route();routeLibraryGeneration=generation;if(tab==1){saveForm();refreshPageWhenSafe();}}
    @Override protected void onResume(){super.onResume();activityResumed=true;timetableRecognition.attach(timetableChanged);if(tab==2||tab==3)refreshPageWhenSafe();updateSystemSetupStatus();String ocrMessage=timetableRecognition.takeMessage();if(!ocrMessage.isEmpty())message(ocrMessage);if(map!=null)map.onResume();if(sheetMap!=null)sheetMap.onResume();updateMediaPermissionStatus();handler.removeCallbacks(refreshPlayback);handler.post(refreshPlayback);}
    @Override protected void onDestroy(){
        if(apkPresentationLoader!=null)apkPresentationLoader.close();
        if(activeSheet!=null&&activeSheet.isShowing()){activeSheet.dismiss();activeSheet=null;}
        if(Build.VERSION.SDK_INT>=31)try{getWindow().getDecorView().setRenderEffect(null);}catch(Exception ignored){}
        timetableRecognition.detach(timetableChanged);handler.removeCallbacks(refreshPlayback);if(map!=null)map.onDestroy();if(sheetMap!=null){sheetMap.onDestroy();sheetMap=null;}worker.shutdown();super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state){saveForm();state.putInt("tab",tab);state.putBoolean("space_settings_expanded",spaceSettingsExpanded);super.onSaveInstanceState(state);if(map!=null)map.onSaveInstanceState(state);}
}


