package org.openhab.binding.supla.internal.cloud.api;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import pl.grzeslowski.jsupla.api.generated.ApiException;
import pl.grzeslowski.jsupla.api.generated.model.Channel;
import pl.grzeslowski.jsupla.api.generated.model.ChannelExecuteActionRequest;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static java.util.Collections.unmodifiableList;

final class GuavaChannelsCloudApi implements ChannelsCloudApi {
    private final ChannelsCloudApi channelsCloudApi;
    private final LoadingCache<GetChannelKey, Channel> getChannelCache;

    GuavaChannelsCloudApi(final ChannelsCloudApi channelsCloudApi) {
        this.channelsCloudApi = channelsCloudApi;
        getChannelCache = CacheBuilder.newBuilder()
                                  .expireAfterWrite(GuavaCache.cacheEvictTime, GuavaCache.cacheEvictUnit)
                                  .build(new CacheLoader<GetChannelKey, Channel>() {
                                      @Override
                                      public Channel load(@SuppressWarnings("NullableProblems") final GetChannelKey key) throws Exception {
                                          GuavaCache.LOGGER.trace("Missed cache for `getChannel`");
                                          return channelsCloudApi.getChannel(key.id, key.include);
                                      }
                                  });
    }

    @Override
    public void executeAction(final ChannelExecuteActionRequest body, final Integer id) throws ApiException {
        channelsCloudApi.executeAction(body, id);
    }

    @Override
    public Channel getChannel(final int id, final List<String> include) throws ApiException {
        try {
            return getChannelCache.get(new GetChannelKey(id, include));
        } catch (ExecutionException e) {
            throw new ApiException(e);
        }
    }

    private static final class GetChannelKey {
        final int id;
        final List<String> include;

        private GetChannelKey(final int id, final List<String> include) {
            this.id = id;
            this.include = unmodifiableList(include);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof GetChannelKey)) return false;
            final GetChannelKey that = (GetChannelKey) o;
            return id == that.id &&
                           Objects.equals(include, that.include);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "GetChannelKey{" +
                           "id=" + id +
                           ", include=" + include +
                           '}';
        }
    }
}
