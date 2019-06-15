package org.openhab.binding.supla.internal.cloud.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

final class GuavaCache {
    static final Logger LOGGER = LoggerFactory.getLogger(GuavaCache.class);
    static final int cacheEvictTime = 30;
    static final TimeUnit cacheEvictUnit = SECONDS;

    // do not create this class, it's singleton
    private GuavaCache() {

    }
}
