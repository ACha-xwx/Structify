/**
 * Align studyroom unit-test constructors with MyBatis + .service package signatures.
 * Mapper dependencies are mocked so sources compile; DB-heavy tests may need later rewiring.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const t = (...p) => path.join(root, 'backend/spring/src/test/java/com/feng/dsagent', ...p);

function ensureImport(text, imp) {
  if (text.includes(imp)) return text;
  return text.replace(/^(package .+;\r?\n)/m, `$1\n${imp}\n`);
}

function ensureStaticMock(text) {
  if (text.includes('import static org.mockito.Mockito')) return text;
  if (text.includes('import static org.mockito.Mockito.')) return text;
  return ensureImport(text, 'import static org.mockito.Mockito.mock;');
}

const edits = [
  {
    file: t('studyroom/checks/ClassroomQuestionBankTest.java'),
    apply: (s) =>
      s
        .replace(
          'new ClassroomCheckService(null,null,null)',
          'new ClassroomCheckService(null,null,null,null)'
        )
        .replace(
          'new ClassroomCheckService(null,new OutlineJson(new ObjectMapper()),null)',
          'new ClassroomCheckService(null,null,new OutlineJson(new ObjectMapper()),null)'
        ),
  },
  {
    file: t('studyroom/fragment/ClassroomCheckServiceTest.java'),
    apply: (s) => {
      s = ensureImport(s, 'import com.feng.dsagent.studyroom.checks.mapper.ClassroomCheckMapper;');
      s = ensureStaticMock(s);
      s = s.replace(
        /new ClassroomCheckService\(db\.store,db\.json,jobs\)/g,
        'new ClassroomCheckService(db.store,mock(ClassroomCheckMapper.class),db.json,jobs)'
      );
      s = s.replace(
        /new ClassroomCheckService\(db\.store,db\.json,new JdbcGenerationRepository\(([^)]+)\)\)/g,
        'new ClassroomCheckService(db.store,mock(ClassroomCheckMapper.class),db.json,new JdbcGenerationRepository($1))'
      );
      return s;
    },
  },
  {
    file: t('studyroom/events/ClassroomEventRepositoryTest.java'),
    apply: (s) => {
      s = ensureImport(s, 'import com.feng.dsagent.studyroom.events.mapper.ClassroomEventMapper;');
      s = ensureStaticMock(s);
      return s.replace(
        /new ClassroomEventService\(new JdbcClassroomEventRepository\([^)]+\),\s*new DataSourceTransactionManager\(source\)\)/,
        'new ClassroomEventService(mock(ClassroomEventMapper.class), json, new DataSourceTransactionManager(source))'
      );
    },
  },
  {
    file: t('studyroom/fragment/ClassroomLectureServiceTest.java'),
    apply: (s) => {
      s = ensureImport(s, 'import com.feng.dsagent.studyroom.teaching.mapper.ClassroomLectureMapper;');
      s = ensureStaticMock(s);
      return s.replace(
        /new ClassroomLectureService\(new JdbcFragmentRepository\(db\.store,db\.json\),/,
        'new ClassroomLectureService(mock(ClassroomLectureMapper.class),'
      );
    },
  },
  {
    file: t('studyroom/library/CourseLibraryServiceTest.java'),
    apply: (s) => {
      s = ensureImport(s, 'import com.feng.dsagent.studyroom.library.mapper.CourseLibraryMapper;');
      s = ensureStaticMock(s);
      return s.replace(
        /new CourseLibraryService\(store,json\)/,
        'new CourseLibraryService(mock(CourseLibraryMapper.class),json)'
      );
    },
  },
  {
    file: t('studyroom/fragment/FragmentLearningServiceTest.java'),
    apply: (s) => {
      s = ensureImport(s, 'import com.feng.dsagent.studyroom.fragment.mapper.FragmentLearningMapper;');
      s = ensureStaticMock(s);
      return s.replace(
        /new FragmentLearningService\(repo,jobs,scheduler,db\.json\)/,
        'new FragmentLearningService(mock(FragmentLearningMapper.class),scheduler,db.json)'
      );
    },
  },
  {
    file: t('studyroom/fragment/FragmentPublicationServiceTest.java'),
    apply: (s) => {
      s = ensureImport(s, 'import com.feng.dsagent.studyroom.fragment.mapper.FragmentPublicationMapper;');
      s = ensureStaticMock(s);
      return s.replace(
        /new FragmentPublicationService\(repo,jobs,db\.json\)/,
        'new FragmentPublicationService(mock(FragmentPublicationMapper.class),db.json)'
      );
    },
  },
  {
    file: t('studyroom/fragment/FragmentReadServiceTest.java'),
    apply: (s) => {
      s = ensureImport(s, 'import com.feng.dsagent.studyroom.fragment.mapper.FragmentReadMapper;');
      s = ensureStaticMock(s);
      return s.replace(
        /new FragmentReadService\(new JdbcFragmentRepository\(db\.store,db\.json\),db\.json,mock\(CodeExecutionReadiness\.class\)\)/,
        'new FragmentReadService(mock(FragmentReadMapper.class),db.json,mock(CodeExecutionReadiness.class))'
      );
    },
  },
  {
    file: t('studyroom/fragment/ProgressiveOutlineConfirmationTest.java'),
    apply: (s) => {
      s = ensureImport(s, 'import com.feng.dsagent.studyroom.draft.mapper.OutlineMapper;');
      s = ensureStaticMock(s);
      return s.replace(
        /new OutlineService\(db\.store,jobs,db\.json,new OutlineValidator\(\),feasibility,compiler,fragments,new PreparationScheduler\(fragments,jobs,db\.json\)\)/,
        'new OutlineService(db.store,mock(OutlineMapper.class),jobs,db.json,new OutlineValidator(),feasibility,compiler,fragments,new PreparationScheduler(fragments,jobs,db.json))'
      );
    },
  },
  {
    file: t('studyroom/fragment/GenerationCallStreamingTest.java'),
    apply: (s) => {
      s = ensureImport(s, 'import com.feng.dsagent.studyroom.generation.mapper.GenerationCallMapper;');
      s = ensureImport(s, 'import org.springframework.transaction.PlatformTransactionManager;');
      s = ensureStaticMock(s);
      // GenerationCallService(jobs, quota) -> (mapper, txManager, quota)
      return s.replace(
        /new GenerationCallService\(jobs,\s*new AiQuotaExecution\(\)/,
        'new GenerationCallService(mock(GenerationCallMapper.class), mock(PlatformTransactionManager.class), new AiQuotaExecution()'
      );
    },
  },
  {
    file: t('studyroom/fragment/FragmentExecutionBudgetIntegrationTest.java'),
    apply: (s) => {
      s = ensureImport(s, 'import com.feng.dsagent.studyroom.generation.mapper.GenerationCallMapper;');
      s = ensureImport(s, 'import org.springframework.transaction.PlatformTransactionManager;');
      s = ensureStaticMock(s);
      return s.replace(
        /new GenerationCallService\(jobs,model\)/,
        'new GenerationCallService(mock(GenerationCallMapper.class), mock(PlatformTransactionManager.class), model)'
      );
    },
  },
  {
    file: t('studyroom/fragment/ClassroomQuestionServiceTest.java'),
    apply: (s) => {
      s = ensureImport(s, 'import com.feng.dsagent.studyroom.events.mapper.ClassroomEventMapper;');
      s = ensureImport(s, 'import com.feng.dsagent.studyroom.questions.mapper.ClassroomQuestionMapper;');
      s = ensureImport(s, 'import com.feng.dsagent.studyroom.generation.mapper.GenerationCallMapper;');
      s = ensureImport(s, 'import org.springframework.transaction.PlatformTransactionManager;');
      s = ensureStaticMock(s);
      s = s.replace(
        /new ClassroomEventService\(new JdbcClassroomEventRepository\(db\.store,db\.json\),manager\)/g,
        'new ClassroomEventService(mock(ClassroomEventMapper.class), db.json, manager)'
      );
      s = s.replace(
        /new ClassroomQuestionService\(repository,jobs,events,view\)/,
        'new ClassroomQuestionService(mock(ClassroomQuestionMapper.class),db.store,db.json,jobs,events,view)'
      );
      s = s.replace(
        /new ClassroomQuestionService\(new JdbcClassroomQuestionRepository\(db\.store,db\.json\),jobs,events,rooms\)/,
        'new ClassroomQuestionService(mock(ClassroomQuestionMapper.class),db.store,db.json,jobs,events,rooms)'
      );
      s = s.replace(
        /new GenerationCallService\(jobs,model\)/,
        'new GenerationCallService(mock(GenerationCallMapper.class), mock(PlatformTransactionManager.class), model)'
      );
      return s;
    },
  },
];

for (const e of edits) {
  if (!fs.existsSync(e.file)) {
    console.warn('missing', e.file);
    continue;
  }
  const before = fs.readFileSync(e.file, 'utf8');
  const after = e.apply(before);
  if (after !== before) {
    fs.writeFileSync(e.file, after);
    console.log('updated', path.relative(root, e.file));
  } else {
    console.log('unchanged', path.relative(root, e.file));
  }
}
