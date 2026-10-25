package com.feng.dsagent.review.service;

import com.feng.dsagent.review.ReviewRepository;
import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.knowledge.KnowledgeEligibilityChanged;
import com.feng.dsagent.security.AuthenticatedUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

public record ReviewSourceView(String type, String id, String title, String status) {
}
