package org.openhab.binding.supla.internal.cloud.api;

@FunctionalInterface
public interface IoDevicesCloudApiFactory {
    static IoDevicesCloudApiFactory getFactory() {
        return new GuavaIoDevicesCloudApiFactory(SwaggerIoDevicesCloudApiFactory.FACTORY);
    }

    IoDevicesCloudApi newIoDevicesCloudApi(String token);
}
