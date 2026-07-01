package com.textellent.mcp.services.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

class TextellentPagedListMergerTest {

    @Test
    void shouldStopBeforeAppendingDuplicatePageContent() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String pageBody = """
                {
                  "tags": [
                    { "tagName": "BRM" },
                    { "tagName": "Cooper" }
                  ]
                }
                """;
        AtomicInteger fetchCount = new AtomicInteger(0);

        TextellentPagedListMerger.MergeOrRaw result = TextellentPagedListMerger.mergeAllRowsOrRaw(
                mapper,
                LoggerFactory.getLogger(TextellentPagedListMergerTest.class),
                "tags_get_summary_test",
                pageNum -> {
                    fetchCount.incrementAndGet();
                    return pageBody;
                },
                TextellentPagedListMerger::parseTagsListPage,
                10);

        assertNull(result.rawFirstPageIfUnparsed);
        assertNotNull(result.merged);
        assertEquals(2, result.merged.size());
        assertEquals("BRM", result.merged.get(0).get("tagName"));
        assertEquals("Cooper", result.merged.get(1).get("tagName"));
        assertEquals(2, fetchCount.get());
    }

    @Test
    void shouldMergeDistinctPagesUntilPartialPage() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String page1 = """
                {
                  "tags": [
                    { "tagName": "BRM" },
                    { "tagName": "Cooper" }
                  ],
                  "pageSize": 2
                }
                """;
        String page2 = """
                {
                  "tags": [
                    { "tagName": "Ferrari" }
                  ],
                  "pageSize": 2
                }
                """;

        TextellentPagedListMerger.MergeOrRaw result = TextellentPagedListMerger.mergeAllRowsOrRaw(
                mapper,
                LoggerFactory.getLogger(TextellentPagedListMergerTest.class),
                "tags_get_summary_test",
                pageNum -> pageNum == 1 ? page1 : page2,
                TextellentPagedListMerger::parseTagsListPage,
                10);

        assertNull(result.rawFirstPageIfUnparsed);
        assertNotNull(result.merged);
        assertEquals(3, result.merged.size());
        assertEquals(
                List.of("BRM", "Cooper", "Ferrari"),
                result.merged.stream().map(row -> String.valueOf(row.get("tagName"))).toList());
    }

    @Test
    void shouldStopOnDuplicateSecondPageEvenWhenPageSizeHintExists() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String pageBody = """
                {
                  "tags": [
                    { "tagName": "BRM" },
                    { "tagName": "Cooper" }
                  ],
                  "pageSize": 2
                }
                """;

        TextellentPagedListMerger.MergeOrRaw result = TextellentPagedListMerger.mergeAllRowsOrRaw(
                mapper,
                LoggerFactory.getLogger(TextellentPagedListMergerTest.class),
                "tags_get_summary_test",
                pageNum -> pageBody,
                TextellentPagedListMerger::parseTagsListPage,
                10);

        assertNull(result.rawFirstPageIfUnparsed);
        assertNotNull(result.merged);
        assertEquals(2, result.merged.size());
        assertEquals(
                List.of("BRM", "Cooper"),
                result.merged.stream().map(row -> String.valueOf(row.get("tagName"))).toList());
    }
}
