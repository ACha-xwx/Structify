package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.studyroom.teaching.mapper.ClassroomLectureMapper;

import com.feng.dsagent.studyroom.service.StudyroomService;
import com.feng.dsagent.studyroom.teaching.service.ClassroomLectureService;
import com.feng.dsagent.studyroom.support.StudyroomMapperFactory;

import com.feng.dsagent.studyroom.*;
import com.feng.dsagent.studyroom.teaching.*;
import com.feng.dsagent.animation.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClassroomLectureServiceTest {
    @Test void regionsAreOwnedRevisionBoundAndIdempotentAndNeverPublishLearningProgress() throws Exception {
        var db=new FragmentTestDatabase();db.unit("u",0);db.fragment("f","u",0,"READY");
        db.jdbc.update("UPDATE studyroom_fragments SET content_revision=1 WHERE id='f'");
        var scene=new StudyroomScene("ppt","PPT","课件",List.of(new StudyroomScene.Item("p","看看栈顶。",null,null)),null,null,null,null);
        var step=new StudyroomView.Step("step-f","f","CONCEPT","栈","先认识栈。",List.of(),List.of(new StudyroomView.Resource("ppt","p","课件","",null,true)),0,false,null,null,new StudyroomScene.Orchestration(2,"理解栈","CONCEPT",scene,List.of()));
        var rooms=mock(StudyroomService.class);when(rooms.get(db.user,"room")).thenReturn(new StudyroomView("room",null,"栈","问题","简介",null,1,null,null,List.of(step),List.of()));
        var catalog=mock(StudyroomResourceCatalog.class);when(catalog.candidates(null)).thenReturn(List.of());when(catalog.codeDetails(List.of())).thenReturn(List.of());
        var service=new ClassroomLectureService(StudyroomMapperFactory.create(db.source, ClassroomLectureMapper.class),rooms,catalog,mock(DsvpLocalEngine.class),mock(DsvpAnimationAdapter.class),db.json);
        var region=new LectureRegion(UUID.randomUUID().toString(),"ppt","p",.1,.2,.3,.4,"这里是栈顶指针。");
        assertEquals(region,service.save(db.user,"run","f",1,region));assertEquals(region,service.save(db.user,"run","f",1,region));
        assertEquals(1,db.jdbc.queryForObject("SELECT COUNT(*) FROM studyroom_lecture_regions",Integer.class));
        var plan=service.get(db.user,"run","f",1);assertTrue(db.json.write(plan).contains("PPT_REGION"));
        assertEquals("PPT",((Map<?,?>)((List<?>)plan.get("cues")).getFirst()).get("kind"));
        assertThrows(RuntimeException.class,()->service.save(db.user+1,"run","f",1,region));
        assertThrows(RuntimeException.class,()->service.get(db.user,"run","f",2));
        assertThrows(RuntimeException.class,()->service.save(db.user,"run","f",1,new LectureRegion(UUID.randomUUID().toString(),"ppt","foreign",0,0,.2,.2,"越界资源")));
        service.remove(db.user,"run","f",1,region.id());assertEquals(List.of(),service.get(db.user,"run","f",1).get("regions"));
        assertEquals(0,db.jdbc.queryForObject("SELECT COUNT(*) FROM studyroom_learning_progress",Integer.class));
        db.jdbc.update("UPDATE study_rooms SET current_run_id=NULL WHERE id='room'");
        assertThrows(RuntimeException.class,()->service.get(db.user,"run","f",1));
    }
}
