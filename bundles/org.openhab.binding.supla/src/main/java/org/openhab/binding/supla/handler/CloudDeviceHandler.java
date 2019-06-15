package org.openhab.binding.supla.handler;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.HSBType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StopMoveType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.library.types.UpDownType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.binding.BridgeHandler;
import org.eclipse.smarthome.core.thing.binding.builder.ThingBuilder;
import org.eclipse.smarthome.core.types.State;
import org.openhab.binding.supla.internal.cloud.ChannelFunctionDispatcher;
import org.openhab.binding.supla.internal.cloud.ChannelInfo;
import org.openhab.binding.supla.internal.cloud.ChannelInfoParser;
import org.openhab.binding.supla.internal.cloud.api.ChannelsCloudApi;
import org.openhab.binding.supla.internal.cloud.api.ChannelsCloudApiFactory;
import org.openhab.binding.supla.internal.cloud.api.IoDevicesCloudApi;
import org.openhab.binding.supla.internal.cloud.api.IoDevicesCloudApiFactory;
import org.openhab.binding.supla.internal.cloud.executors.LedCommandExecutor;
import org.openhab.binding.supla.internal.cloud.executors.LedCommandExecutorFactory;
import org.openhab.binding.supla.internal.cloud.executors.SuplaLedCommandExecutorFactory;
import org.openhab.binding.supla.internal.cloud.functionswitch.CreateChannelFunctionSwitch;
import org.openhab.binding.supla.internal.cloud.functionswitch.FindStateFunctionSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.grzeslowski.jsupla.api.generated.ApiException;
import pl.grzeslowski.jsupla.api.generated.model.ChannelExecuteActionRequest;
import pl.grzeslowski.jsupla.api.generated.model.ChannelFunctionActionEnum;
import pl.grzeslowski.jsupla.api.generated.model.Device;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;
import static java.lang.String.valueOf;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.eclipse.smarthome.core.library.types.OnOffType.ON;
import static org.eclipse.smarthome.core.library.types.UpDownType.UP;
import static org.eclipse.smarthome.core.thing.ThingStatus.OFFLINE;
import static org.eclipse.smarthome.core.thing.ThingStatus.ONLINE;
import static org.eclipse.smarthome.core.thing.ThingStatusDetail.BRIDGE_UNINITIALIZED;
import static org.eclipse.smarthome.core.thing.ThingStatusDetail.COMMUNICATION_ERROR;
import static org.eclipse.smarthome.core.thing.ThingStatusDetail.CONFIGURATION_ERROR;
import static org.eclipse.smarthome.core.thing.ThingStatusDetail.NONE;
import static org.eclipse.smarthome.core.types.RefreshType.REFRESH;
import static org.openhab.binding.supla.SuplaBindingConstants.SUPLA_DEVICE_CLOUD_ID;
import static org.openhab.binding.supla.internal.cloud.AdditionalChannelType.LED_BRIGHTNESS;
import static org.openhab.binding.supla.internal.cloud.ChannelFunctionDispatcher.DISPATCHER;
import static pl.grzeslowski.jsupla.api.generated.model.ChannelFunctionActionEnum.CLOSE;
import static pl.grzeslowski.jsupla.api.generated.model.ChannelFunctionActionEnum.OPEN;
import static pl.grzeslowski.jsupla.api.generated.model.ChannelFunctionActionEnum.OPEN_CLOSE;
import static pl.grzeslowski.jsupla.api.generated.model.ChannelFunctionActionEnum.REVEAL;
import static pl.grzeslowski.jsupla.api.generated.model.ChannelFunctionActionEnum.REVEAL_PARTIALLY;
import static pl.grzeslowski.jsupla.api.generated.model.ChannelFunctionActionEnum.SHUT;
import static pl.grzeslowski.jsupla.api.generated.model.ChannelFunctionActionEnum.TURN_OFF;
import static pl.grzeslowski.jsupla.api.generated.model.ChannelFunctionActionEnum.TURN_ON;

/**
 * This is handler for all Supla devices.
 * <p>
 * Channels are created at runtime after connecting to Supla Cloud
 *
 * @author Martin Grześlowski - initial contributor
 */
@SuppressWarnings("PackageAccessibility")
public final class CloudDeviceHandler extends AbstractDeviceHandler {
    private final Logger logger = LoggerFactory.getLogger(CloudBridgeHandler.class);
    private final ChannelsCloudApiFactory channelsCloudApiFactory;
    private final IoDevicesCloudApiFactory ioDevicesCloudApiFactory;
    private final LedCommandExecutorFactory ledCommandExecutorFactory;

    private ChannelsCloudApi channelsApi;
    private int cloudId;
    private IoDevicesCloudApi ioDevicesApi;

