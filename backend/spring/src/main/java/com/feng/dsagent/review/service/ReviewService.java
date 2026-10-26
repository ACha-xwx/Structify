package com.feng.dsagent.review.service;

import com.feng.dsagent.admin.service.AdminAuditService;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.common.support.PageParams;
import com.feng.dsagent.common.support.StringNormalizer;
import com.feng.dsagent.common.vo.PageVO;
import com.feng.dsagent.knowledge.domain.KnowledgeEligibilityChanged;
import com.feng.dsagent.review.converter.ReviewHistoryConverter;
import com.feng.dsagent.review.converter.ReviewItemConverter;
import com.feng.dsagent.review.converter.ReviewSourceConverter;
import com.feng.dsagent.review.domain.ReviewType;
import com.feng.dsagent.review.entity.ReviewEntity;
import com.feng.dsagent.review.entity.ReviewSourceEntity;
import com.feng.dsagent.review.error.ReviewErrorCode;
import com.feng.dsagent.review.mapper.ReviewMapper;
import com.feng.dsagent.review.query.ReviewQuery;
import com.feng.dsagent.review.support.SourceAssessment;
import com.feng.dsagent.review.vo.ReviewDetailVO;
import com.feng.dsagent.review.vo.ReviewHistoryVO;
import com.feng.dsagent.review.vo.ReviewItemVO;
import com.feng.dsagent.review.vo.ReviewSourceVO;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 职责：编排审核项查询、状态变更与知识库资格事件发布。
 */
@Service
public class ReviewService {
    private static final Set<String> REVIEW_STATUSES = Set.of(
        "LEGACY_UNVERIFIED", "DRAFT", "PUBLISHED", "VERIFIED", "EXCLUDED"
    );
    private static final Set<String> VERIFIED_SOURCE_STATUSES = Set.of("VERIFIED");

    private final ReviewMapper mapper;
    private final ReviewHistoryConverter reviewHistoryConverter;
    private final ReviewItemConverter reviewItemConverter;
    private final ReviewSourceConverter reviewSourceConverter;
    private final AdminAuditService adminAudit;
    private final ApplicationEventPublisher events;

    public ReviewService(
        ReviewMapper mapper,
        ReviewHistoryConverter reviewHistoryConverter,
        ReviewItemConverter reviewItemConverter,
        ReviewSourceConverter reviewSourceConverter,
        AdminAuditService adminAudit,
        ApplicationEventPublisher events
    ) {
        this.mapper = mapper;
        this.reviewHistoryConverter = reviewHistoryConverter;
        this.reviewItemConverter = reviewItemConverter;
        this.reviewSourceConverter = reviewSourceConverter;
        this.adminAudit = adminAudit;
        this.events = events;
    }

    public PageVO<ReviewItemVO> list(
        int page,
        int size,
        String requestedType,
        String requestedStatus,
        String search
    ) {
        PageParams.requireValid(page, size, ReviewErrorCode.PAGE_INVALID);
        List<ReviewType> types = parseTypes(requestedType);
        String status = parseOptionalStatus(requestedStatus);
        ReviewQuery query = new ReviewQuery(
            page,
            size,
            types,
            status,
            StringNormalizer.optional(search)
        );
        long total = mapper.countReviews(query);
        return PageVO.of(
            page,
            size,
            total,
            mapper.selectReviews(query).stream().map(this::view).toList()
        );
    }

    public ReviewDetailVO detail(String requestedType, String id) {
        ReviewEntity item = require(parseType(requestedType), normalizeId(id));
        SourceAssessment assessment = sources(item);
        return new ReviewDetailVO(view(item, assessment.complete()), assessment.chain());
    }

