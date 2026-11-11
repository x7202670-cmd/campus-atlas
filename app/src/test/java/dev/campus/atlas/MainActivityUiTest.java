package dev.campus.atlas;

import android.app.*;
import android.content.Intent;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowDialog;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

/** Exercises the native UI with preview output; no map or container hooks are enabled. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class MainActivityUiTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private LocalStore store;
    @Before public void setup() throws Exception {
        Application app=RuntimeEnvironment.getApplication();app.getSharedPreferences("atlas",0).edit().clear().commit();app.getSharedPreferences("playback_recovery",0).edit().clear().commit();
        Field singleton=PlaybackRuntime.class.getDeclaredField("instance");singleton.setAccessible(true);singleton.set(null,null);store=new LocalStore(app);
    }
    @After public void teardown(){if(controller!=null)controller.pause().stop().destroy();}
    private void open(int tab){controller=Robolectric.buildActivity(MainActivity.class,new Intent(RuntimeEnvironment.getApplication(),MainActivity.class).putExtra("open_tab",tab)).setup();activity=controller.get();}
    private View root(){return activity.getWindow().getDecorView();}
    private View description(View view,String label){if(label.equals(String.valueOf(view.getContentDescription())))return view;if(view instanceof ViewGroup){ViewGroup parent=(ViewGroup)view;for(int i=0;i<parent.getChildCount();i++){View match=description(parent.getChildAt(i),label);if(match!=null)return match;}}return null;}
    private TextView text(View view,String prefix){if(view instanceof TextView&&((TextView)view).getText().toString().startsWith(prefix))return (TextView)view;if(view instanceof ViewGroup){ViewGroup parent=(ViewGroup)view;for(int i=0;i<parent.getChildCount();i++){TextView match=text(parent.getChildAt(i),prefix);if(match!=null)return match;}}return null;}

    @Test public void bottomInsetExpandsOnlyWhenOutputControlsAreVisible() throws Exception {
        open(0);Field bodyField=MainActivity.class.getDeclaredField("body");bodyField.setAccessible(true);View body=(View)bodyField.get(activity);
        int idle=body.getPaddingBottom();text(root(),"预览此位置").performClick();int active=body.getPaddingBottom();
        assertTrue(active>idle);text(root(),"停止并还原").performClick();assertEquals(idle,body.getPaddingBottom());
    }
    @Test public void touchingNavigationRequestsSystemHapticFeedback() {
        open(0);View item=description(root(),"路线");
        MotionEvent down=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,1,1,0);
        item.dispatchTouchEvent(down);down.recycle();
        assertEquals(HapticFeedbackConstants.VIRTUAL_KEY,Shadows.shadowOf(item).lastHapticFeedbackPerformed());
        MotionEvent cancel=MotionEvent.obtain(0,1,MotionEvent.ACTION_CANCEL,1,1,0);item.dispatchTouchEvent(cancel);cancel.recycle();
    }

    @Test public void instantApplyBoxTogglesAndPersists() {
        open(0);Switch toggle=(Switch)description(root(),"点选即生效");
        assertNotNull(toggle);assertFalse(toggle.isChecked());
        ViewGroup box=(ViewGroup)toggle.getParent();assertNotNull(box.getBackground());
        assertSame(box,text(box,"点选即生效").getParent());
        box.performClick();assertTrue(toggle.isChecked());assertTrue(store.prefs.getBoolean("instant_apply",false));
        toggle.performClick();assertFalse(toggle.isChecked());assertFalse(store.prefs.getBoolean("instant_apply",true));
    }
    @Test public void permissionResultUpdatesOpenSystemSetupStatus() throws Exception {
        Application app=RuntimeEnvironment.getApplication();SystemLocationOutputTest.FakeBackend backend=new SystemLocationOutputTest.FakeBackend();backend.reason="缺少权限";
        PlaybackRuntime runtime=new PlaybackRuntime(app,new SystemLocationOutput(app,backend));Field singleton=PlaybackRuntime.class.getDeclaredField("instance");singleton.setAccessible(true);singleton.set(null,runtime);
        open(0);text(root(),"系统定位授权与设置").performClick();Dialog dialog=ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing());
        Field statusField=MainActivity.class.getDeclaredField("systemSetupStatus");statusField.setAccessible(true);TextView status=(TextView)statusField.get(activity);assertEquals("缺少权限",status.getText().toString());
        backend.reason="";activity.onRequestPermissionsResult(32,new String[0],new int[0]);assertTrue(dialog.isShowing());assertEquals(runtime.systemLocation.status(),status.getText().toString());
    }
    @Test public void recreatedRoutePageRefreshesAfterBackgroundImportCompletes() throws Exception {
        open(1);PlaybackRuntime runtime=PlaybackRuntime.get(activity);assertTrue(runtime.beginRouteImport());
        controller.recreate();activity=controller.get();store.createRoute("导入后的路线");runtime.finishRouteImportWithChanges();
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1100));assertNotNull(text(root(),"导入后的路线"));
    }

    @Test public void distancePacePresetsSetExpectedDurationWithoutChangingInterval() {
        store.prefs.edit().putString("interval","5").commit();open(1);
        assertNotNull(text(root(),"管理路线"));
        assertTrue(text(root(),"闭合并循环路线").isShown());
        String[][] cases={{"1 公里","3.0","3"},{"5 公里","4.0","20"},{"10 公里","5.0","50"}};
        for(String[] item:cases){
            text(root(),item[0]).performClick();
            assertEquals(item[1],store.prefs.getString("pace",null));
            assertEquals(item[2],store.prefs.getString("duration",null));
            assertEquals("5",store.prefs.getString("interval",null));
            assertTrue(text(root(),item[0]).isSelected());
        }
        ((EditText)description(root(),"配速 · 分/公里")).setText("6");
        for(String[] item:cases)assertFalse(text(root(),item[0]).isSelected());
    }
    @Test public void routeManagerOpensCenteredAndClosesAfterCreatingRoute(){
        open(1);text(root(),"管理路线").performClick();Dialog manager=ShadowDialog.getLatestDialog();assertTrue(manager.isShowing());
        assertEquals(Gravity.CENTER,manager.getWindow().getAttributes().gravity);
        View managerContent=manager.getWindow().getDecorView();assertNotNull(text(managerContent,"导入并合并"));assertNotNull(text(managerContent,"导出全部 JSON"));
        text(managerContent,"＋ 新建空白").performClick();AlertDialog name=(AlertDialog)ShadowDialog.getLatestDialog();assertTrue(name.isShowing());
        name.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertFalse(manager.isShowing());assertNotNull(text(root(),"新路线"));
        text(root(),"管理路线").performClick();Dialog reopened=ShadowDialog.getLatestDialog();assertTrue(reopened.isShowing());assertNotNull(text(reopened.getWindow().getDecorView(),"新路线"));
    }

    @Test public void coordinateEditsDoNotPublishUntilAppliedAndStopRestores(){
        open(0);text(root(),"坐标修改").performClick();
        ((EditText)description(root(),"纬度 · WGS84")).setText("30.26");((EditText)description(root(),"经度 · WGS84")).setText("120.12");
        PlaybackRuntime runtime=PlaybackRuntime.get(activity);assertNull(runtime.output.last());text(root(),"预览此位置").performClick();assertEquals(30.26,runtime.output.last().lat,0);assertEquals(120.12,runtime.output.last().lon,0);text(root(),"停止并还原").performClick();assertNull(runtime.output.last());
    }
    @Test public void allTabsNavigateAndCourseEditsSurvivePageChanges(){
        store.prefs.edit().putString("ocr","朝阳教 4-512").commit();open(2);EditText editor=(EditText)description(root(),"识别文本");editor.setText("朝阳科技\n实验大楼");description(root(),"定位").performClick();description(root(),"路线").performClick();assertNotNull(text(root(),"开始播放路线"));description(root(),"空间").performClick();assertNotNull(text(root(),"容器服务状态"));description(root(),"课表").performClick();assertEquals("朝阳科技实验大楼",((EditText)description(root(),"识别文本")).getText().toString());assertNotNull(text(root(),"朝阳科技实验大楼"));
    }
    @Test public void unfinishedRestoreDisablesNewPositionOutput(){
        PlaybackRuntime.get(RuntimeEnvironment.getApplication()).player.requireRecovery("需要重试还原");open(0);assertFalse(text(root(),"预览此位置").isEnabled());assertNotNull(text(root(),"重试还原"));assertNull(PlaybackRuntime.get(activity).output.last());
    }
    @Test public void targetPickerHasSeparateOptionsAndKeepsPreviewSelectable(){open(0);View chip=description(root(),"切换位置接收目标");assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT,chip.getLayoutParams().width);chip.performClick();Dialog dialog=ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing());View content=dialog.getWindow().getDecorView();View preview=description(content,"仅本地预览，当前目标");View system=description(content,"手机系统模拟定位，选择目标");assertNotNull(preview);assertNotNull(system);assertTrue(((ViewGroup.MarginLayoutParams)system.getLayoutParams()).topMargin>0);preview.performClick();assertFalse(dialog.isShowing());assertTrue(PlaybackRuntime.get(activity).output.description().startsWith("仅应用内预览"));}
    @Test public void cancellingPlaceDrawerDoesNotSavePendingChanges(){
        open(2);text(root(),"＋ 添加").performClick();Dialog dialog=ShadowDialog.getLatestDialog();assertTrue(dialog.isShowing());EditText name=(EditText)description(dialog.getWindow().getDecorView(),"地点名称");name.setText("未保存教学楼");text(dialog.getWindow().getDecorView(),"取消").performClick();assertFalse(dialog.isShowing());assertNull(store.bound("未保存教学楼"));assertTrue(store.manualPlaces().isEmpty());
    }
    @Test public void returningToOpenPlaceDrawerKeepsEditsAndDefersPageRefresh(){open(2);View oldTitle=text(root(),"课表图像提取");text(root(),"＋ 添加").performClick();Dialog dialog=ShadowDialog.getLatestDialog();EditText name=(EditText)description(dialog.getWindow().getDecorView(),"地点名称");name.setText("待核对教学楼");controller.pause().stop().restart().start().resume().visible();assertTrue(dialog.isShowing());assertEquals("待核对教学楼",name.getText().toString());assertSame(oldTitle,text(root(),"课表图像提取"));text(dialog.getWindow().getDecorView(),"取消").performClick();assertFalse(dialog.isShowing());assertNull(store.bound("待核对教学楼"));}
    @Test public void timetablePanelsSwitchAndKeepEditedText(){open(2);assertTrue(text(root(),"地点候选与坐标核对").isShown());assertFalse(description(root(),"识别文本").isShown());text(root(),"课表图片 / 文字").performClick();EditText editor=(EditText)description(root(),"识别文本");assertTrue(editor.isShown());editor.setText("朝阳教 6-304");text(root(),"地点坐标绑定").performClick();assertTrue(text(root(),"地点候选与坐标核对").isShown());text(root(),"课表图片 / 文字").performClick();assertEquals("朝阳教 6-304",((EditText)description(root(),"识别文本")).getText().toString());}
    @Test public void spacePanelsSwitchBetweenAppsAndPermissions(){open(3);assertTrue(description(root(),"导入 APK").isShown());assertFalse(text(root(),"精确位置").isShown());text(root(),"容器状态与权限").performClick();assertTrue(text(root(),"精确位置").isShown());assertFalse(description(root(),"导入 APK").isShown());text(root(),"空间应用").performClick();assertTrue(description(root(),"导入 APK").isShown());}
    @Test public void openingFromPlaybackNotificationShowsRouteControls(){
        open(0);controller.newIntent(new Intent(activity,MainActivity.class).putExtra("open_tab",1));assertNotNull(text(root(),"路线回放"));assertNotNull(text(root(),"开始播放路线"));assertNotNull(text(root(),"展开参数微调"));
    }
    @Test public void recreationKeepsTheUsersCurrentTabAfterTheLaunchIntentWasConsumed(){
        open(0);description(root(),"课表").performClick();controller.recreate();activity=controller.get();assertNotNull(text(root(),"课表地点"));assertNotNull(text(root(),"课表图像提取"));
    }
    @Test public void manualAndRecognizedFormattingAliasesShareOneCandidate()throws Exception{
        store.addManualPlace("朝阳教4-512");store.bind("朝阳教4-512",new dev.campus.atlas.core.Point(30.26,120.12));
        store.prefs.edit().putString("ocr","朝阳教 ４－５１２\n朝 阳 教4-512").commit();open(2);
        assertEquals(1,countText(root(),"朝阳教4-512"));assertNotNull(description(root(),"应用地点"));
    }
    @Test public void updatingCandidatesUsesEditedTextWithoutAppendingOldPlaces(){
        store.prefs.edit().putString("ocr","朝阳教4-512").commit();open(2);
        ((EditText)description(root(),"识别文本")).setText("朝阳教6-304\n朝阳教 ６－３０４");text(root(),"提取地点").performClick();
        assertNotNull(text(root(),"朝阳教 6-304"));assertNull(text(root(),"朝阳教 4-512"));
    }
    @Test public void inFlightRecognitionSurvivesNavigationAndRecreationWithoutStartingAgain()throws Exception{
        final TimetableRecognition.Result[] pending={null};int[] requests={0};
        TimetableRecognition session=new TimetableRecognition(RuntimeEnvironment.getApplication(),(uri,result)->{requests[0]++;pending[0]=result;});
        Field singleton=TimetableRecognition.class.getDeclaredField("instance");singleton.setAccessible(true);singleton.set(null,session);
        open(2);session.start(android.net.Uri.parse("content://course/image"));assertFalse(text(root(),"正在本机识别").isEnabled());
        description(root(),"定位").performClick();description(root(),"课表").performClick();
        controller.recreate();activity=controller.get();assertFalse(text(root(),"正在本机识别").isEnabled());assertEquals(1,requests[0]);
        pending[0].success("朝阳教6-304","hash");assertTrue(text(root(),"＋ 选择课表图片（本机识别）").isEnabled());
        assertEquals("朝阳教6-304",((EditText)description(root(),"识别文本")).getText().toString());assertNotNull(text(root(),"朝阳教 6-304"));
    }
    @Test public void coordinateReuseDoesNotChooseBetweenConflictingAliases(){
        String bindings="{\"朝阳教４－５１２\":[30.26,120.12],\"朝阳教4 - 512\":[30.27,120.13]}";
        store.prefs.edit().putString("bindings",bindings).putString("ocr","朝阳教4-512").commit();open(2);
        text(root(),"复用同名已存坐标").performClick();assertEquals(bindings,store.prefs.getString("bindings",""));
        assertNotNull(description(root(),"核对并绑定坐标"));
    }
    private int countText(View view,String value){int count=view instanceof TextView&&value.contentEquals(((TextView)view).getText())?1:0;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)count+=countText(group.getChildAt(i),value);}return count;}
}
