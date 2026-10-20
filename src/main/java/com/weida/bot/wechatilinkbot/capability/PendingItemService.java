package com.weida.bot.wechatilinkbot.capability;

import com.weida.bot.wechatilinkbot.model.PendingItem;
import com.weida.bot.wechatilinkbot.model.PendingItemSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Aggregates pending items from all registered adapters with timeout
 * isolation, deduplication, source filtering, and natural sorting.
 */
@Component
public class PendingItemService {

    private static final Logger LOG = LoggerFactory.getLogger(PendingItemService.class);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Duration ADAPTER_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private final Map<String, PendingItemQuery> queryCache = new ConcurrentHashMap<>();
    private static final Pattern CN_YEAR_MONTH_DAY = Pattern.compile(
            "(\\d{4})年(\\d{1,2})月(\\d{1,2})日?");
    private static final Pattern CN_YEAR_MONTH = Pattern.compile(
            "(\\d{4})年(\\d{1,2})月");
    private static final Pattern CN_MONTH_DAY = Pattern.compile(
            "(\\d{1,2})月(\\d{1,2})日?");
    private static final DateTimeFormatter DATE_TIME_WITH_OPTIONAL_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]");

    private final List<PendingItemAdapter> adapters;
    private final Clock clock;
    private final ExecutorService executor = Executors.newCachedThreadPool(
            Thread.ofVirtual().name("pending-item-", 0).factory());

    @Autowired
    public PendingItemService(List<PendingItemAdapter> adapters) {
        this(adapters, Clock.system(SHANGHAI));
    }

    /** Constructor for tests with a fixed clock. */
    public PendingItemService(List<PendingItemAdapter> adapters, Clock clock) {
        this.adapters = adapters;
        this.clock = clock;
    }

    /** All pending items from all sources, numbered and sorted. */
    public List<PendingItem> listAll(String conversationId) {
        return listAllWithStatus(conversationId).items();
    }

    /** All pending items plus source-read warnings for user-facing tools. */
    public PendingItemQuery listAllWithStatus(String conversationId) {
        return merged(conversationId, null);
    }

    public List<PendingItem> listToday(String conversationId) {
        return listTodayWithStatus(conversationId).items();
    }

    public PendingItemQuery listTodayWithStatus(String conversationId) {
        Instant now = Instant.now(clock);
        return filterAndNumber(merged(conversationId, null),
                items -> filterByDay(items, now));
    }

    public List<PendingItem> listOverdue(String conversationId) {
        return listOverdueWithStatus(conversationId).items();
    }

    public PendingItemQuery listOverdueWithStatus(String conversationId) {
        Instant now = Instant.now(clock);
        return filterAndNumber(merged(conversationId, null), items -> items.stream()
                .filter(item -> item.dueTime().isBefore(now))
                .toList());
    }

    public List<PendingItem> listTomorrow(String conversationId) {
        return listTomorrowWithStatus(conversationId).items();
    }

    public PendingItemQuery listTomorrowWithStatus(String conversationId) {
        Instant now = Instant.now(clock);
        java.time.ZonedDateTime nowZone = now.atZone(SHANGHAI);
        java.time.ZonedDateTime tomorrowStart = nowZone.plusDays(1)
                .toLocalDate()
                .atStartOfDay(SHANGHAI);
        java.time.ZonedDateTime tomorrowEnd = tomorrowStart.plusDays(1);
        return filterAndNumber(merged(conversationId, null), items -> items.stream()
                .filter(item -> !item.dueTime().isBefore(tomorrowStart.toInstant())
                        && item.dueTime().isBefore(tomorrowEnd.toInstant()))
                .toList());
    }

    public List<PendingItem> listThisWeek(String conversationId) {
        return listThisWeekWithStatus(conversationId).items();
    }

    public PendingItemQuery listThisWeekWithStatus(String conversationId) {
        Instant now = Instant.now(clock);
        java.time.ZonedDateTime zdt = now.atZone(SHANGHAI);
        java.time.ZonedDateTime tomorrowEnd = zdt.plusDays(1)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(SHANGHAI);
        java.time.ZonedDateTime weekEnd = zdt.with(java.time.DayOfWeek.SUNDAY)
                .plusDays(1)
                .toLocalDate()
                .atStartOfDay(SHANGHAI);
        return filterAndNumber(merged(conversationId, null), items -> items.stream()
                .filter(item -> !item.dueTime().isBefore(tomorrowEnd.toInstant())
                        && item.dueTime().isBefore(weekEnd.toInstant()))
                .toList());
    }

    /** Items from a specific set of sources. */
    public List<PendingItem> listBySources(String conversationId, Set<PendingItemSource> sources) {
        return listBySourcesWithStatus(conversationId, sources).items();
    }

    public PendingItemQuery listBySourcesWithStatus(
            String conversationId, Set<PendingItemSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return listAllWithStatus(conversationId);
        }
        return merged(conversationId, sources);
    }

    /**
     * Default upcoming view: overdue plus the next 30 days.
     * Items without a deadline (represented by {@code Instant.MAX}) are excluded.
     */
    public PendingItemQuery listUpcomingWithStatus(
            String conversationId,
            Set<PendingItemSource> sources) {
        Instant now = Instant.now(clock);
        Instant boundary = now.plus(Duration.ofDays(30));
        PendingItemQuery query = merged(conversationId, sources);
        List<PendingItem> filtered = query.items().stream()
                .filter(item -> item.dueTime().isBefore(boundary)
                        || item.dueTime().equals(boundary))
                .toList();
        return filterAndNumber(query, items -> filtered);
    }

    /** Items due inside an inclusive-start / exclusive-end range. */
    public PendingItemQuery listRangeWithStatus(
            String conversationId,
            Set<PendingItemSource> sources,
            String from,
            String to) {
        Instant start = parseBoundary(from, true);
        Instant end = parseBoundary(to, false);
        PendingItemQuery query = merged(conversationId, sources);
        List<PendingItem> filtered = query.items().stream()
                .filter(item -> (start == null || !item.dueTime().isBefore(start))
                        && (end == null || item.dueTime().isBefore(end)))
                .toList();
        return filterAndNumber(query, items -> filtered);
    }

    // ── private helpers ────────────────────────────────────────────

    /** Clears cached query results for one conversation after data changes. */
    public void invalidate(String conversationId) {
        queryCache.keySet().removeIf(key -> key.startsWith(conversationId + '|'));
    }

    private PendingItemQuery merged(
            String conversationId,
            Set<PendingItemSource> sourceFilter) {
        String cacheKey = conversationId + '|'
                + (sourceFilter == null ? "*" : sourceFilter.hashCode());
        PendingItemQuery cached = queryCache.get(cacheKey);
        Instant now = Instant.now(clock);
        if (cached != null) {
            Instant expiresAt = now.minus(CACHE_TTL);
            if (cached.cachedAt().isAfter(expiresAt)) {
                return cached;
            }
            queryCache.remove(cacheKey, cached);
        }

        List<Future<AdapterResult>> futures = new ArrayList<>();
        for (PendingItemAdapter adapter : adapters) {
            if (sourceFilter != null && !sourceFilter.contains(adapter.source())) {
                continue;
            }
            futures.add(executor.submit(() -> fetchWithTimeout(adapter, conversationId)));
        }

        List<PendingItem> all = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Future<AdapterResult> future : futures) {
            try {
                AdapterResult result = future.get(
                        ADAPTER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                all.addAll(result.items());
                if (result.warning() != null) {
                    warnings.add(result.warning());
                }
            } catch (java.util.concurrent.TimeoutException e) {
                warnings.add("部分来源查询超时");
                future.cancel(true);
            } catch (Exception e) {
                Throwable cause = e instanceof java.util.concurrent.ExecutionException
                        && e.getCause() != null ? e.getCause() : e;
                LOG.warn("pending_item_merge_failed reason={}", cause.getClass().getSimpleName());
                warnings.add("部分来源暂时无法查询");
            }
        }

        List<PendingItem> deduped = deduplicateByTitle(all);
        deduped.sort(SORTER.apply(now));
        PendingItemQuery result = new PendingItemQuery(numbered(deduped), warnings);
        queryCache.put(cacheKey, result);
        return result;
    }

    /**
     * Fetches items from one adapter with a hard timeout. If the adapter
     * hangs or throws, the failure is logged and the remaining sources
     * continue unaffected.
     */
    private AdapterResult fetchWithTimeout(PendingItemAdapter adapter, String conversationId) {
        Future<List<PendingItem>> future = executor.submit(() -> {
            return adapter.fetchItems(conversationId);
        });
        try {
            return new AdapterResult(
                    future.get(ADAPTER_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS), null);
        } catch (java.util.concurrent.TimeoutException e) {
            LOG.warn("pending_item_adapter_timeout source={}", adapter.source());
            future.cancel(true);
            return new AdapterResult(List.of(), timeoutWarning(adapter.source()));
        } catch (Exception e) {
            Throwable cause = e instanceof java.util.concurrent.ExecutionException
                    && e.getCause() != null ? e.getCause() : e;
            String warning = warningFor(adapter.source(), cause);
            LOG.warn("pending_item_adapter_failed source={} reason={}",
                    adapter.source(), cause.getClass().getSimpleName());
            return new AdapterResult(List.of(), warning);
        }
    }

    private static String warningFor(PendingItemSource source, Throwable failure) {
        String sourceLabel = sourceLabel(source);
        if (failure instanceof PendingItemSourceException sourceFailure) {
            return sourceLabel + "待办暂时无法查询：" + sourceFailure.userMessage();
        }
        return sourceLabel + "待办暂时无法查询，请稍后重试。";
    }

    private static String timeoutWarning(PendingItemSource source) {
        return sourceLabel(source) + "待办查询超时，请稍后重试。";
    }

    private static String sourceLabel(PendingItemSource source) {
        return switch (source) {
            case COURSE -> "学习通";
            case EMAIL -> "邮箱";
            case PERSONAL -> "个人";
        };
    }

    private static PendingItemQuery filterAndNumber(
            PendingItemQuery query,
            java.util.function.Function<List<PendingItem>, List<PendingItem>> filter) {
        return new PendingItemQuery(numbered(filter.apply(query.items())), query.warnings());
    }

    /**
     * Deduplicates items that share the same title (case-insensitive,
     * whitespace-normalised). First occurrence wins.
     */
    static List<PendingItem> deduplicateByTitle(List<PendingItem> items) {
        Set<String> seen = new LinkedHashSet<>();
        List<PendingItem> result = new ArrayList<>();
        for (PendingItem item : items) {
            String key = item.title().strip().toLowerCase(java.util.Locale.ROOT);
            if (seen.add(key)) {
                result.add(item);
            }
        }
        return result;
    }

    private static List<PendingItem> filterByDay(List<PendingItem> items, Instant now) {
        return items.stream()
                .filter(item -> !item.dueTime().isBefore(now)
                        && isSameDay(item.dueTime(), now))
                .toList();
    }

    static List<PendingItem> numbered(List<PendingItem> items) {
        return IntStream.range(0, items.size())
                .mapToObj(i -> new PendingItem(
                        i + 1,
                        items.get(i).internalId(),
                        items.get(i).title(),
                        items.get(i).source(),
                        items.get(i).status(),
                        items.get(i).dueTime(),
                        items.get(i).note()))
                .toList();
    }

    /** Result of one aggregate query, including failures isolated to a source. */
    public record PendingItemQuery(List<PendingItem> items, List<String> warnings, Instant cachedAt) {

        public PendingItemQuery {
            items = List.copyOf(items == null ? List.of() : items);
            warnings = List.copyOf(warnings == null ? List.of() : warnings);
            cachedAt = cachedAt == null ? Instant.EPOCH : cachedAt;
        }

        public PendingItemQuery(List<PendingItem> items, List<String> warnings) {
            this(items, warnings, Instant.now());
        }
    }

    private record AdapterResult(List<PendingItem> items, String warning) {

        private AdapterResult {
            items = List.copyOf(items == null ? List.of() : items);
        }
    }

    // ── sorting ──────────────────────────────────────────────────

    interface SortFunction {
        Comparator<PendingItem> apply(Instant now);
    }

    static final SortFunction SORTER = now ->
            Comparator.<PendingItem, Boolean>comparing(
                    item -> item.dueTime().isBefore(now))  // overdue last (false first)
                    .reversed()  // overdue first
                    .thenComparing(PendingItem::dueTime)
                    .thenComparing(PendingItem::internalId);

    private static boolean isSameDay(Instant a, Instant b) {
        java.time.LocalDate dayA = java.time.LocalDate.ofInstant(a, SHANGHAI);
        java.time.LocalDate dayB = java.time.LocalDate.ofInstant(b, SHANGHAI);
        return dayA.equals(dayB);
    }

    private static Instant parseBoundary(String value, boolean start) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
            // try date forms below
        }
        try {
            LocalDate date = LocalDate.parse(normalized);
            return start
                    ? date.atStartOfDay(SHANGHAI).toInstant()
                    : date.plusDays(1).atStartOfDay(SHANGHAI).toInstant();
        } catch (DateTimeParseException ignored) {
            // try month form below
        }
        try {
            YearMonth month = YearMonth.parse(normalized);
            return start
                    ? month.atDay(1).atStartOfDay(SHANGHAI).toInstant()
                    : month.plusMonths(1).atDay(1).atStartOfDay(SHANGHAI).toInstant();
        } catch (DateTimeParseException ignored) {
            // try Chinese forms below
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(
                    normalized, DATE_TIME_WITH_OPTIONAL_SECONDS);
            return dateTime.atZone(SHANGHAI).toInstant();
        } catch (DateTimeParseException ignored) {
            // try Chinese date forms below
        }
        Matcher yearMonthDay = CN_YEAR_MONTH_DAY.matcher(normalized);
        if (yearMonthDay.find()) {
            LocalDate date = LocalDate.of(
                    Integer.parseInt(yearMonthDay.group(1)),
                    Integer.parseInt(yearMonthDay.group(2)),
                    Integer.parseInt(yearMonthDay.group(3)));
            return start
                    ? date.atStartOfDay(SHANGHAI).toInstant()
                    : date.plusDays(1).atStartOfDay(SHANGHAI).toInstant();
        }
        Matcher yearMonth = CN_YEAR_MONTH.matcher(normalized);
        if (yearMonth.find()) {
            YearMonth month = YearMonth.of(
                    Integer.parseInt(yearMonth.group(1)),
                    Integer.parseInt(yearMonth.group(2)));
            return start
                    ? month.atDay(1).atStartOfDay(SHANGHAI).toInstant()
                    : month.plusMonths(1).atDay(1).atStartOfDay(SHANGHAI).toInstant();
        }
        Matcher monthDay = CN_MONTH_DAY.matcher(normalized);
        if (monthDay.find()) {
            LocalDate today = LocalDate.now(SHANGHAI);
            LocalDate date = LocalDate.of(
                    today.getYear(),
                    Integer.parseInt(monthDay.group(1)),
                    Integer.parseInt(monthDay.group(2)));
            return start
                    ? date.atStartOfDay(SHANGHAI).toInstant()
                    : date.plusDays(1).atStartOfDay(SHANGHAI).toInstant();
        }
        throw new IllegalArgumentException(
                "无法解析待办时间范围「" + value + "」，请使用 2026-07-01、2026-07 或 7月1日 等格式");
    }
}
