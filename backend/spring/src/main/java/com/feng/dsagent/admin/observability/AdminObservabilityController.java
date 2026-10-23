package com.feng.dsagent.admin.observability;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the console could not see: the studyroom's own job ledger, the tokens it spends, and whether those paths
 * are alive at all. Read-only by design - nothing here changes a job.
 */
@RestController
@RequestMapping("/api/v1/admin/observability")
@PreAuthorize("hasRole('ADMIN')")
class AdminObservabilityController {

    private final AdminObservabilityService observability;

    AdminObservabilityController(AdminObservabilityService observability) {
        this.observability = observability;
    }

    /**
     * Both job ledgers in one page.
     *
     * @param source BACKGROUND or STUDYROOM; omit for both
     * @param status optional ledger status filter
     * @param userId optional owner filter
     */
    @GetMapping("/jobs")
    AdminObservabilityService.JobPage jobs(
        @RequestParam(required = false) String source,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Long userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return observability.jobs(source, status, userId, page, size);
    }

    /**
     * Tokens actually spent between two days.
     *
     * @param groupBy DAY (default) or USER
     */
    @GetMapping("/cost")
    AdminObservabilityService.CostReport cost(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "DAY") String groupBy
    ) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        return observability.cost(start, end, groupBy);
    }

    /** Module availability with counts, for the console home: studyroom, learning, quota. */
    @GetMapping("/modules")
    List<AdminObservabilityService.ModuleState> modules() {
        return observability.modules();
    }
}
