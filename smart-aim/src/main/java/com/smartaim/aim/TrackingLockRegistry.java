package com.smartaim.aim;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread-only registry; keys and values contain no live Minecraft entity objects. */
final class TrackingLockRegistry {
   private final Map<UUID, TrackingLock> locks = new HashMap<>();

   TrackingLock get(UUID projectileId) { return this.locks.get(projectileId); }
   void put(UUID projectileId, TrackingLock lock) { this.locks.put(projectileId, lock); }
   void clear(UUID projectileId) { this.locks.remove(projectileId); }
   int size() { return this.locks.size(); }
}
