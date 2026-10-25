package com.feng.dsagent.admin.mapper;

import java.sql.Timestamp;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminKnowledgeMapper {

    public Long countAllChunks();

    public List<CountBucketRow> countByReviewStatus();

    public List<CountBucketRow> countBySourceBucket();

    public Long countChunksFiltered(
        @Param("chapterId") String chapterId,
        @Param("status") String status,
        @Param("sourceLike") String sourceLike,
        @Param("queryLike") String queryLike
    );

    public List<ChunkListRow> selectChunksFiltered(
        @Param("chapterId") String chapterId,
        @Param("status") String status,
        @Param("sourceLike") String sourceLike,
        @Param("queryLike") String queryLike,
        @Param("limit") int limit,
        @Param("offset") int offset
    );

    public ChunkDetailRow selectChunkById(@Param("chunkId") String chunkId);

    public int insertChunk(
        @Param("id") String id,
        @Param("chapterId") String chapterId,
        @Param("title") String title,
        @Param("content") String content,
        @Param("sourcePath") String sourcePath,
        @Param("pageLabel") String pageLabel,
        @Param("reviewStatus") String reviewStatus,
        @Param("licenseScope") String licenseScope
    );

    public int updateChunk(
        @Param("chunkId") String chunkId,
        @Param("chapterId") String chapterId,
        @Param("title") String title,
        @Param("content") String content,
        @Param("pageLabel") String pageLabel,
        @Param("reviewStatus") String reviewStatus,
        @Param("licenseScope") String licenseScope
    );

    public int deleteChunk(@Param("chunkId") String chunkId);

    public Integer countChapterById(@Param("chapterId") String chapterId);

    public record CountBucketRow(String bucket, long n) {
    }

    public record ChunkListRow(
        String id,
        String chapterId,
        String title,
        String sourcePath,
        String pageLabel,
        String reviewStatus,
        String licenseScope,
        int retrievable,
        int contentLength,
        Timestamp createdAt
    ) {
    }

    public record ChunkDetailRow(
        String id,
        String chapterId,
        String title,
        String content,
        String sourcePath,
        String pageLabel,
        String reviewStatus,
        String licenseScope,
        int retrievable,
        Timestamp createdAt
    ) {
    }
}
