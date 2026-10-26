package com.feng.dsagent.review.mapper;

import com.feng.dsagent.review.domain.ReviewType;
import com.feng.dsagent.review.entity.ReviewEntity;
import com.feng.dsagent.review.entity.ReviewSourceEntity;
import com.feng.dsagent.review.query.ReviewQuery;
import com.feng.dsagent.review.entity.ReviewHistoryEntity;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 职责：审核项、来源与历史记录的持久化访问。
 */
@Mapper
public interface ReviewMapper {

    long countReviews(ReviewQuery query);

    List<ReviewEntity> selectReviews(ReviewQuery query);

    ReviewEntity find(@Param("type") ReviewType type, @Param("id") String id);

    ReviewEntity findPresentationPageBySourceRef(@Param("sourceRef") String sourceRef);

    ReviewEntity lock(@Param("type") ReviewType type, @Param("id") String id);

    ReviewSourceEntity chapter(@Param("id") String id);

    int updateStatus(
        @Param("type") ReviewType type,
        @Param("id") String id,
        @Param("status") String status
    );

    int appendReviewEvent(
        @Param("type") ReviewType type,
        @Param("id") String id,
        @Param("previousStatus") String previousStatus,
        @Param("nextStatus") String nextStatus,
        @Param("note") String note,
        @Param("reviewerUserId") long reviewerUserId,
        @Param("requestId") String requestId
    );

    int appendAdminAudit(
        @Param("actorUserId") long actorUserId,
        @Param("type") ReviewType type,
        @Param("id") String id,
        @Param("result") String result,
        @Param("requestId") String requestId,
        @Param("beforeSummary") String beforeSummary,
        @Param("afterSummary") String afterSummary
    );

    List<ReviewHistoryEntity> history(@Param("type") ReviewType type, @Param("id") String id);

    default Optional<ReviewEntity> findOptional(ReviewType type, String id) {
        return Optional.ofNullable(find(type, id));
    }

    default Optional<ReviewEntity> findPresentationPageBySourceRefOptional(String sourceRef) {
        return Optional.ofNullable(findPresentationPageBySourceRef(sourceRef));
    }

    default Optional<ReviewEntity> lockOptional(ReviewType type, String id) {
        return Optional.ofNullable(lock(type, id));
    }

    default Optional<ReviewSourceEntity> chapterOptional(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(chapter(id));
    }
}
