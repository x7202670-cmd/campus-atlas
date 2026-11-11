package dev.campus.atlas.core;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class TimetableLayoutTest {
    private TimetableLayout.Line line(String text,int x,int y,int width){return new TimetableLayout.Line(text,x,y,x+width,y+20);}
    @Test public void pairsFragmentsVerticallyInsteadOfAcrossAdjacentColumns(){
        String text=TimetableLayout.text(Arrays.asList(line("朝阳教",20,20,80),line("海淀教",220,20,100),
                line("4-512",30,45,70),line("6-304",235,45,70)));
        assertEquals(Arrays.asList("朝阳教 4-512","海淀教 6-304"),TimetableParser.candidates(text));
    }
    @Test public void distantRowsCannotBorrowCampusContext(){
        String text=TimetableLayout.text(Arrays.asList(line("朝阳教",20,20,80),line("4-512",30,200,70)));
        assertTrue(TimetableParser.candidates(text).isEmpty());
    }
    @Test public void separateColumnsCannotBorrowCampusContext(){
        String text=TimetableLayout.text(Arrays.asList(line("朝阳教",20,20,80),line("4-512",220,45,70)));
        assertTrue(TimetableParser.candidates(text).isEmpty());
    }
    @Test public void overlappingDuplicateLinesAreRemoved(){
        assertEquals("朝阳教4-512",TimetableLayout.text(Arrays.asList(line("朝阳教4-512",20,20,120),line("朝阳教４－５１２",21,21,120))));
    }
    @Test public void repeatedCoursesInSeparateCellsRemainInEditableTextButDeduplicatePlaces(){
        String text=TimetableLayout.text(Arrays.asList(line("朝阳教4-512",20,20,120),line("朝阳教4-512",220,20,120)));
        assertEquals("朝阳教4-512\n\n朝阳教4-512",text);
        assertEquals(Arrays.asList("朝阳教 4-512"),TimetableParser.candidates(text));
    }
    @Test public void shuffledInputStillProducesStableColumnGroups(){
        assertEquals("朝阳教\n4-512\n\n海淀教\n6-304",TimetableLayout.text(Arrays.asList(
                line("6-304",235,45,70),line("4-512",30,45,70),line("海淀教",220,20,100),line("朝阳教",20,20,80))));
    }
}
