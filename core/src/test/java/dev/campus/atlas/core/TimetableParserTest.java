package dev.campus.atlas.core;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class TimetableParserTest {
    @Test public void keepsExistingBuildingAndSportsFieldCandidates(){
        assertEquals(Arrays.asList("东一教学楼 101","朝阳田径场"),
                TimetableParser.candidates("星期一\n08:00\n1\n东一教学楼 101\n朝阳田径场"));
    }

    @Test public void combinesCampusFragmentsWithRoomCodesAndSportsVenues(){
        List<String> found=TimetableParser.candidates(
                "20-21秋冬\n一\n08:00\n1\n示例体育课\n朝阳新桥\n门足球场\n"
                +"示例课程甲\n朝阳教\n4-512\n"
                +"示例课程乙\n朝阳教 4－510\n"
                +"示例课程丙\n朝阳教\n6—304");
        assertEquals(Arrays.asList("朝阳新桥门足球场","朝阳教 4-512","朝阳教 4-510","朝阳教 6-304"),found);
    }

    @Test public void removesDuplicateRoomCandidatesAcrossCourseCells(){
        assertEquals(Arrays.asList("朝阳教 4-512"),TimetableParser.candidates(
                "示例课程甲\n朝阳教 4-512\n示例课程乙\n朝阳教 4-512"));
    }

    @Test public void joinsSplitBuildingAcrossOcrLineBreaks(){
        String raw="示例课程\n朝阳科技\n实验\n大 楼";
        assertEquals("示例课程\n朝阳科技实验大楼",TimetableParser.normalizeOcrText(raw));
        assertTrue(TimetableParser.candidates(raw).contains("朝阳科技实验大楼"));
    }

    @Test public void mergesTypographyAndCampusAliasesButKeepsDifferentRoomsAndCampuses(){
        assertEquals(Arrays.asList("朝阳教 4-512","朝阳教 4-510","海淀教 4-512"),TimetableParser.candidates(
                "朝阳教 ４－５１２\n朝 阳 教4 – 512\n示范大学朝阳校区教 4-512\n朝阳教 4-510\n海淀教4-512"));
    }
    @Test public void doesNotEmitBothBuildingAndRoomForOneVenue(){
        assertEquals(Arrays.asList("朝阳教学楼 4-512"),TimetableParser.candidates("朝阳教学楼 4-512"));
    }
    @Test public void ignoresWeekRangesTimesAndCourseTitles(){
        assertTrue(TimetableParser.candidates("20-21秋冬\n1-16周\n4-512\n08:00-09:35\n专业课程A\n专业课程B\n通用通识课\n高等数学").isEmpty());
    }
    @Test public void doesNotCarryAnExistingRoomIntoAnotherCell(){
        assertEquals(Arrays.asList("朝阳教 4-512"),TimetableParser.candidates("朝阳教 4-512\n6-304"));
    }
    @Test public void blankLinesAndCellSeparatorsStopContext(){
        assertTrue(TimetableParser.candidates("朝阳教\n\n4-512\n海淀教 | 6-304").isEmpty());
    }
    @Test public void combinesBuildingWithSimpleRoomOnNextLine(){
        assertEquals(Arrays.asList("东一教学楼 101"),TimetableParser.candidates("东一教学楼\n101"));
    }
    @Test public void preservesBothVenuesOnTheSameLine(){
        assertEquals(Arrays.asList("朝阳教 4-512","海淀东一教学楼 101"),TimetableParser.candidates("朝阳教4-512；海淀东一教学楼101"));
    }
    @Test public void extractsSameCampusVenuesWithoutJoiningThem(){
        assertEquals(Arrays.asList("朝阳教 4-512","朝阳教 6-304"),TimetableParser.candidates("朝阳教4-512 朝阳教6-304"));
    }
    @Test public void removesCourseAndTeacherMetadata(){
        assertEquals(Arrays.asList("朝阳教 4-512","东一教学楼 101"),TimetableParser.candidates(
                "示例课程甲 朝阳教4-512 (1-16周) 张老师\n上课地点：东一教学楼101"));
    }
    @Test public void extractsVenueWithFullWidthColonPrefix(){
        assertEquals(Arrays.asList("东一教学楼 101"),TimetableParser.candidates("上课地址：东一教学楼 101"));
    }
    @Test public void doesNotGuessAmbiguousRoomCharacters(){
        assertEquals(Arrays.asList("朝阳教 4-B12"),TimetableParser.candidates("朝阳教4-B12\n朝阳教4-3I2\n朝阳教4-3l2"));
    }
    @Test public void uniquePlacesNormalizesManualAndOcrEntries(){
        assertEquals(Arrays.asList("朝阳教4-512","朝阳教 4-510"),TimetableParser.uniquePlaces(
                Arrays.asList("朝阳教４－５１２","朝 阳 教 4 - 512","朝阳教 4-510")));
    }
    @Test public void knownBuildingRepairCannotBridgeSeparateCells(){
        assertEquals("朝阳科技\n\n实验大楼",TimetableParser.normalizeOcrText("朝阳科技\n\n实验大楼"));
    }
    @Test public void repairsOnlyKnownCampusClassroomShorthand(){
        assertEquals(Arrays.asList("朝阳教 4-512","朝阳救援中心楼"),TimetableParser.candidates("朝阳救\n4-512\n朝阳救援中心楼"));
    }
    @Test public void correctsKnownCampusMisreadsOnlyInClassroomContext(){
        assertEquals(Arrays.asList("朝阳教 4-512","海淀教 6-304"),TimetableParser.candidates("朝阳数\n4-512\n海淀教\n6-304"));
    }
}
