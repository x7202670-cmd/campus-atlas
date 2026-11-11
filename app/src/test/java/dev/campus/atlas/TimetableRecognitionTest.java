package dev.campus.atlas;

import android.app.Application;
import android.net.Uri;
import dev.campus.atlas.core.Point;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,application=Application.class)
public class TimetableRecognitionTest {
    private LocalStore store;
    private FakeBackend backend;
    private TimetableRecognition session;
    private static final Uri IMAGE=Uri.parse("content://course/image");
    private static final class FakeBackend implements TimetableRecognition.Backend {
        TimetableRecognition.Result result;int calls;
        public void recognize(Uri uri,TimetableRecognition.Result result){this.result=result;calls++;}
    }
    @Before public void setup(){
        Application app=RuntimeEnvironment.getApplication();store=new LocalStore(app);store.prefs.edit().clear().commit();
        backend=new FakeBackend();session=new TimetableRecognition(app,backend);
        store.prefs.edit().putString("ocr","朝阳教 4-512").commit();
    }
    @Test public void simultaneousRequestsOnlyStartOneRecognition(){
        assertTrue(session.start(IMAGE));assertTrue(session.busy());assertFalse(session.start(IMAGE));assertEquals(1,backend.calls);
        backend.result.success("朝阳教 6-304","hash");assertFalse(session.busy());assertEquals("朝阳教 6-304",store.prefs.getString("ocr",""));
    }
    @Test public void emptyResultPreservesPreviousTextAndBindings()throws Exception{
        store.bind("朝阳教 4-512",new Point(30.26,120.12));session.start(IMAGE);backend.result.success(" \n ","hash");
        assertEquals("朝阳教 4-512",store.prefs.getString("ocr",""));assertNotNull(store.bound("朝阳教 4-512"));assertFalse(session.busy());
        assertTrue(session.takeMessage().contains("保留原结果"));assertEquals("",store.prefs.getString("ocr_image_sha256",""));
    }
    @Test public void recognitionFailureKeepsPreviousResultAndAllowsRetry(){
        session.start(IMAGE);backend.result.failure(new IllegalArgumentException("decode failure"));
        assertEquals("朝阳教 4-512",store.prefs.getString("ocr",""));assertFalse(session.busy());assertTrue(session.start(IMAGE));
    }
    @Test public void completionCannotOverwriteEditsMadeAfterJobStarted(){
        session.start(IMAGE);store.prefs.edit().putString("ocr","已人工核对的地点").commit();backend.result.success("错误识别","hash");
        assertEquals("已人工核对的地点",store.prefs.getString("ocr",""));assertTrue(session.takeMessage().contains("保留你的修改"));
    }
    @Test public void completionSurvivesListenerReplacementDuringRecreation(){
        int[] oldCalls={0},newCalls={0};Runnable old=()->oldCalls[0]++,replacement=()->newCalls[0]++;
        session.attach(old);session.start(IMAGE);session.detach(old);session.attach(replacement);session.detach(old);
        backend.result.success("朝阳教 6-304","hash");
        assertEquals(1,oldCalls[0]);assertEquals(1,newCalls[0]);assertFalse(session.busy());assertEquals("hash",store.prefs.getString("ocr_image_sha256",""));
    }
    @Test public void completionWhilePageIsAwayIsSavedForNextPage(){
        session.start(IMAGE);backend.result.success("朝阳教 6-304","hash");
        assertEquals("朝阳教 6-304",store.prefs.getString("ocr",""));assertTrue(session.takeMessage().contains("1 个候选"));assertEquals("",session.takeMessage());
    }
    @Test public void formattingAliasesRetainExistingCoordinates()throws Exception{
        Point p=new Point(30.26,120.12);store.bind("朝阳校区教４－５１２",p);
        assertEquals(p.lat,store.bound("朝阳教 4-512").lat,0);assertNull(store.bound("海淀教 4-512"));assertNull(store.bound("朝阳教 4-510"));
    }
    @Test public void conflictingAliasCoordinatesNeedExplicitReview()throws Exception{
        store.prefs.edit().putString("bindings","{\"朝阳教４－５１２\":[30.26,120.12],\"朝阳教4 - 512\":[30.27,120.13]}").commit();
        assertNull(store.bound("朝阳教 4-512"));assertNotNull(store.bound("朝阳教４－５１２"));
    }
    @Test public void savingReviewedCoordinatesReplacesOnlyFormattingAliases()throws Exception{
        store.bind("朝阳教４－５１２",new Point(30.26,120.12));store.bind("朝阳教4 - 510",new Point(30.28,120.14));
        store.bind("朝阳教 4-512",new Point(30.27,120.13));
        assertEquals(2,store.bindings().length());assertFalse(store.bindings().has("朝阳教４－５１２"));
        assertEquals(30.27,store.bound("朝阳教４－５１２").lat,0);assertEquals(30.28,store.bound("朝阳教4-510").lat,0);
    }
    @Test public void formattingOnlyRenameKeepsOneManualPlaceAndItsCoordinates()throws Exception{
        store.bind("朝阳教４－５１２",new Point(30.26,120.12));store.addManualPlace("朝阳教４－５１２");
        store.renameBinding("朝阳教４－５１２","朝阳教 4-512",new Point(30.27,120.13));
        assertEquals(1,store.bindings().length());assertEquals(java.util.Arrays.asList("朝阳教 4-512"),store.manualPlaces());
        assertEquals(30.27,store.bound("朝阳教4-512").lat,0);
    }
}