    // CommandExecutors
    private LedCommandExecutor ledCommandExecutor;

    CloudDeviceHandler(
            final Thing thing,
            final ChannelsCloudApiFactory channelsCloudApiFactory,
            final IoDevicesCloudApiFactory ioDevicesCloudApiFactory,
            final LedCommandExecutorFactory ledCommandExecutorFactory) {
        super(thing);
        this.channelsCloudApiFactory = channelsCloudApiFactory;
        this.ioDevicesCloudApiFactory = ioDevicesCloudApiFactory;
        this.ledCommandExecutorFactory = ledCommandExecutorFactory;
    }

    public CloudDeviceHandler(final Thing thing) {
        this(
                thing,
                ChannelsCloudApiFactory.getFactory(),
                IoDevicesCloudApiFactory.getFactory(),
                SuplaLedCommandExecutorFactory.FACTORY);
    }

    @Override
    protected void internalInitialize() throws ApiException {
        @Nullable final Bridge bridge = getBridge();
        if (bridge == null) {
            logger.debug("No bridge for thing with UID {}", thing.getUID());
            updateStatus(OFFLINE, BRIDGE_UNINITIALIZED,
                    "There is no bridge for this thing. Remove it and add it again.");
            return;
        }
        final @Nullable BridgeHandler bridgeHandler = bridge.getHandler();
        if (!(bridgeHandler instanceof CloudBridgeHandler)) {
            logger.debug("Bridge is not instance of {}! Current bridge class {}, Thing UID {}",
                    CloudBridgeHandler.class.getSimpleName(), bridgeHandler.getClass().getSimpleName(), thing.getUID());
            updateStatus(OFFLINE, BRIDGE_UNINITIALIZED, "There is wrong type of bridge for cloud device!");
            return;
        }
        CloudBridgeHandler handler = (CloudBridgeHandler) bridgeHandler;
        final Optional<String> token = handler.getOAuthToken();
        if (!token.isPresent()) {
            updateStatus(OFFLINE, CONFIGURATION_ERROR, "There is no OAuth token in bridge!");
            return;
        }
        initApi(token.get());

        if (!initCloudApi()) {
            return;
        }

        if (!checkIfIsOnline()) {
            return;
        }

        if (!checkIfIsEnabled()) {
            return;
        }

        initChannels();
        initCommandExecutors();

        // done
        updateStatus(ONLINE);
    }

    private boolean initCloudApi() {
        final String cloudIdString = valueOf(getConfig().get(SUPLA_DEVICE_CLOUD_ID));
        try {
            this.cloudId = parseInt(cloudIdString);
            return true;
        } catch (NumberFormatException e) {
            logger.error("Cannot parse cloud ID `{}` to integer!", cloudIdString, e);
            updateStatus(OFFLINE, CONFIGURATION_ERROR, "Cloud ID is incorrect!");
            return false;
        }
    }

    private void initApi(final String token) {
        ioDevicesApi = ioDevicesCloudApiFactory.newIoDevicesCloudApi(token);
        channelsApi = channelsCloudApiFactory.newChannelsCloudApi(token);
    }

    private boolean checkIfIsOnline() throws ApiException {
        final Device device = ioDevicesApi.getIoDevice(cloudId, singletonList("connected"));
        if (device.isConnected() == null || !device.isConnected()) {
            updateStatus(OFFLINE, NONE, "This device is is not connected to Supla Cloud.");
            return false;
        } else {
            return true;
        }
    }

    private boolean checkIfIsEnabled() throws ApiException {
        final Device device = ioDevicesApi.getIoDevice(cloudId, emptyList());
        if (device.isEnabled() == null || !device.isEnabled()) {
            updateStatus(OFFLINE, NONE, "This device is turned off in Supla Cloud.");
            return false;
        } else {
            return true;
        }
    }

    private void initChannels() {
        try {
            final List<Channel> channels = ioDevicesApi.getIoDevice(cloudId, singletonList("channels"))
                                                   .getChannels()
                                                   .stream()
                                                   .filter(channel -> !channel.isHidden())
                                                   .map(channel -> DISPATCHER.dispatch(channel, new CreateChannelFunctionSwitch(thing.getUID())))
                                                   .flatMap(List::stream)
                                                   .collect(Collectors.toList());
            updateChannels(channels);
        } catch (ApiException e) {
            logger.error("Error when loading IO device from Supla Cloud!", e);
            updateStatus(OFFLINE, COMMUNICATION_ERROR, "Error when loading IO device from Supla Cloud!");
        }
    }

    private void initCommandExecutors() {
        ledCommandExecutor = ledCommandExecutorFactory.newLedCommandExecutor(channelsApi);
    }

