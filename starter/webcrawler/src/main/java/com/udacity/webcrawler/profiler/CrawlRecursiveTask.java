package com.udacity.webcrawler.profiler;

import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class CrawlRecursiveTask extends RecursiveTask<Boolean> {
    private final String url;
    private final Instant deadline;
    private final int maxDepth;
    private final ConcurrentMap<String, Integer> counts;
    private final ConcurrentSkipListSet<String> visitedUrls;
    private final Clock clock;
    private final PageParserFactory parserFactory;
    private final List<Pattern> ignoredUrls;

    public String getUrl() {
        return url;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public ConcurrentMap<String, Integer> getCounts() {
        return counts;
    }

    public ConcurrentSkipListSet<String> getVisitedUrls() {
        return visitedUrls;
    }

    public Clock getClock() {
        return clock;
    }

    public PageParserFactory getParserFactory() {
        return parserFactory;
    }

    public List<Pattern> getIgnoredUrls() {
        return ignoredUrls;
    }

    private CrawlRecursiveTask(String url,
                               Instant deadline,
                               int maxDepth,
                               ConcurrentMap<String, Integer> counts,
                               ConcurrentSkipListSet<String> visitedUrls,
                               Clock clock,
                               PageParserFactory parserFactory,
                               List<Pattern> ignoredUrls) {
        this.url = url;
        this.deadline = deadline;
        this.maxDepth = maxDepth;
        this.counts = counts;
        this.visitedUrls = visitedUrls;
        this.clock = clock;
        this.parserFactory = parserFactory;
        this.ignoredUrls = ignoredUrls;
    }

    protected Boolean compute() {
        if (maxDepth == 0 || clock.instant().isAfter(deadline)) {
            return false;
        }
        for (Pattern pattern : ignoredUrls) {
            if (pattern.matcher(url).matches()) {
                return false;
            }
        }
        synchronized (this) {
            if (visitedUrls.contains(url)) {
                return false;
            }
            visitedUrls.add(url);
        }
        PageParser.Result result = parserFactory.get(url).parse();
        ExecutorService executor = Executors.newFixedThreadPool(12);
//        for (Map.Entry<String, Integer> e : result.getWordCounts().entrySet()) {
        List<Future<?>> futures = new ArrayList<>();
        for (ConcurrentMap.Entry<String, Integer> e : result.getWordCounts().entrySet()) {
            /* if (counts.containsKey(e.getKey())) {
                counts.put(e.getKey(), e.getValue() + counts.get(e.getKey()));
            } else {
                counts.put(e.getKey(), e.getValue());
            }*/
            futures.add(
                    executor.submit(() -> {
                        counts.compute(e.getKey(), (k , v) -> v == null ? e.getValue() : e.getValue() + v);
                    }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        executor.shutdown();

        List<CrawlRecursiveTask> subtasks = new ArrayList<>();
        for (String link : result.getLinks()) {
            // crawlInternal(link, deadline, maxDepth - 1, counts, visitedUrls);
            subtasks.add(new CrawlRecursiveTask(
                    link,
                    deadline,
                    maxDepth - 1,
                    counts,
                    visitedUrls,
                    clock,
                    parserFactory,
                    ignoredUrls));
        }
        invokeAll(subtasks);
        return true;
    }

    public static final class Builder {
        private String url;
        private Instant deadline;
        private int maxDepth;
        private ConcurrentMap<String, Integer> counts;
        private ConcurrentSkipListSet<String> visitedUrls;
        private Clock clock;
        private PageParserFactory parserFactory;
        private List<Pattern> ignoredUrls;

        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        public Builder setDeadline(Instant deadline) {
            this.deadline = deadline;
            return this;
        }

        public Builder setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public Builder setCounts(ConcurrentMap<String, Integer> counts) {
            this.counts = counts;
            return this;
        }

        public Builder setVisitedUrls(ConcurrentSkipListSet<String> visitedUrls) {
            this.visitedUrls = visitedUrls;
            return this;
        }

        public Builder setClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder setParserFactory(PageParserFactory parserFactory) {
            this.parserFactory = parserFactory;
            return this;
        }

        public Builder setIgnoredUrls(List<Pattern> ignoredUrls) {
            this.ignoredUrls = ignoredUrls;
            return this;
        }

        public CrawlRecursiveTask build() {
            return new CrawlRecursiveTask(url,
                    deadline,
                    maxDepth,
                    counts,
                    visitedUrls,
                    clock,
                    parserFactory,
                    ignoredUrls);
        }
    }

}
