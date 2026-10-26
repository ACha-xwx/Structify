/**
 * Fix admin/review service imports after accidental git checkout, then wire ErrorCodes.
 * UTF-8 safe.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const files = [
  'backend/spring/src/main/java/com/feng/dsagent/admin/service/AdminService.java',
  'backend/spring/src/main/java/com/feng/dsagent/admin/service/AdminClassService.java',
  'backend/spring/src/main/java/com/feng/dsagent/admin/service/AdminKnowledgeService.java',
  'backend/spring/src/main/java/com/feng/dsagent/admin/service/BackgroundTaskService.java',
  'backend/spring/src/main/java/com/feng/dsagent/admin/service/AdminUserLearningService.java',
  'backend/spring/src/main/java/com/feng/dsagent/review/service/ReviewService.java',
];

const importRewrites = [
  ['com.feng.dsagent.common.ApiException', 'com.feng.dsagent.common.exception.ApiException'],
  ['com.feng.dsagent.admin.AdminAuditEvent', 'com.feng.dsagent.admin.entity.AdminAuditEvent'],
  ['com.feng.dsagent.admin.AdminAuditQuery', 'com.feng.dsagent.admin.entity.AdminAuditQuery'],
  ['com.feng.dsagent.admin.AdminAuditWrite', 'com.feng.dsagent.admin.dto.AdminAuditWrite'],
  ['com.feng.dsagent.admin.AdminUserQuery', 'com.feng.dsagent.admin.entity.AdminUserQuery'],
  ['com.feng.dsagent.admin.AdminPage', 'com.feng.dsagent.admin.vo.AdminPage'],
  ['com.feng.dsagent.admin.AdminUser;', 'com.feng.dsagent.admin.entity.AdminUserEntity;'],
  ['com.feng.dsagent.admin.BackgroundTaskView', 'com.feng.dsagent.admin.vo.BackgroundTaskView'],
  ['com.feng.dsagent.admin.BackgroundTaskPage', 'com.feng.dsagent.admin.vo.BackgroundTaskPage'],
  ['com.feng.dsagent.admin.BackgroundTaskQuery', 'com.feng.dsagent.admin.entity.BackgroundTaskQuery'],
  ['com.feng.dsagent.review.ReviewEntity', 'com.feng.dsagent.review.entity.ReviewEntity'],
  ['com.feng.dsagent.review.ReviewSourceEntity', 'com.feng.dsagent.review.entity.ReviewSourceEntity'],
  ['com.feng.dsagent.review.ReviewQuery', 'com.feng.dsagent.review.entity.ReviewQuery'],
];

function ensureImport(text, fqcn) {
  if (text.includes(`import ${fqcn};`)) return text;
  const pkgMatch = text.match(/^package\s+[\w.]+\s*;\s*\n/);
  let insertAt = pkgMatch[0].length;
  while (text[insertAt] === '\n') insertAt++;
  const importSection = text.slice(insertAt).match(/^(?:import\s+.+;\s*\n)+/);
  if (importSection) insertAt += importSection[0].length;
  return text.slice(0, insertAt) + `import ${fqcn};\n` + text.slice(insertAt);
}

for (const rel of files) {
  const file = path.join(root, rel);
  let text = fs.readFileSync(file, 'utf8');

  for (const [from, to] of importRewrites) {
    text = text.split(from).join(to);
  }
  // Type rename AdminUser -> AdminUserEntity (word boundary)
  text = text.replace(/\bAdminUser\b/g, (m, offset) => {
    const after = text.slice(offset + m.length, offset + m.length + 16);
    if (/^(Entity|View|Query|Learning)/.test(after)) return m;
    return 'AdminUserEntity';
  });

  // Move Views that were same-package
  if (rel.includes('AdminService')) {
    text = ensureImport(text, 'com.feng.dsagent.admin.vo.AdminUserView');
    text = ensureImport(text, 'com.feng.dsagent.admin.vo.AdminCapabilityView');
    text = ensureImport(text, 'com.feng.dsagent.admin.constant.AdminErrorCode');
  }
  if (rel.includes('BackgroundTask')) {
    text = ensureImport(text, 'com.feng.dsagent.admin.constant.AdminErrorCode');
  }
  if (rel.includes('AdminClass') || rel.includes('AdminKnowledge') || rel.includes('AdminUserLearning')) {
    text = ensureImport(text, 'com.feng.dsagent.admin.constant.AdminErrorCode');
  }
  if (rel.includes('AdminKnowledge')) {
    text = ensureImport(text, 'com.feng.dsagent.admin.vo.KnowledgeSettingsView');
    // Remove nested KnowledgeSettingsView if present
    text = text.replace(
      /\s*public record KnowledgeSettingsView\([\s\S]*?\) \{\}\s*/,
      '\n',
    );
  }
  if (rel.includes('AdminUserLearning')) {
    text = ensureImport(text, 'com.feng.dsagent.admin.vo.AdminUserLearningView');
    text = text.replace(
      /\s*public record AdminUserLearningView\([\s\S]*?\) \{\}\s*/,
      '\n',
    );
  }
  if (rel.includes('ReviewService')) {
    text = ensureImport(text, 'com.feng.dsagent.review.constant.ReviewErrorCode');
    text = ensureImport(text, 'com.feng.dsagent.review.vo.ReviewItemView');
    text = ensureImport(text, 'com.feng.dsagent.review.vo.ReviewDetailView');
    text = ensureImport(text, 'com.feng.dsagent.review.vo.ReviewHistoryView');
    text = ensureImport(text, 'com.feng.dsagent.review.vo.ReviewSourceView');
    text = ensureImport(text, 'com.feng.dsagent.review.vo.ReviewPage');
    text = ensureImport(text, 'com.feng.dsagent.review.ReviewType');
    text = ensureImport(text, 'com.feng.dsagent.review.support.SourceAssessment');
  }

  // Wire error codes — specific replacements (preserve Chinese messages via toException)
  const pairs = [
    // AdminService / shared
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_DISABLE_REASON_REQUIRED",\s*"禁用用户时必须填写原因"\s*\)/g,
      'AdminErrorCode.DISABLE_REASON_REQUIRED.toException()'],
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_AUDIT_TIME_RANGE_INVALID",\s*"审计时间范围无效"\s*\)/g,
      'AdminErrorCode.AUDIT_TIME_RANGE_INVALID.toException()'],
    [/new ApiException\(\s*HttpStatus\.NOT_FOUND,\s*"ADMIN_USER_NOT_FOUND",\s*"用户不存在"\s*\)/g,
      'AdminErrorCode.USER_NOT_FOUND.toException()'],
    [/new ApiException\(\s*HttpStatus\.CONFLICT,\s*"ADMIN_SELF_MUTATION_FORBIDDEN",\s*"管理员不能修改自己的角色或启用状态"\s*\)/g,
      'AdminErrorCode.SELF_MUTATION_FORBIDDEN.toException()'],
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_USER_STATUS_INVALID",\s*"用户状态必须是 ACTIVE 或 DISABLED"\s*\)/g,
      'AdminErrorCode.USER_STATUS_INVALID.toException()'],
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_ROLE_INVALID",\s*"角色无效"\s*\)/g,
      'AdminErrorCode.ROLE_INVALID.toException()'],
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_ROLE_SET_INVALID",\s*"角色集合不能为空"\s*\)/g,
      'AdminErrorCode.ROLE_SET_INVALID.toException("角色集合不能为空")'],
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_ROLE_SET_INVALID",\s*"角色集合包含空值"\s*\)/g,
      'AdminErrorCode.ROLE_SET_INVALID.toException("角色集合包含空值")'],
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_ROLE_SET_INVALID",\s*"所有账号必须保留 STUDENT 基础角色"\s*\)/g,
      'AdminErrorCode.ROLE_SET_INVALID.toException("所有账号必须保留 STUDENT 基础角色")'],
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_AUDIT_TIME_INVALID",\s*parameter \+ " 必须是 ISO-8601 UTC 时间"\s*\)/g,
      'AdminErrorCode.AUDIT_TIME_INVALID.toException(parameter + " 必须是 ISO-8601 UTC 时间")'],
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_PAGE_INVALID",\s*"分页参数无效"\s*\)/g,
      'AdminErrorCode.PAGE_INVALID.toException()'],
    [/new ApiException\(\s*HttpStatus\.NOT_FOUND,\s*"USER_NOT_FOUND",\s*"没有这个用户。"\s*\)/g,
      'AdminErrorCode.USER_LOOKUP_NOT_FOUND.toException()'],
    [/new ApiException\(\s*HttpStatus\.NOT_FOUND,\s*"USER_NOT_FOUND",\s*"找不到这个邮箱或用户名对应的账号。"\s*\)/g,
      'AdminErrorCode.CLASS_USER_NOT_FOUND.toException()'],
    [/new ApiException\(\s*HttpStatus\.CONFLICT,\s*"ALREADY_ENROLLED",\s*"这个账号已经在这个班里了。"\s*\)/g,
      'AdminErrorCode.ALREADY_ENROLLED.toException()'],
    [/new ApiException\(\s*HttpStatus\.NOT_FOUND,\s*"CLASS_NOT_FOUND",\s*"这个班级不存在。"\s*\)/g,
      'AdminErrorCode.CLASS_NOT_FOUND.toException()'],
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ROLE_IN_CLASS_INVALID",\s*"班内角色只能是学生或教师。"\s*\)/g,
      'AdminErrorCode.ROLE_IN_CLASS_INVALID.toException()'],
    [/new ApiException\(\s*HttpStatus\.NOT_FOUND,\s*"ADMIN_BACKGROUND_TASK_NOT_FOUND",\s*"后台任务不存在"\s*\)/g,
      'AdminErrorCode.BACKGROUND_TASK_NOT_FOUND.toException()'],
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_BACKGROUND_TASK_STATUS_INVALID",\s*"后台任务状态无效"\s*\)/g,
      'AdminErrorCode.BACKGROUND_TASK_STATUS_INVALID.toException()'],
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_BACKGROUND_TASK_TYPE_INVALID",\s*"后台任务类型无效"\s*\)/g,
      'AdminErrorCode.BACKGROUND_TASK_TYPE_INVALID.toException()'],
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_KNOWLEDGE_QUERY_REQUIRED",\s*"请输入要测试的检索词"\s*\)/g,
      'AdminErrorCode.KNOWLEDGE_QUERY_REQUIRED.toException()'],
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_KNOWLEDGE_PAGE_LABEL_INVALID",\s*"页签最长 120 个字符"\s*\)/g,
      'AdminErrorCode.KNOWLEDGE_PAGE_LABEL_INVALID.toException()'],
    [/new ApiException\(\s*HttpStatus\.NOT_FOUND,\s*"ADMIN_KNOWLEDGE_CHUNK_NOT_FOUND",\s*"知识条目不存在"\s*\)/g,
      'AdminErrorCode.KNOWLEDGE_CHUNK_NOT_FOUND.toException()'],
    [/new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_KNOWLEDGE_CHAPTER_NOT_FOUND",\s*"章节不存在"\s*\)/g,
      'AdminErrorCode.KNOWLEDGE_CHAPTER_NOT_FOUND.toException()'],
  ];

  for (const [re, rep] of pairs) {
    text = text.replace(re, rep);
  }

  // Multiline LAST_ADMIN / background retry/cancel / knowledge settings
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.CONFLICT,\s*"ADMIN_LAST_ADMIN_PROTECTED",\s*[^)]+\)/gs,
    'AdminErrorCode.LAST_ADMIN_PROTECTED.toException()',
  );
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.CONFLICT,\s*"ADMIN_BACKGROUND_TASK_RETRY_NOT_ALLOWED",\s*[^)]+\)/gs,
    'AdminErrorCode.BACKGROUND_TASK_RETRY_NOT_ALLOWED.toException()',
  );
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.CONFLICT,\s*"ADMIN_BACKGROUND_TASK_CANCEL_NOT_ALLOWED",\s*[^)]+\)/gs,
    'AdminErrorCode.BACKGROUND_TASK_CANCEL_NOT_ALLOWED.toException()',
  );
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_KNOWLEDGE_SETTINGS_INCOMPLETE",\s*[^)]+\)/gs,
    'AdminErrorCode.KNOWLEDGE_SETTINGS_INCOMPLETE.toException()',
  );
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_KNOWLEDGE_SETTINGS_INVALID",\s*[^)]+\)/gs,
    'AdminErrorCode.KNOWLEDGE_SETTINGS_INVALID.toException()',
  );
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_KNOWLEDGE_AUDIENCE_INVALID",\s*[^)]+\)/gs,
    'AdminErrorCode.KNOWLEDGE_AUDIENCE_INVALID.toException()',
  );
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_KNOWLEDGE_REVIEW_STATUS_INVALID",\s*[^)]+\)/gs,
    'AdminErrorCode.KNOWLEDGE_REVIEW_STATUS_INVALID.toException()',
  );
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_KNOWLEDGE_LICENSE_SCOPE_INVALID",\s*[^)]+\)/gs,
    'AdminErrorCode.KNOWLEDGE_LICENSE_SCOPE_INVALID.toException()',
  );
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_KNOWLEDGE_TEXT_REQUIRED",\s*label \+ "不能为空"\s*\)/g,
    'AdminErrorCode.KNOWLEDGE_TEXT_REQUIRED.toException(label + "不能为空")',
  );
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_KNOWLEDGE_TEXT_TOO_LONG",\s*[^)]+\)/gs,
    (m) => {
      const msg = m.match(/,\s*([^,]+)\s*\)$/);
      // Keep dynamic message if present
      const inner = m.replace(/^[\s\S]*ADMIN_KNOWLEDGE_TEXT_TOO_LONG",\s*/, '').replace(/\s*\)$/, '');
      return `AdminErrorCode.KNOWLEDGE_TEXT_TOO_LONG.toException(${inner})`;
    },
  );

  // Class create validation helpers — switch to enum codes
  text = text.replace(
    /requireText\(name, MAX_NAME, "CLASS_NAME_REQUIRED", "班级名称不能为空。"\)/g,
    'requireText(name, MAX_NAME, AdminErrorCode.CLASS_NAME_REQUIRED)',
  );
  text = text.replace(
    /optionalText\(term, MAX_TERM, "CLASS_TERM_TOO_LONG", "学期名称太长。"\)/g,
    'optionalText(term, MAX_TERM, AdminErrorCode.CLASS_TERM_TOO_LONG)',
  );
  text = text.replace(
    /requireText\(identifier, 255, "IDENTIFIER_REQUIRED", "请填写邮箱或用户名。"\)/g,
    'requireText(identifier, 255, AdminErrorCode.IDENTIFIER_REQUIRED)',
  );

  // Update requireText/optionalText signatures if present
  if (text.includes('AdminErrorCode.CLASS_NAME_REQUIRED)')) {
    text = text.replace(
      /private static String requireText\(String value, int max, String code, String message\) \{\s*String clean = value == null \? "" : value\.trim\(\);\s*if \(clean\.isEmpty\(\) \|\| clean\.length\(\) > max\) \{\s*throw new ApiException\(HttpStatus\.BAD_REQUEST, code, message\);\s*\}\s*return clean;\s*\}/s,
      `private static String requireText(String value, int max, AdminErrorCode error) {
        String clean = value == null ? "" : value.trim();
        if (clean.isEmpty() || clean.length() > max) {
            throw error.toException();
        }
        return clean;
    }`,
    );
    text = text.replace(
      /private static String optionalText\(String value, int max, String code, String message\) \{\s*String clean = value == null \? "" : value\.trim\(\);\s*if \(clean\.length\(\) > max\) \{\s*throw new ApiException\(HttpStatus\.BAD_REQUEST, code, message\);\s*\}\s*return clean\.isEmpty\(\) \? null : clean;\s*\}/s,
      `private static String optionalText(String value, int max, AdminErrorCode error) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() > max) {
            throw error.toException();
        }
        return clean.isEmpty() ? null : clean;
    }`,
    );
  }

  // ReviewService
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.NOT_FOUND,\s*"ADMIN_REVIEW_NOT_FOUND",\s*type\.name\(\) \+ " review item was not found: " \+ id\s*\)/g,
    'ReviewErrorCode.NOT_FOUND.toException(type.name() + " review item was not found: " + id)',
  );
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_REVIEW_TYPE_INVALID",\s*"Unknown review type: " \+ value\s*\)/g,
    'ReviewErrorCode.TYPE_INVALID.toException("Unknown review type: " + value)',
  );
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_REVIEW_STATUS_INVALID",\s*"Unknown review status"\s*\)/g,
    'ReviewErrorCode.STATUS_INVALID.toException()',
  );
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_REVIEW_ID_INVALID",\s*"Review item identifier is invalid"\s*\)/g,
    'ReviewErrorCode.ID_INVALID.toException()',
  );
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.BAD_REQUEST,\s*"ADMIN_PAGE_INVALID",\s*"Pagination parameters are invalid"\s*\)/g,
    'ReviewErrorCode.PAGE_INVALID.toException()',
  );
  text = text.replace(
    /new ApiException\(\s*HttpStatus\.\w+,\s*"ADMIN_REVIEW_SOURCE_INCOMPLETE",\s*[^)]+\)/gs,
    'ReviewErrorCode.SOURCE_INCOMPLETE.toException("Verified review status requires a verified, complete source chain in a published chapter")',
  );

  fs.writeFileSync(file, text, 'utf8');
  console.log('rewired', rel);
}
