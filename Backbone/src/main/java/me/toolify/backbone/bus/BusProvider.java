/*
 * Copyright (C) 2012 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.toolify.backbone.bus;

import com.squareup.otto.Bus;

import me.toolify.backbone.bus.events.BusEvent;

/**
 * Maintains a singleton instance for obtaining the bus. Ideally this would be replaced with a more efficient means
 * such as through injection directly into interested classes.
 */
public final class BusProvider {
  private static final Bus BUS = new Bus();

  public static Bus getInstance() {
    return BUS;
  }

    /**
     * Central calling point for Bus.post() calls
     * @param event BusEvent class to post to static Bus object
     */
    public static void postEvent(BusEvent event)
    {
        BUS.post(event);
    }

    /**
     * Central calling point for Bus.register() calls
     * @param object
     */
    public static void register(Object object) {
        BUS.register(object);
    }

    /**
     * Central calling point for Bus.unregister() calls
     * @param object
     */
    public static void unregister(Object object) {
        BUS.unregister(object);
    }

  private BusProvider() {
    // No instances.
  }
}
