import fs from 'node:fs';

function fixIntent(file) {
  let t = fs.readFileSync(file, 'utf8');
  if (!t.includes('import com.feng.dsagent.animation.controller.AnimationIntentController')) {
    t = t.replace(
      'package com.feng.dsagent.animation;\n',
      'package com.feng.dsagent.animation;\n\nimport com.feng.dsagent.animation.controller.AnimationIntentController;\n'
    );
  }
  if (!t.includes('import com.feng.dsagent.animation.service.AnimationChapterService')) {
    t = t.replace(
      /import org\.springframework\.jdbc\.core\.JdbcTemplate;\r?\n/,
      'import com.feng.dsagent.animation.service.AnimationChapterService;\n'
    );
  } else {
    t = t.replace(/import org\.springframework\.jdbc\.core\.JdbcTemplate;\r?\n/, '');
  }
  t = t.replace(
    /final JdbcTemplate jdbc = mock\(JdbcTemplate\.class\);/,
    'final AnimationChapterService chapters = mock(AnimationChapterService.class);'
  );
  t = t.replace(
    /when\(jdbc\.queryForList\(anyString\(\),\s*eq\(String\.class\),\s*eq\("([^"]+)"\)\)\)\.thenReturn\(List\.of\("([^"]*)"\)\);/g,
    'when(chapters.requirePublishedTitle(eq("$1"))).thenReturn("$2");'
  );
  t = t.replace(
    /when\(jdbc\.queryForList\(anyString\(\),\s*eq\(String\.class\),\s*eq\("([^"]+)"\)\)\)\.thenReturn\(List\.of\(\)\);/g,
    'when(chapters.requirePublishedTitle(eq("$1"))).thenThrow(new com.feng.dsagent.common.ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "CHAPTER_NOT_FOUND", "章节未发布或不存在"));'
  );
  t = t.replace(/,\s*jdbc,\s*mapper/g, ',chapters,mapper');
  fs.writeFileSync(file, t);
  console.log('fixed', file);
}

function fixPlan(file) {
  let t = fs.readFileSync(file, 'utf8');
  if (!t.includes('import com.feng.dsagent.animation.controller.AnimationIntentController')) {
    t = t.replace(
      'package com.feng.dsagent.animation;\n',
      'package com.feng.dsagent.animation;\n\nimport com.feng.dsagent.animation.controller.AnimationIntentController;\n'
    );
  }
  t = t.replace(
    /import org\.springframework\.jdbc\.core\.JdbcTemplate;\r?\n/,
    'import com.feng.dsagent.animation.service.AnimationChapterService;\n'
  );
  if (!t.includes('AnimationChapterService')) {
    t = t.replace(
      'import org.junit.jupiter.api.Test;\n',
      'import org.junit.jupiter.api.Test;\nimport com.feng.dsagent.animation.service.AnimationChapterService;\n'
    );
  }
  t = t.replace(
    /private final JdbcTemplate jdbc = mock\(JdbcTemplate\.class\);/,
    'private final AnimationChapterService chapters = mock(AnimationChapterService.class);'
  );
  // previous partial edit
  t = t.replace(
    /private final AnimationChapterService chapters = mock\(AnimationChapterService\.class\);/,
    'private final AnimationChapterService chapters = mock(AnimationChapterService.class);'
  );
  t = t.replace(/,\s*jdbc,\s*mapper/g, ', chapters, mapper');
  fs.writeFileSync(file, t);
  console.log('fixed', file);
}

fixIntent('backend/spring/src/test/java/com/feng/dsagent/animation/AnimationIntentControllerTest.java');
fixPlan('backend/spring/src/test/java/com/feng/dsagent/animation/AnimationPlanEndpointTest.java');

// ClassroomEventRepositoryTest
{
  const file =
    'backend/spring/src/test/java/com/feng/dsagent/studyroom/events/ClassroomEventRepositoryTest.java';
  let t = fs.readFileSync(file, 'utf8');
  t = t.replace(
    /service = new ClassroomEventService\([^;]+;/,
    'service = new ClassroomEventService(mock(ClassroomEventMapper.class), json, new DataSourceTransactionManager(source));'
  );
  if (!t.includes('import static org.mockito.Mockito.mock')) {
    t = t.replace(
      'package com.feng.dsagent.studyroom.events;\n',
      'package com.feng.dsagent.studyroom.events;\n\nimport static org.mockito.Mockito.mock;\n'
    );
  }
  if (!t.includes('ClassroomEventMapper')) {
    t = t.replace(
      'import com.feng.dsagent.studyroom.events.service.ClassroomEventService;\n',
      'import com.feng.dsagent.studyroom.events.mapper.ClassroomEventMapper;\nimport com.feng.dsagent.studyroom.events.service.ClassroomEventService;\n'
    );
  }
  fs.writeFileSync(file, t);
  console.log('fixed', file);
}
