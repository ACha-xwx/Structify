package com.feng.dsagent.resource;

import com.feng.dsagent.resource.service.ResourceContentService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.feng.dsagent.common.exception.BizException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.feng.dsagent.resource.config.ResourceProperties;
import com.feng.dsagent.resource.converter.ResourceConverterImpl;
import com.feng.dsagent.resource.domain.ResourceContent;
import com.feng.dsagent.resource.mapper.ResourceMapper;
class ResourceContentServiceTest {

    @TempDir
    Path resourceRoot;

    @Test
    void servesAReviewedPdfInsideTheConfiguredResourceRoot() throws Exception {
        Path file = resourceRoot.resolve("03-stack-queue/stack.pdf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "%PDF-1.7 sample");
        ResourceContentService service = service(row("stack-pdf", "03-stack-queue/stack.pdf"));

        ResourceContent content = service.content("stack-pdf", null);

        assertThat(content.filename()).isEqualTo("stack.pdf");
        assertThat(content.mediaType().toString()).isEqualTo("application/pdf");
        assertThat(content.inline()).isTrue();
        try (var input = content.resource().getInputStream()) {
            assertThat(new String(input.readAllBytes())).isEqualTo("%PDF-1.7 sample");
        }
    }

    @Test
    void rejectsPathsThatEscapeTheConfiguredResourceRoot() throws Exception {
        Path outside = resourceRoot.getParent().resolve("outside-private.txt");
        Files.writeString(outside, "private");
        ResourceContentService service = service(row("escape", "../outside-private.txt"));

        assertThatThrownBy(() -> service.content("escape", null))
            .isInstanceOfSatisfying(BizException.class, error -> {
                assertThat(error.code()).isEqualTo("RESOURCE_CONTENT_NOT_FOUND");
                assertThat(error.getMessage()).doesNotContain("outside-private");
            });
    }

    @Test
    void hidesAssetsWithoutPublishedMetadata() {
        ResourceContentService service = service(null);

        assertThatThrownBy(() -> service.content("missing", null))
            .isInstanceOfSatisfying(BizException.class, error ->
                assertThat(error.code()).isEqualTo("RESOURCE_NOT_FOUND")
            );
    }

    private ResourceContentService service(ResourceMapper.ResourceRow row) {
        ResourceMapper resources = mock(ResourceMapper.class);
        when(resources.selectPublishedById(eq("stack-pdf"), anyBoolean(), anyBoolean())).thenReturn(
            row != null && "stack-pdf".equals(row.id()) ? row : null
        );
        when(resources.selectPublishedById(eq("escape"), anyBoolean(), anyBoolean())).thenReturn(
            row != null && "escape".equals(row.id()) ? row : null
        );
        when(resources.selectPublishedById(eq("missing"), anyBoolean(), anyBoolean())).thenReturn(null);
        if (row != null) {
            when(resources.selectPublishedById(eq(row.id()), anyBoolean(), anyBoolean())).thenReturn(row);
        }
        return new ResourceContentService(
            resources,
            new ResourceConverterImpl(),
            new ResourceProperties(resourceRoot.toString())
        );
    }

    private ResourceMapper.ResourceRow row(String id, String filePath) {
        return new ResourceMapper.ResourceRow(
            id,
            "03-stack-queue",
            "PDF",
            "栈与队列讲义",
            "审核后资料",
            filePath,
            "课程组",
            "1.0",
            "PUBLISHED",
            "PUBLIC"
        );
    }
}
