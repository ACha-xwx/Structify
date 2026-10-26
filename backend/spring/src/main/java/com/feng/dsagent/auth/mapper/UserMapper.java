package com.feng.dsagent.auth.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 职责：用户账号与角色绑定的持久化访问。
 */
@Mapper
public interface UserMapper {

    public UserRow selectActiveByEmail(@Param("email") String email);

    public UserRow selectActiveByUsername(@Param("usernameNormalized") String usernameNormalized);

    public UserRow selectActiveById(@Param("id") long id);

    public UserRow selectAnyByEmail(@Param("email") String email);

    public UserRow selectAnyByUsername(@Param("usernameNormalized") String usernameNormalized);

    public List<String> selectRoles(@Param("userId") long userId);

    public int insertUser(UserInsert row);

    public int insertRole(@Param("userId") long userId, @Param("role") String role);

    public int updatePassword(@Param("userId") long userId, @Param("passwordHash") String passwordHash);

    public int reconcileUser(
        @Param("userId") long userId,
        @Param("email") String email,
        @Param("username") String username,
        @Param("usernameNormalized") String usernameNormalized,
        @Param("passwordHash") String passwordHash
    );

    public int deleteRoles(@Param("userId") long userId);

    public record UserRow(long id, String email, String username, String passwordHash) {
    }

    public static final class UserInsert {
        private long id;
        private String email;
        private String username;
        private String usernameNormalized;
        private String passwordHash;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getUsernameNormalized() {
            return usernameNormalized;
        }

        public void setUsernameNormalized(String usernameNormalized) {
            this.usernameNormalized = usernameNormalized;
        }

        public String getPasswordHash() {
            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }
    }
}
