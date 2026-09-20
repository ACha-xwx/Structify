package com.feng.dsagent.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The reviewed pages keep section numbers as running text, so reading them back has to be strict enough
 * not to mistake a figure caption or a sentence for a section title.
 */
class TextbookSectionsTest {

    @Test
    void recognisesASectionLineWithAnIdeographicSpace() {
        assertThat(TextbookSections.line("8.3.4\u3000二叉搜索树的节点删除")).isEqualTo("8.3.4 二叉搜索树的节点删除");
        assertThat(TextbookSections.line("8.3.1 二叉排序树")).isEqualTo("8.3.1 二叉排序树");
    }

    @Test
    void readsTheRunningHeaderTheBookPrintsOnTopOfEachPage() {
        // The header names the section and the printed page, and some sections are marked optional.
        assertThat(TextbookSections.line("1.1\u3000数据结构的基础概念\u3000· 3 ·")).isEqualTo("1.1 数据结构的基础概念");
        assertThat(TextbookSections.line("1.3\u3000算　　法\u3000· 9 ·")).isEqualTo("1.3 算 法");
        assertThat(TextbookSections.line("*8.3.3\u3000B 树")).isEqualTo("8.3.3 B 树");
    }

    @Test
    void refusesCaptionsAndProseThatMerelyStartWithANumber() {
        assertThat(TextbookSections.line("图 8.3.1 二叉排序树示意")).isNull();
        assertThat(TextbookSections.line("8.3.4 二叉搜索树中的删除操作是这样的，要先找到结点然后分三种情况处理，最后还要维护性质")).isNull();
        assertThat(TextbookSections.line("1. 先找到要删除的结点")).isNull();
        assertThat(TextbookSections.line("例 8.2 设关键字的输入顺序为 45，24，53")).isNull();
        assertThat(TextbookSections.line("")).isNull();
    }

    @Test
    void widensSectionNumbersOneLevelAtATime() {
        assertThat(TextbookSections.parent("8.3.4")).isEqualTo("8.3");
        assertThat(TextbookSections.parent("8.3")).isEmpty();
        assertThat(TextbookSections.chapter("8.3.4")).isEqualTo("8");
        assertThat(TextbookSections.under("8.3.4", "8.3")).isTrue();
        assertThat(TextbookSections.under("8.3", "8.3.4")).isFalse();
        assertThat(TextbookSections.under("9.1", "8.3")).isFalse();
    }

    @Test
    void reportsEverySectionInsideOnePageInReadingOrder() {
        String page = """
            ### 教材页 276（PDF页 287）
            8.3.3　B 树
            这是一棵多路查找树。
            8.3.4　二叉搜索树的节点删除
            删除分三种情况。
            """;
        assertThat(TextbookSections.spans(page)).extracting(TextbookSections.Span::section)
            .containsExactly("8.3.3 B 树", "8.3.4 二叉搜索树的节点删除");
        assertThat(TextbookSections.carried(page, "8.3")).isEqualTo("8.3.4 二叉搜索树的节点删除");
        assertThat(TextbookSections.carried("普通正文，没有小节行。", "8.3")).isEqualTo("8.3");
    }
}