    private void updateChannels(final List<Channel> channels) {
        ThingBuilder thingBuilder = editThing();
        thingBuilder.withChannels(channels);
        updateThing(thingBuilder.build());
    }

    @Override
    protected void handleRefreshCommand(final ChannelUID channelUID) throws Exception {
        final ChannelInfo channelInfo = ChannelInfoParser.PARSER.parse(channelUID);
        final int channelId = channelInfo.getChannelId();
        logger.trace("Refreshing channel `{}`", channelUID);
        final pl.grzeslowski.jsupla.api.generated.model.Channel channel = queryForChannel(channelId);
        final FindStateFunctionSwitch findStateFunctionSwitch = new FindStateFunctionSwitch(ledCommandExecutor, channelUID);
        Optional<? extends State> foundState = ChannelFunctionDispatcher.DISPATCHER.dispatch(channel, findStateFunctionSwitch);
        if (foundState.isPresent()) {
            final State state = foundState.get();
            logger.trace("Updating state `{}` to `{}`", channelUID, state);
            updateState(channelUID, state);
        } else {
            logger.warn("There was no found state for channel `{}` channelState={}, function={}",
                    channelUID, channel.getState(), channel.getFunction());
        }
    }

    @Override
    protected void handleOnOffCommand(final ChannelUID channelUID, final OnOffType command) throws Exception {
        final ChannelInfo channelInfo = ChannelInfoParser.PARSER.parse(channelUID);
        final int channelId = channelInfo.getChannelId();
        final pl.grzeslowski.jsupla.api.generated.model.Channel channel = queryForChannel(channelId);
        switch (channel.getFunction().getName()) {
            case CONTROLLINGTHEGATE:
            case CONTROLLINGTHEGARAGEDOOR:
                final ChannelExecuteActionRequest action = new ChannelExecuteActionRequest().action(OPEN_CLOSE);
                channelsApi.executeAction(action, channelId);
                break;
            default:
                handleOneZeroCommand(channelId, command == ON, TURN_ON, TURN_OFF);
        }
    }

    @Override
    protected void handleUpDownCommand(final ChannelUID channelUID, final UpDownType command) throws Exception {
        final ChannelInfo channelInfo = ChannelInfoParser.PARSER.parse(channelUID);
        final int channelId = channelInfo.getChannelId();
        final pl.grzeslowski.jsupla.api.generated.model.Channel channel = queryForChannel(channelId);
        switch (channel.getFunction().getName()) {
            case CONTROLLINGTHEROLLERSHUTTER:
                handleOneZeroCommand(channelId, command == UP, REVEAL, SHUT);
                final int value = command == UP ? 100 : 0;
                updateState(channelUID, new PercentType(value));
                break;
        }
    }

    @Override
    protected void handleHsbCommand(final ChannelUID channelUID, final HSBType command) throws ApiException {
        final ChannelInfo channelInfo = ChannelInfoParser.PARSER.parse(channelUID);
        final int channelId = channelInfo.getChannelId();
        final pl.grzeslowski.jsupla.api.generated.model.Channel channel = queryForChannel(channelId);
        handleHsbCommand(channel, channelUID, command);
    }

    private void handleHsbCommand(final pl.grzeslowski.jsupla.api.generated.model.Channel channel,
                                  final ChannelUID channelUID,
                                  final HSBType command) throws ApiException {
        switch (channel.getFunction().getName()) {
            case RGBLIGHTING:
            case DIMMERANDRGBLIGHTING:
                ledCommandExecutor.changeColor(channel.getId(), command);
                return;
            default:
                logger.warn("Not handling `{}` ({}) on channel `{}`", command, command.getClass().getSimpleName(), channelUID);
        }
    }

    @Override
    protected void handleOpenClosedCommand(final ChannelUID channelUID, final OpenClosedType command) throws ApiException {
        final ChannelInfo channelInfo = ChannelInfoParser.PARSER.parse(channelUID);
        final int channelId = channelInfo.getChannelId();
        final pl.grzeslowski.jsupla.api.generated.model.Channel channel = queryForChannel(channelId);
        switch (channel.getFunction().getName()) {
            case CONTROLLINGTHEGATE:
            case CONTROLLINGTHEGARAGEDOOR:
                handleOneZeroCommand(channelId, command == OpenClosedType.OPEN, OPEN, CLOSE);
        }
    }

