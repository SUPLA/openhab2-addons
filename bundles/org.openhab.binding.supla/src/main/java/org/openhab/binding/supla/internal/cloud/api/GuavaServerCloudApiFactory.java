package org.openhab.binding.supla.internal.cloud.api;

final class GuavaServerCloudApiFactory implements ServerCloudApiFactory {
    private final ServerCloudApiFactory serverCloudApiFactory;

    GuavaServerCloudApiFactory(final ServerCloudApiFactory serverCloudApiFactory) {
        this.serverCloudApiFactory = serverCloudApiFactory;
    }

    @Override
    public ServerCloudApi newServerCloudApi(final String token) {
        return new GuavaServerCloudApi(serverCloudApiFactory.newServerCloudApi(token));
    }
}
