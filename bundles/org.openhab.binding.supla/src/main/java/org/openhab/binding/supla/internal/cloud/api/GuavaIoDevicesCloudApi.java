package org.openhab.binding.supla.internal.cloud.api;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import pl.grzeslowski.jsupla.api.generated.ApiException;
import pl.grzeslowski.jsupla.api.generated.model.Device;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

final class GuavaIoDevicesCloudApi implements IoDevicesCloudApi {
    private final LoadingCache<GetIoDeviceKey, Device> getIoDeviceCache;
    private final LoadingCache<List<String>, List<Device>> getIoDevicesCache;

    GuavaIoDevicesCloudApi(final IoDevicesCloudApi ioDevicesCloudApi) {
        getIoDeviceCache = CacheBuilder.newBuilder()
                                   .expireAfterWrite(GuavaCache.cacheEvictTime, GuavaCache.cacheEvictUnit)
                                   .build(new CacheLoader<GetIoDeviceKey, Device>() {
                                       @Override
                                       public Device load(@SuppressWarnings("NullableProblems") final GetIoDeviceKey key) throws Exception {
                                           GuavaCache.LOGGER.trace("Missed cache for ``getIoDevice");
                                           return ioDevicesCloudApi.getIoDevice(key.id, key.include);
                                       }
                                   });
        getIoDevicesCache = CacheBuilder.newBuilder()
                                    .expireAfterWrite(GuavaCache.cacheEvictTime, GuavaCache.cacheEvictUnit)
                                    .build(new CacheLoader<List<String>, List<Device>>() {
                                        @Override
                                        public List<Device> load(@SuppressWarnings("NullableProblems") final List<String> key) throws Exception {
                                            GuavaCache.LOGGER.trace("Missed cache for `getIoDevices`");
                                            return ioDevicesCloudApi.getIoDevices(key);
                                        }
                                    });
    }

    @Override
    public Device getIoDevice(final int id, final List<String> include) throws ApiException {
        try {
            return getIoDeviceCache.get(new GetIoDeviceKey(id, include));
        } catch (ExecutionException e) {
            throw new ApiException(e);
        }
    }

    @Override
    public List<Device> getIoDevices(final List<String> include) throws ApiException {
        try {
            return getIoDevicesCache.get(include);
        } catch (ExecutionException e) {
            throw new ApiException(e);
        }
    }

    private static final class GetIoDeviceKey {
        final int id;
        final List<String> include;

        private GetIoDeviceKey(final int id, final List<String> include) {
            this.id = id;
            this.include = Collections.unmodifiableList(include);
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof GetIoDeviceKey)) return false;
            final GetIoDeviceKey that = (GetIoDeviceKey) o;
            return id == that.id &&
                           Objects.equals(include, that.include);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public String toString() {
            return "GetIoDeviceKey{" +
                           "id=" + id +
                           ", include=" + include +
                           '}';
        }
    }
}
