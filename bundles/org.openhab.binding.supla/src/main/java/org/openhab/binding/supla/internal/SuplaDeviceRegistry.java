/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.supla.internal;

import org.openhab.binding.supla.handler.SuplaDeviceHandler;

import java.util.Optional;

/**
 * @author Grzeslowski - Initial contribution
 */
public interface SuplaDeviceRegistry {
    void addSuplaDevice(SuplaDeviceHandler suplaDeviceHandler);

    Optional<SuplaDeviceHandler> getSuplaDevice(String guid);
}


