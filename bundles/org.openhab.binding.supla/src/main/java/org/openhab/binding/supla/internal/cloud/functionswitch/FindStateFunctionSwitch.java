package org.openhab.binding.supla.internal.cloud.functionswitch;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.supla.internal.cloud.AdditionalChannelType;
import org.openhab.binding.supla.internal.cloud.ChannelFunctionDispatcher;
import org.openhab.binding.supla.internal.cloud.ChannelInfo;
import org.openhab.binding.supla.internal.cloud.ChannelInfoParser;
import org.openhab.binding.supla.internal.cloud.HsbTypeConverter;
import org.openhab.binding.supla.internal.cloud.executors.LedCommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.grzeslowski.jsupla.api.generated.model.Channel;
import pl.grzeslowski.jsupla.api.generated.model.ChannelState;

import java.math.BigDecimal;
import java.util.Optional;

import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.eclipse.smarthome.core.library.types.OnOffType.OFF;
import static org.eclipse.smarthome.core.library.types.OnOffType.ON;
import static org.openhab.binding.supla.internal.cloud.AdditionalChannelType.LED_BRIGHTNESS;

@SuppressWarnings("PackageAccessibility")
public class FindStateFunctionSwitch implements ChannelFunctionDispatcher.FunctionSwitch<Optional<? extends State>> {
    private final Logger logger = LoggerFactory.getLogger(FindStateFunctionSwitch.class);
    private final LedCommandExecutor ledCommandExecutor;
    private final ChannelUID channelUID;
    private final ChannelInfoParser channelInfoParser;

    public FindStateFunctionSwitch(LedCommandExecutor ledCommandExecutor, final ChannelUID channelUID, ChannelInfoParser channelInfoParser) {
        this.ledCommandExecutor = ledCommandExecutor;
        this.channelUID = channelUID;
        this.channelInfoParser = channelInfoParser;
    }

    public FindStateFunctionSwitch(LedCommandExecutor ledCommandExecutor, final ChannelUID channelUID) {
        this(ledCommandExecutor, channelUID, ChannelInfoParser.PARSER);
    }

    @Override
    public Optional<? extends State> onNone(Channel channel) {
        return Optional.empty();
    }

    @Override
    public Optional<? extends State> onControllingTheGatewayLock(Channel channel) {
        return hiType(channel);
    }

    @Override
    public Optional<? extends State> onControllingTheGate(Channel channel) {
        return optionalHiType(channel);
    }

    @Override
    public Optional<? extends State> onControllingTheGarageDoor(Channel channel) {
        return optionalHiType(channel);
    }

    @Override
    public Optional<? extends State> onThermometer(Channel channel) {
        return of(channel).map(Channel::getState).map(s -> findTemperature(s, channel.getParam2())).map(DecimalType::new);
    }

    @Override
    public Optional<? extends State> onHumidity(Channel channel) {
        return of(channel).map(Channel::getState).map(channelState -> findHumidity(channelState, channel.getParam3())).map(DecimalType::new);
    }

    @Override
    public Optional<? extends State> onHumidityAndTemperature(Channel channel) {
        final ChannelInfo channelInfo = channelInfoParser.parse(channelUID);
        final AdditionalChannelType channelType = channelInfo.getAdditionalChannelType();
        if (channelType == null) {
            throw new NullPointerException("Additional type for channel " + channel + " cannot bt null!");
        }
        final Optional<ChannelState> state = of(channel).map(Channel::getState);
        switch (channelType) {
            case TEMPERATURE:
                return state.map(s -> findTemperature(s, channel.getParam2())).map(DecimalType::new);
            case HUMIDITY:
                return state.map(s -> findHumidity(s, channel.getParam2())).map(DecimalType::new);
            default:
                throw new IllegalStateException("Additional type " + channelType + " is not supported for HumidityAndTemperature channel");
        }
    }

    private BigDecimal findTemperature(ChannelState channelState, Integer param2) {
        return findValueWithAdjustment(channelState.getTemperature(), param2);
    }

    private BigDecimal findHumidity(ChannelState channelState, Integer param3) {
        return findValueWithAdjustment(channelState.getHumidity(), param3);
    }

    private BigDecimal findValueWithAdjustment(BigDecimal value, Integer adjustment) {
        return Optional.ofNullable(adjustment)
                       .map(v -> v / 100)
                       .map(BigDecimal::new)
                       .orElse(BigDecimal.ZERO)
                       .add(value);
    }

    @Override
    public Optional<? extends State> onOpeningSensorGateway(Channel channel) {
        return hiType(channel);
    }

    @Override
    public Optional<? extends State> onOpeningSensorGate(Channel channel) {
        return hiType(channel);
    }

    @Override
    public Optional<? extends State> onOpeningSensorGarageDoor(Channel channel) {
        return hiType(channel);
    }

    @Override
    public Optional<? extends State> onNoLiquidSensor(Channel channel) {
        return hiType(channel);
    }

    @Override
    public Optional<? extends State> onControllingTheDoorLock(Channel channel) {
        return hiType(channel);
    }

    @Override
    public Optional<? extends State> onOpeningSensorDoor(Channel channel) {
        return hiType(channel);
    }

    @Override
    public Optional<? extends State> onControllingTheRollerShutter(Channel channel) {
        return of(channel)
                       .map(Channel::getState)
                       .map(ChannelState::getShut)
                       .map(shut -> 100 - shut)
                       .map(PercentType::new);
    }

