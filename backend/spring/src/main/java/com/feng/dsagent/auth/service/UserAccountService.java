package com.feng.dsagent.auth.service;

import com.feng.dsagent.auth.entity.UserAccountEntity;
import com.feng.dsagent.auth.mapper.UserMapper;
import com.feng.dsagent.auth.support.UsernamePolicy;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 职责：用户账号与角色的读写编排（不含认证会话）。
 */
@Service
public class UserAccountService {

    private final UserMapper users;

    public UserAccountService(UserMapper users) {
        this.users = users;
    }

    public Optional<UserAccountEntity> findByEmail(String email) {
        return Optional.ofNullable(account(users.selectActiveByEmail(email)));
    }

    public Optional<UserAccountEntity> findByUsername(String normalizedUsername) {
        return Optional.ofNullable(account(users.selectActiveByUsername(normalizedUsername)));
    }

    public Optional<UserAccountEntity> findById(long id) {
        return Optional.ofNullable(account(users.selectActiveById(id)));
    }

    public Optional<UserAccountEntity> findAnyByEmail(String email) {
        return Optional.ofNullable(account(users.selectAnyByEmail(email)));
    }

    public Optional<UserAccountEntity> findAnyByUsername(String normalizedUsername) {
        return Optional.ofNullable(account(users.selectAnyByUsername(normalizedUsername)));
    }

    @Transactional
    public UserAccountEntity create(String email, String username, String passwordHash, Set<String> roles) {
        UserMapper.UserInsert insert = new UserMapper.UserInsert();
        insert.setEmail(email);
        insert.setUsername(username);
        insert.setUsernameNormalized(username == null ? null : UsernamePolicy.lookupKey(username));
        insert.setPasswordHash(passwordHash);
        users.insertUser(insert);
        long userId = insert.getId();
        if (userId <= 0) {
            throw new IllegalStateException("Database did not return a user id");
        }
        for (String role : roles) {
            users.insertRole(userId, role);
        }
        return new UserAccountEntity(userId, email, username, passwordHash, roles);
    }

    public UserAccountEntity create(String email, String passwordHash, Set<String> roles) {
        return create(email, null, passwordHash, roles);
    }

    public void updatePassword(long userId, String passwordHash) {
        int updated = users.updatePassword(userId, passwordHash);
        if (updated != 1) {
            throw new IllegalStateException("Active user disappeared during password reset");
        }
    }

    public void reconcileAdministrator(
        long userId,
        String email,
        String username,
        String passwordHash,
        Set<String> roles
    ) {
        int updated = users.reconcileUser(
            userId,
            email,
            username,
            UsernamePolicy.lookupKey(username),
            passwordHash
        );
        if (updated != 1) {
            throw new IllegalStateException("Administrator account disappeared during reconciliation");
        }
        users.deleteRoles(userId);
        for (String role : roles.stream().sorted().toList()) {
            users.insertRole(userId, role);
        }
    }

    private UserAccountEntity account(UserMapper.UserRow row) {
        if (row == null) {
            return null;
        }
        return new UserAccountEntity(
            row.id(),
            row.email(),
            row.username(),
            row.passwordHash(),
            new LinkedHashSet<>(users.selectRoles(row.id()))
        );
    }
}
