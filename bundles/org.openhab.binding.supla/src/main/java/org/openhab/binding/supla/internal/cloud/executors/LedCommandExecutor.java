package org.openhab.binding.supla.internal.cloud.executors;

import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.PercentType;
import pl.grzeslowski.jsupla.api.generated.ApiException;

public interface LedCommandExecutor {
    void setLedState(final int channelId, final PercentType brightness);

    void setLedState(final int channelId, final HSBType hsb);

    void changeColor(final int channelId, final HSBType command) throws ApiException;

    void changeColorBrightness(final int channelId, final PercentType command) throws ApiException;

    void changeBrightness(final int channelId, final PercentType command) throws ApiException;
}