    @Override
    public Optional<? extends State> onOpeningSensorRollerShutter(Channel channel) {
        return hiType(channel);
    }

    @Override
    public Optional<? extends State> onPowerSwitch(Channel channel) {
        return onOffType(channel);
    }

    @Override
    public Optional<? extends State> onLightSwitch(Channel channel) {
        return onOffType(channel);
    }

    private Optional<? extends State> onOffType(Channel channel) {
        return of(channel).map(Channel::getState).map(ChannelState::getOn).map(on -> on ? ON : OFF);
    }

    @Override
    public Optional<? extends State> onDimmer(Channel channel) {
        final Optional<PercentType> state = of(channel).map(Channel::getState)
                                                    .map(ChannelState::getBrightness)
                                                    .map(PercentType::new);
        state.ifPresent(s -> ledCommandExecutor.setLedState(channel.getId(), s));
        return state;
    }

    @Override
    public Optional<? extends State> onRgbLighting(Channel channel) {
        final Optional<HSBType> state = of(channel)
                                                .map(Channel::getState)
                                                .map(s -> HsbTypeConverter.INSTANCE.toHsbType(s.getColor(), s.getColorBrightness()));
        state.ifPresent(s -> ledCommandExecutor.setLedState(channel.getId(), s));
        return state;
    }

    @Override
    public Optional<? extends State> onDimmerAndRgbLightning(Channel channel) {
        final ChannelInfo channelInfo = channelInfoParser.parse(channelUID);
        AdditionalChannelType channelType = channelInfo.getAdditionalChannelType();
        if (channelType == null) {
            final Optional<HSBType> state = of(channel)
                                                    .map(Channel::getState)
                                                    .map(s -> HsbTypeConverter.INSTANCE.toHsbType(s.getColor(), s.getColorBrightness()));
            state.ifPresent(s -> ledCommandExecutor.setLedState(channel.getId(), s));
            return state;
        } else if (channelType == LED_BRIGHTNESS) {
            final Optional<PercentType> state = of(channel)
                                                        .map(Channel::getState)
                                                        .map(ChannelState::getBrightness)
                                                        .map(PercentType::new);
            state.ifPresent(s -> ledCommandExecutor.setLedState(channel.getId(), s));
            return state;
        } else {
            logger.warn("Do not know how to support {} on dimmer and RGB", channelType);
            return empty();
        }
    }

    @Override
    public Optional<? extends State> onDepthSensor(Channel channel) {
        return of(channel).map(Channel::getState).map(ChannelState::getDepth).map(DecimalType::new);
    }

    @Override
    public Optional<? extends State> onDistanceSensor(Channel channel) {
        return of(channel).map(Channel::getState).map(ChannelState::getDistance).map(DecimalType::new);
    }

    @Override
    public Optional<? extends State> onOpeningSensorWindow(Channel channel) {
        return hiType(channel);
    }

    @Override
    public Optional<? extends State> onMailSensor(Channel channel) {
        return hiType(channel);
    }

    @Override
    public Optional<? extends State> onWindSensor(Channel channel) {
        return Optional.empty();
    }

    @Override
    public Optional<? extends State> onPressureSensor(Channel channel) {
        return Optional.empty();
    }

    @Override
    public Optional<? extends State> onRainSensor(Channel channel) {
        return Optional.empty();
    }

    @Override
    public Optional<? extends State> onWeightSensor(Channel channel) {
        return Optional.empty();
    }

    @Override
    public Optional<? extends State> onWeatherStation(Channel channel) {
        return Optional.empty();
    }

    @Override
    public Optional<? extends State> onStaircaseTimer(Channel channel) {
        return onOffType(channel);
    }

    @Override
    public Optional<? extends State> onDefault(Channel channel) {
        logger.warn("Does not know how to map `{}` to OpenHAB state", channel.getState());
        return Optional.empty();
    }

    private Optional<? extends State> hiType(Channel channel) {
        boolean invertedLogic = channel.getParam3() != null && channel.getParam3() > 0;
        return of(channel)
                       .map(Channel::getState)
                       .map(ChannelState::getHi)
                       .map(hi -> invertedLogic ? !hi : hi)
                       .map(hi -> hi ? ON : OFF);
    }

    /**
     * For `CONTROLLINGTHEGATE` and `CONTROLLINGTHEGARAGEDOOR` `hi` exists only when `param2` is set.
     * <p>
     * From doc:
     * <p>
     * "hi is either true or false depending on paired opening sensor state; the hi value is provided only if the
     * channel has param2 set (i.e. has opening sensor chosen); partial_hi is either true or false depending on paired
     * secondary opening sensor state; the partial_hi value is provided only if the channel has param3 set (i.e. has
     * secondary opening sensor chosen)"
     * <p>
     * https://github.com/SUPLA/supla-cloud/wiki/Channel-Functions-states
     */
    private Optional<? extends State> optionalHiType(Channel channel) {
        boolean invertedLogic = channel.getParam3() != null && channel.getParam3() > 0;
        boolean param2Present = channel.getParam2() != null && channel.getParam2() > 0;
        if (param2Present || !channel.getType().isOutput()) {
            return of(channel)
                           .map(Channel::getState)
                           .map(ChannelState::getHi)
                           .map(hi -> invertedLogic ? !hi : hi)
                           .map(hi -> hi ? ON : OFF);
        } else {
            return empty();
        }
    }
}
