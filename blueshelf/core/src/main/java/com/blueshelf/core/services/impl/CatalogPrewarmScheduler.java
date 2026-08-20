package com.blueshelf.core.services.impl;

import com.blueshelf.core.services.CatalogService;
import com.blueshelf.core.services.catalog.ProductQuery;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sling Scheduler (exercise NOTES/03#5): keeps the hottest catalog queries warm so the FIRST visitor
 * after a cache expiry never pays the backend latency.
 *
 * Mechanics: the Sling Commons Scheduler (Quartz under the hood) picks up any service registered with
 * scheduler.* properties — the OSGi "whiteboard" pattern: you don't call the scheduler, you register
 * and it finds you. Alternatives you should be able to name in an interview:
 *  - scheduler.expression = cron ("0 0/5 * * * ?") instead of scheduler.period
 *  - Sling Jobs (JobManager) when you need guaranteed, distributed, exactly-once-ish execution
 *  - AEMaaCS gotcha: schedulers run on EVERY instance; gate with scheduler.runOn or run-mode config
 *    (prewarming publish caches on author is pointless — in prod this would live in config.publish only).
 */
@Component(service = Runnable.class, property = {
        "scheduler.period:Long=300",     // every 5 minutes
        "scheduler.immediate:Boolean=true",
        "scheduler.concurrent:Boolean=false" // never overlap runs — slow backends + overlapping jobs = thread pile-up
})
public class CatalogPrewarmScheduler implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogPrewarmScheduler.class);
    private static final String[] HOT_CATEGORIES = {"tvs", "laptops"};

    @Reference
    private CatalogService catalogService;

    @Override
    public void run() {
        for (String category : HOT_CATEGORIES) {
            var page = catalogService.search(ProductQuery.category(category, 6));
            LOG.info("Prewarm {}: {} products (source={})", category, page.getItems().size(), page.getSource());
        }
    }
}
