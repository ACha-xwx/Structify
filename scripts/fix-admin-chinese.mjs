import fs from 'node:fs';

const file = 'backend/spring/src/main/java/com/feng/dsagent/admin/service/AdminService.java';
let t = fs.readFileSync(file, 'utf8');

t = t.replace(
  /private Set<String> normalizeRoles\(Set<String> values\) \{[\s\S]*?return Set\.copyOf\(roles\);\s*\}/,
  `private Set<String> normalizeRoles(Set<String> values) {
        if (values == null || values.isEmpty()) {
            throw AdminErrorCode.ROLE_SET_INVALID.toException("角色集合不能为空");
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String value : values) {
            String role = normalizeRole(value);
            if (role == null) {
                throw AdminErrorCode.ROLE_SET_INVALID.toException("角色集合包含空值");
            }
            roles.add(role);
        }
        if (!roles.contains("STUDENT")) {
            throw AdminErrorCode.ROLE_SET_INVALID.toException("所有账号必须保留 STUDENT 基础角色");
        }
        return Set.copyOf(roles);
    }`,
);

t = t.replace(
  /throw AdminErrorCode\.AUDIT_TIME_INVALID\.toException\(parameter \+ "[^"]*"\);/,
  'throw AdminErrorCode.AUDIT_TIME_INVALID.toException(parameter + " 必须是 ISO-8601 UTC 时间");',
);

fs.writeFileSync(file, t, 'utf8');
const check = fs.readFileSync(file, 'utf8');
console.log('roles ok', check.includes('角色集合不能为空'));
console.log('audit ok', check.includes('必须是 ISO-8601 UTC 时间'));
