package org.stt.query;

import net.engio.mbassy.bus.MBassador;
import net.engio.mbassy.listener.Handler;
import org.stt.StopWatch;
import org.stt.model.ItemModified;
import org.stt.model.TimeTrackingItem;
import org.stt.persistence.ItemReader;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;
import static org.stt.Streams.distinctByKey;

@Singleton
public class TimeTrackingItemQueries {
    private static final Logger LOG = Logger.getLogger(TimeTrackingItemQueries.class.getSimpleName());
    private final Provider<ItemReader> provider;
    private List<TimeTrackingItem> cachedItems;

	/**
     * @param provider
     *            where to search for items
     */
    @Inject
    public TimeTrackingItemQueries(Provider<ItemReader> provider,
                                   Optional<MBassador<Object>> eventbus) {
        this.provider = requireNonNull(provider);
        eventbus.ifPresent(bus -> bus.subscribe(this));
    }

    @Handler(priority = Integer.MAX_VALUE)
    public synchronized void sourceChanged(ItemModified event) {
        cachedItems = null;
        LOG.fine("Clearing query cache");
    }

    /**
     * Returns the item which is ongoing (even if it starts in the future). This is neccessarily the last item.
     */
    public Optional<TimeTrackingItem> getOngoingItem() {
        return getLastItem().filter(timeTrackingItem -> !timeTrackingItem.getEnd().isPresent());
    }

    public Optional<TimeTrackingItem> getLastItem() {
        validateCache();
        return cachedItems.isEmpty() ? Optional.empty() : Optional.of(cachedItems.get(cachedItems.size() - 1));
    }

    /**
     * Returns the items coming directly before and directly after the give item.
     * There will be no gap between previousItem, forItem and nextItem
     */
    public AdjacentItems getAdjacentItems(TimeTrackingItem forItem) {
        validateCache();
        int itemIndex = cachedItems.indexOf(forItem);
        TimeTrackingItem previous = null;
        if (itemIndex > 0) {
            TimeTrackingItem potentialPrevious = cachedItems.get(itemIndex - 1);
            if (potentialPrevious.getEnd().filter(forItem.getStart()::equals).isPresent()) {
                previous = potentialPrevious;
            }
        }
        TimeTrackingItem next = null;
        if (itemIndex < cachedItems.size() - 1) {
            TimeTrackingItem potentialNext = cachedItems.get(itemIndex + 1);
            if (forItem.getEnd().filter(potentialNext.getStart()::equals).isPresent()) {
                next = potentialNext;
            }
        }
        return new AdjacentItems(previous, next);
    }

    /**
     * @return a {@link Stream} containing all time tracking items, be sure to {@link Stream#close()} it!
     */
    public Stream<LocalDate> queryAllTrackedDays() {
        return queryAllItems()
                .map(TimeTrackingItem::getStart)
                .map(LocalDateTime::toLocalDate)
                .distinct();
    }

    public Stream<TimeTrackingItem> queryFirstItemsOfDays() {
        return queryAllItems()
                .filter(distinctByKey(item -> item.getStart().toLocalDate()));
    }

    /**
     * @return a {@link Stream} containing all time tracking items matching the given criteria, be sure to {@link Stream#close()} it!
     */
    public Stream<TimeTrackingItem> queryItems(Criteria criteria) {
        return queryAllItems()
                .filter(criteria::matches);
    }

    /**
     * @return a {@link Stream} containing all time tracking items, be sure to {@link Stream#close()} it!
     */
    public Stream<TimeTrackingItem> queryAllItems() {
        validateCache();
        return cachedItems.stream();
    }

    private synchronized void validateCache() {
        if (cachedItems == null) {
            LOG.fine("Rebuilding cache");
            StopWatch stopWatch = new StopWatch("Query cache rebuild");
            cachedItems = new ArrayList<>(2000);
            try (ItemReader reader = provider.get()) {
                Optional<TimeTrackingItem> itemOptional;
                while ((itemOptional = reader.read()).isPresent()) {
                    cachedItems.add(itemOptional.get());
                }
            }
            stopWatch.stop();
        }
    }

    public static class AdjacentItems {
        private final TimeTrackingItem previousItem;
        private final TimeTrackingItem nextItem;

        public AdjacentItems(TimeTrackingItem previousItem, TimeTrackingItem nextItem) {
            this.previousItem = previousItem;
            this.nextItem = nextItem;
        }

        public Optional<TimeTrackingItem> previousItem() {
            return Optional.ofNullable(previousItem);
        }

        public Optional<TimeTrackingItem> nextItem() {
            return Optional.ofNullable(nextItem);
        }
    }
}
