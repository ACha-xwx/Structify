package com.feng.dsagent.animation.support;

import com.feng.dsagent.animation.domain.DsvpEvidenceContext;
import com.feng.dsagent.animation.domain.DsvpEvidenceResolution;
import com.feng.dsagent.animation.domain.DsvpEvidenceSource;
import com.feng.dsagent.animation.mapper.DsvpEvidenceMapper;
import com.feng.dsagent.animation.error.AnimationErrorCode;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Resolves DSVP context from rows the current user is allowed to reference. */
@Component
public final class DsvpEvidenceResolver {

    private final DsvpEvidenceMapper evidence;

    public DsvpEvidenceResolver(DsvpEvidenceMapper evidence) {
        this.evidence = evidence;
    }

    public DsvpEvidenceResolution resolve(
        long userId,
        Set<String> roles,
        JsonNode request,
        DsvpEvidenceSource source
    ) {
        DsvpEvidenceContext context = DsvpEvidenceContext.from(request);
        validateSourceType(context, source);

        Candidate classroom = context.classroomSessionId() == null
            ? null
            : classroom(userId, context.classroomSessionId());
        Candidate presentation = context.presentationPageId() == null
            ? null
            : presentation(userId, roles, context.presentationPageId(), context.presentationId());
        String explicit = publishedChapter(context.chapterId());

        if (classroom == null && context.classroomSessionId() != null) forbidden();
        if (presentation == null && context.presentationPageId() != null) forbidden();

        if (classroom != null && presentation != null && !classroom.chapterId().equals(presentation.chapterId())) {
            conflict();
        }
        Candidate authoritative = classroom != null ? classroom : presentation;
        if (authoritative != null && explicit != null && !authoritative.chapterId().equals(explicit)) {
            conflict();
        }
        if (authoritative == null && explicit != null) {
            authoritative = new Candidate(explicit, null, "EXPLICIT_CHAPTER");
        }
        if (authoritative == null && context.sourceRef() != null) {
            authoritative = reviewedAnimation(roles, context.sourceRef());
        }
        if (authoritative == null) {
            return DsvpEvidenceResolution.preview();
        }

        String sourceRef = authoritative.sourceRef();
        if (sourceRef == null || sourceRef.isBlank()) {
            sourceRef = context.sourceRef();
        }
        if (sourceRef == null || sourceRef.isBlank()) {
            sourceRef = source.reference();
        }
        return new DsvpEvidenceResolution(authoritative.chapterId(), sourceRef, authoritative.matchSource());
    }

    private Candidate classroom(long userId, String sessionId) {
        String chapterId = evidence.selectClassroomChapter(sessionId, userId);
        if (chapterId == null || chapterId.isBlank()) {
            return null;
        }
        return new Candidate(chapterId, "classroom_session:" + sessionId, "CLASSROOM_SESSION");
    }

    private Candidate presentation(long userId, Set<String> roles, String pageId, String presentationId) {
        DsvpEvidenceMapper.PresentationEvidenceRow row = evidence.selectPresentationPage(pageId);
        if (row == null) {
            return null;
        }
        if (presentationId != null && !presentationId.equals(row.presentationId())) {
            conflict();
        }
        String license = row.licenseScope();
        if ("TEAM_ONLY".equalsIgnoreCase(license)
            && !(roles.contains("TEACHER") || roles.contains("ADMIN"))) {
            forbidden();
        }
        if ("CLASSROOM_ONLY".equalsIgnoreCase(license) && userId <= 0) forbidden();
        return new Candidate(row.chapterId(), row.sourceRef(), "PRESENTATION_PAGE");
    }

    private String publishedChapter(String chapterId) {
        if (chapterId == null || chapterId.isBlank()) return null;
        if (evidence.countPublishedChapter(chapterId) == 0) {
            throw AnimationErrorCode.DSVP_CHAPTER_INVALID.toException();
        }
        return chapterId;
    }

    private Candidate reviewedAnimation(Set<String> roles, String sourceRef) {
        DsvpEvidenceMapper.AnimationEvidenceRow row = evidence.selectReviewedAnimation(sourceRef);
        if (row == null) {
            return null;
        }
        String license = row.licenseScope();
        if ("TEAM_ONLY".equalsIgnoreCase(license)
            && !(roles.contains("TEACHER") || roles.contains("ADMIN"))) {
            forbidden();
        }
        return new Candidate(row.chapterId(), row.id(), "ANIMATION_DEFINITION");
    }

    private void validateSourceType(DsvpEvidenceContext context, DsvpEvidenceSource source) {
        if (context.sourceType() != null && !source.type().equalsIgnoreCase(context.sourceType())) {
            throw AnimationErrorCode.DSVP_SOURCE_TYPE_INVALID.toException();
        }
    }

    private void forbidden() {
        throw AnimationErrorCode.DSVP_SOURCE_FORBIDDEN.toException();
    }

    private void conflict() {
        throw AnimationErrorCode.DSVP_CHAPTER_CONFLICT.toException();
    }

    private record Candidate(String chapterId, String sourceRef, String matchSource) {
    }
}
