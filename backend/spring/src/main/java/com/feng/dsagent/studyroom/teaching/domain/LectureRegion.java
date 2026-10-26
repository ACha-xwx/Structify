package com.feng.dsagent.studyroom.teaching.domain;

/** Geometry refers to the image itself, never to the padded viewport. */
public record LectureRegion(String id,String sceneId,String resourceId,double x,double y,double width,double height,String narration) {
    public LectureRegion {
        if(id==null||!id.matches("[a-fA-F0-9-]{36}")||sceneId==null||sceneId.isBlank()||sceneId.length()>160
            ||resourceId==null||resourceId.isBlank()||resourceId.length()>240||narration==null||narration.isBlank()||narration.length()>800
            ||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(width)||!Double.isFinite(height)
            ||x<0||y<0||width<.01||height<.01||x+width>1.000001||y+height>1.000001)
            throw new IllegalArgumentException("LECTURE_REGION_INVALID");
    }
}