    @Transactional
    public ReviewItemVO updateStatus(
        AuthenticatedUser actor,
        String requestedType,
        String id,
        String requestedStatus,
        String requestedNote,
        String requestId
    ) {
        ReviewType type = parseType(requestedType);
        String contentId = normalizeId(id);
        String nextStatus = parseRequiredStatus(requestedStatus);
        String note = StringNormalizer.optional(requestedNote);
        ReviewEntity before = mapper.lockOptional(type, contentId).orElseThrow(() -> notFound(type, contentId));
        SourceAssessment assessment = sources(before);
        if ("VERIFIED".equals(nextStatus) && !assessment.complete()) {
            adminAudit.recordCommitted(
                actor.userId(),
                "REVIEW_STATUS_CHANGED",
                type.name(),
                contentId,
                "REJECTED",
                safeRequestId(requestId),
                summary(before),
                "verification_blocked=source_chain_incomplete"
            );
            throw ReviewErrorCode.SOURCE_INCOMPLETE.toException();
        }
        if (before.status().equals(nextStatus)) {
            mapper.appendAdminAudit(
                actor.userId(), type, contentId, "NO_CHANGE", safeRequestId(requestId), summary(before), summary(before)
            );
            return view(before, assessment.complete());
        }
        mapper.updateStatus(type, contentId, nextStatus);
        ReviewEntity after = require(type, contentId);
        mapper.appendReviewEvent(
            type,
            contentId,
            before.status(),
            after.status(),
            note == null ? "" : note,
            actor.userId(),
            safeRequestId(requestId)
        );
        mapper.appendAdminAudit(
            actor.userId(), type, contentId, "SUCCESS", safeRequestId(requestId), summary(before), summary(after)
        );
        events.publishEvent(new KnowledgeEligibilityChanged());
        return view(after);
    }

    public List<ReviewHistoryVO> history(String requestedType, String id) {
        ReviewType type = parseType(requestedType);
        String contentId = normalizeId(id);
        require(type, contentId);
        return mapper.history(type, contentId).stream().map(reviewHistoryConverter::toView).toList();
    }

    private ReviewItemVO view(ReviewEntity entity) {
        return view(entity, sources(entity).complete());
    }

    private ReviewItemVO view(ReviewEntity entity, boolean sourceComplete) {
        return reviewItemConverter.toView(entity, sourceComplete);
    }

    private SourceAssessment sources(ReviewEntity item) {
        List<ReviewSourceVO> chain = new ArrayList<>();
        Optional<ReviewSourceEntity> chapter = mapper.chapterOptional(item.chapterId());
        chapter.map(this::source).ifPresent(chain::add);
        boolean chapterPublished = chapter.map(value -> "PUBLISHED".equals(value.status())).orElse(false);

        return switch (item.type()) {
            case RESOURCE -> new SourceAssessment(
                chainWithDeclaredSource(chain, item),
                chapterPublished && completeMetadata(item.sourceName(), item.versionLabel())
            );
            case KNOWLEDGE_CHUNK -> sourceForKnowledge(item, chain, chapterPublished);
            case PRESENTATION_MANIFEST -> sourceForManifest(item, chain, chapterPublished);
            case PRESENTATION_PAGE -> sourceForPage(item, chain, chapterPublished);
            case DSVP_REQUEST_SNAPSHOT -> sourceForEvidence(item, chain, chapterPublished);
        };
    }

    private SourceAssessment sourceForKnowledge(
        ReviewEntity item,
        List<ReviewSourceVO> chain,
        boolean chapterPublished
    ) {
        Optional<ReviewEntity> resource = item.parentId() == null || item.parentId().isBlank()
            ? Optional.empty()
            : mapper.findOptional(ReviewType.RESOURCE, item.parentId());
        resource.map(this::source).ifPresent(chain::add);
        boolean complete = chapterPublished
            && resource.filter(value -> VERIFIED_SOURCE_STATUSES.contains(value.status()))
                .map(this::sources)
                .map(SourceAssessment::complete)
                .orElse(false);
        return new SourceAssessment(chain, complete);
    }

    private SourceAssessment sourceForManifest(
        ReviewEntity item,
        List<ReviewSourceVO> chain,
        boolean chapterPublished
    ) {
        Optional<ReviewEntity> resource = item.parentId() == null || item.parentId().isBlank()
            ? Optional.empty()
            : mapper.findOptional(ReviewType.RESOURCE, item.parentId());
        resource.map(this::source).ifPresent(chain::add);
        List<ReviewSourceVO> withMetadata = chainWithDeclaredSource(chain, item);
        boolean resourceComplete = resource.isEmpty() || resource
            .filter(value -> VERIFIED_SOURCE_STATUSES.contains(value.status()))
            .map(this::sources)
            .map(SourceAssessment::complete)
            .orElse(false);
        return new SourceAssessment(
            withMetadata,
            chapterPublished && resourceComplete && completeMetadata(item.sourceName(), item.versionLabel())
                && nonBlank(item.sourcePath()) && nonBlank(item.contentHash())
        );
    }

