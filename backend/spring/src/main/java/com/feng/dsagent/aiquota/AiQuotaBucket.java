package com.feng.dsagent.aiquota;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

public record AiQuotaBucket(long userId, LocalDate quotaDate, long dailyTokenQuota, long reservedTokens, long consumedTokens) {
}
