package com.feng.dsagent.review.entity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import org.springframework.transaction.annotation.Propagation;

public record ReviewSourceEntity(String type, String id, String title, String status) {
}
