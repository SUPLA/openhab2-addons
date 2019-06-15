package org.openhab.binding.supla.internal.cloud.api;

final class GuavaChannelsCloudApiFactory implements ChannelsCloudApiFactory {
    private final ChannelsCloudApiFactory channelsCloudApiFactory;

    GuavaChannelsCloudApiFactory(final ChannelsCloudApiFactory channelsCloudApiFactory) {
        this.channelsCloudApiFactory = channelsCloudApiFactory;
    }

    @Override
    public ChannelsCloudApi newChannelsCloudApi(final String token) {
        return new GuavaChannelsCloudApi(channelsCloudApiFactory.newChannelsCloudApi(token));
    }
}
