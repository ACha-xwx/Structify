package com.feng.dsagent.studyroom;

import java.util.*;
import tools.jackson.databind.ObjectMapper;

public final class StudyroomSceneAdapter {
    private StudyroomSceneAdapter() {}
    public static StudyroomScene.Orchestration parse(ObjectMapper mapper, String json, List<StudyroomView.Resource> resources) {
        try {
            var result = mapper.readValue(json, StudyroomScene.Orchestration.class);
            if (result.scenes().size() > 3) throw new IllegalArgumentException("Too many scenes");
            Set<String> sceneIds = new HashSet<>();
            for (var scene : result.scenes()) {
                if (!sceneIds.add(scene.id()) || scene.purpose().length() > 200) throw new IllegalArgumentException("场景ID重复或教学用途超过200字");
                if(scene.items().size()>(scene.kind().equals("PPT")?12:1))throw new IllegalArgumentException("当前步骤的"+scene.kind()+"最多允许"+(scene.kind().equals("PPT")?12:1)+"个资源；不要在单步合并多个动画或代码，其他过程已有独立步骤");
                Set<String> itemIds = new HashSet<>();
                for (var item : scene.items()) {
                    if (!itemIds.add(item.resourceId()) || resources.stream().noneMatch(r -> r.id().equals(item.resourceId()) && r.type().equalsIgnoreCase(scene.kind())))
                        throw new IllegalArgumentException("Scene references unbound resource");
                    if (!scene.kind().equals("C_CODE") && item.startLine() != null) throw new IllegalArgumentException("Only code has line ranges");
                }
                if (scene.task() != null && scene.task().length() > 1000 || scene.expectedObservation() != null && scene.expectedObservation().length() > 1000)
                    throw new IllegalArgumentException("Experiment text too long");
            }
            return result;
        } catch (IllegalArgumentException error) { throw error; }
        catch (Exception error) { throw new IllegalArgumentException("Invalid orchestration JSON", error); }
    }
}