    @Override
    protected void handlePercentCommand(final ChannelUID channelUID, final PercentType command) throws ApiException {
        final ChannelInfo channelInfo = ChannelInfoParser.PARSER.parse(channelUID);
        final int channelId = channelInfo.getChannelId();
        final pl.grzeslowski.jsupla.api.generated.model.Channel channel = queryForChannel(channelId);
        switch (channel.getFunction().getName()) {
            case CONTROLLINGTHEROLLERSHUTTER:
                final int shut = 100 - command.intValue();
                logger.debug("Channel `{}` is roller shutter; setting shut={}%", channelUID, shut);
                final ChannelExecuteActionRequest action = new ChannelExecuteActionRequest().action(REVEAL_PARTIALLY).percentage(shut);
                channelsApi.executeAction(action, channelId);
                return;
            case RGBLIGHTING:
            case DIMMERANDRGBLIGHTING:
                if (channelInfo.getAdditionalChannelType() == null) {
                    ledCommandExecutor.changeColorBrightness(channelId, command);
                } else if (channelInfo.getAdditionalChannelType() == LED_BRIGHTNESS) {
                    ledCommandExecutor.changeBrightness(channelId, command);
                }
                return;
            case DIMMER:
                ledCommandExecutor.changeBrightness(channelId, command);
                break;
            default:
                logger.warn("Not handling `{}` ({}) on channel `{}`", command, command.getClass().getSimpleName(), channelUID);
        }
    }

    @Override
    protected void handleDecimalCommand(final ChannelUID channelUID, final DecimalType command) {
// TODO handle this command
        logger.warn("Not handling `{}` ({}) on channel `{}`", command, command.getClass().getSimpleName(), channelUID);
    }

    private void handleOneZeroCommand(final int channelId,
                                      final boolean firstOrSecond,
                                      final ChannelFunctionActionEnum first,
                                      final ChannelFunctionActionEnum second) throws ApiException {
        final ChannelFunctionActionEnum action = firstOrSecond ? first : second;
        logger.trace("Executing 0/1 command `{}`", action);
        channelsApi.executeAction(new ChannelExecuteActionRequest().action(action), channelId);
    }

    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    @Override
    protected void handleStopMoveTypeCommand(final @NonNull ChannelUID channelUID, final @NonNull StopMoveType command) throws ApiException {
        final ChannelInfo channelInfo = ChannelInfoParser.PARSER.parse(channelUID);
        final int channelId = channelInfo.getChannelId();
        final pl.grzeslowski.jsupla.api.generated.model.Channel channel = queryForChannel(channelId);
        switch (channel.getFunction().getName()) {
            case CONTROLLINGTHEROLLERSHUTTER:
                handleStopMoveTypeCommandOnRollerShutter(channelUID, channel, command);
                return;
            default:
                logger.warn("Not handling `{}` ({}) on channel `{}`", command, command.getClass().getSimpleName(), channelUID);
        }
    }

    private void handleStopMoveTypeCommandOnRollerShutter(
            final ChannelUID channelUID,
            final pl.grzeslowski.jsupla.api.generated.model.Channel channel,
            final StopMoveType command) throws ApiException {
        switch (command) {
            case MOVE:
                logger.trace("Do not know how to handle command `{}` on roller shutter with id `{}`", command, channelUID);
                return;
            case STOP:
                final ChannelFunctionActionEnum action = ChannelFunctionActionEnum.STOP;
                logger.trace("Sending stop action `{}` to channel with UUID `{}`", action, channelUID);
                channelsApi.executeAction(new ChannelExecuteActionRequest().action(action), channel.getId());
        }
    }

    @Override
    protected void handleStringCommand(final ChannelUID channelUID, final StringType command) throws ApiException {
        logger.warn("Not handling `{}` ({}) on channel `{}`", command, command.getClass().getSimpleName(), channelUID);
    }

    private void changeColorOfRgb(final pl.grzeslowski.jsupla.api.generated.model.Channel channel,
                                  final HSBType hsbType,
                                  final ChannelUID rgbChannelUid) throws ApiException {
        logger.trace("Setting color to `{}` for channel `{}`", hsbType, rgbChannelUid);
        handleHsbCommand(channel, rgbChannelUid, hsbType);
        updateState(rgbChannelUid, hsbType);
    }

    void refresh() {
        logger.debug("Refreshing `{}`", thing.getUID());
        try {
            if (checkIfIsOnline() && checkIfIsEnabled()) {
                updateStatus(ONLINE);
                logger.trace("Thing `{}` is connected & enabled. Refreshing channels", thing.getUID());
                thing.getChannels()
                        .stream()
                        .map(Channel::getUID)
                        .forEach(channelUID -> handleCommand(channelUID, REFRESH));
            }
        } catch (ApiException e) {
            logger.error("Cannot check if device `{}` is online/enabled", thing.getUID(), e);
        }
    }

    private pl.grzeslowski.jsupla.api.generated.model.Channel queryForChannel(final int channelId) throws ApiException {
        return channelsApi.getChannel(channelId, asList("supportedFunctions", "state"));
    }

}