    private SourceAssessment sourceForPage(
        ReviewEntity item,
        List<ReviewSourceVO> chain,
        boolean chapterPublished
    ) {
        Optional<ReviewEntity> manifest = item.parentId() == null || item.parentId().isBlank()
            ? Optional.empty()
            : mapper.findOptional(ReviewType.PRESENTATION_MANIFEST, item.parentId());
        manifest.map(this::source).ifPresent(chain::add);
        boolean complete = chapterPublished
            && nonBlank(item.contentHash())
            && manifest.filter(value -> VERIFIED_SOURCE_STATUSES.contains(value.status()))
                .map(this::sources)
                .map(SourceAssessment::complete)
                .orElse(false);
        return new SourceAssessment(chain, complete);
    }

    private SourceAssessment sourceForEvidence(
        ReviewEntity item,
        List<ReviewSourceVO> chain,
        boolean chapterPublished
    ) {
        Optional<ReviewEntity> page = item.sourceRef() == null || item.sourceRef().isBlank()
            ? Optional.empty()
            : mapper.findPresentationPageBySourceRefOptional(item.sourceRef());
        page.map(this::source).ifPresent(chain::add);
        boolean complete = chapterPublished
            && page.filter(value -> VERIFIED_SOURCE_STATUSES.contains(value.status()))
                .map(this::sources)
                .map(SourceAssessment::complete)
                .orElse(false);
        return new SourceAssessment(chain, complete);
    }

    private List<ReviewSourceVO> chainWithDeclaredSource(List<ReviewSourceVO> chain, ReviewEntity item) {
        if (!nonBlank(item.sourceName())) {
            return chain;
        }
        List<ReviewSourceVO> result = new ArrayList<>(chain);
        result.add(new ReviewSourceVO(
            "DECLARED_SOURCE",
            item.id(),
            item.sourceName(),
            completeMetadata(item.sourceName(), item.versionLabel()) ? "DECLARED" : "INCOMPLETE"
        ));
        return result;
    }

    private ReviewSourceVO source(ReviewSourceEntity entity) {
        return reviewSourceConverter.toView(entity);
    }

    private ReviewSourceVO source(ReviewEntity entity) {
        return reviewSourceConverter.toView(entity);
    }

    private ReviewEntity require(ReviewType type, String id) {
        return mapper.findOptional(type, id).orElseThrow(() -> notFound(type, id));
    }

    private BizException notFound(ReviewType type, String id) {
        return ReviewErrorCode.NOT_FOUND.toException(type.name() + " review item was not found: " + id);
    }

    private List<ReviewType> parseTypes(String value) {
        String normalized = StringNormalizer.optional(value);
        if (normalized == null) {
            return List.of(ReviewType.values());
        }
        List<ReviewType> types = new ArrayList<>();
        for (String candidate : normalized.split(",")) {
            ReviewType type = parseType(candidate);
            if (!types.contains(type)) {
                types.add(type);
            }
        }
        if (types.isEmpty()) {
            throw invalidType(value);
        }
        return List.copyOf(types);
    }

    private ReviewType parseType(String value) {
        String normalized = StringNormalizer.optional(value);
        if (normalized == null) {
            throw invalidType(value);
        }
        try {
            return ReviewType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw invalidType(value);
        }
    }

    private BizException invalidType(String value) {
        return ReviewErrorCode.TYPE_INVALID.toException("Unknown review type: " + value);
    }

    private String parseOptionalStatus(String value) {
        String normalized = StringNormalizer.optional(value);
        return normalized == null ? null : parseRequiredStatus(normalized);
    }

    private String parseRequiredStatus(String value) {
        String normalized = StringNormalizer.optional(value);
        if (normalized == null || !REVIEW_STATUSES.contains(normalized.toUpperCase(Locale.ROOT))) {
            throw ReviewErrorCode.STATUS_INVALID.toException();
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeId(String value) {
        String normalized = StringNormalizer.optional(value);
        if (normalized == null || normalized.length() > 160) {
            throw ReviewErrorCode.ID_INVALID.toException();
        }
        return normalized;
    }

    private static boolean completeMetadata(String sourceName, String versionLabel) {
        return nonBlank(sourceName) && nonBlank(versionLabel);
    }

    private static boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String safeRequestId(String value) {
        String normalized = StringNormalizer.optional(value);
        return normalized == null ? "" : normalized.substring(0, Math.min(normalized.length(), 128));
    }

    private static String summary(ReviewEntity value) {
        return "status=" + value.status() + ";type=" + value.type().name();
    }
}
