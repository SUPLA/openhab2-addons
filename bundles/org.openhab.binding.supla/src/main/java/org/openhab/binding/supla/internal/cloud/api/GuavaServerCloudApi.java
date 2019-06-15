package org.openhab.binding.supla.internal.cloud.api;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import pl.grzeslowski.jsupla.api.generated.ApiException;
import pl.grzeslowski.jsupla.api.generated.model.ServerInfo;

import java.util.concurrent.ExecutionException;

final class GuavaServerCloudApi implements ServerCloudApi {
    private final LoadingCache<String, ServerInfo> getServerInfoCache;

    public GuavaServerCloudApi(final ServerCloudApi serverCloudApi) {
        getServerInfoCache = CacheBuilder.newBuilder()
                                     .expireAfterWrite(GuavaCache.cacheEvictTime, GuavaCache.cacheEvictUnit)
                                     .build(new CacheLoader<String, ServerInfo>() {
                                         @SuppressWarnings("NullableProblems")
                                         @Override
                                         public ServerInfo load(final String __) throws Exception {
                                             GuavaCache.LOGGER.trace("Missed cache for `getServerInfo`");
                                             return serverCloudApi.getServerInfo();
                                         }
                                     });
    }

    @Override
    public ServerInfo getServerInfo() throws ApiException {
        try {
            return getServerInfoCache.get("getServerInfo()");
        } catch (ExecutionException e) {
            throw new ApiException(e);
        }
    }
}
