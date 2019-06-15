package org.openhab.binding.supla.internal.cloud.api;

final class GuavaIoDevicesCloudApiFactory implements IoDevicesCloudApiFactory {
    private final IoDevicesCloudApiFactory ioDevicesCloudApiFactory;

    GuavaIoDevicesCloudApiFactory(final IoDevicesCloudApiFactory ioDevicesCloudApiFactory) {
        this.ioDevicesCloudApiFactory = ioDevicesCloudApiFactory;
    }

    @Override
    public IoDevicesCloudApi newIoDevicesCloudApi(final String token) {
        return new GuavaIoDevicesCloudApi(ioDevicesCloudApiFactory.newIoDevicesCloudApi(token));
    }
}
