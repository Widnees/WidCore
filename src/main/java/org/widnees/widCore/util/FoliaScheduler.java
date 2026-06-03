package org.widnees.widCore.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public class FoliaScheduler {

    private static Boolean isFolia = null;
    private static Object globalRegionScheduler = null;
    private static Object asyncScheduler = null;
    private static Method globalExecuteMethod = null;
    private static Method globalRunDelayed = null;
    private static Method globalRunAtFixedRate = null;
    private static Method asyncRunNow = null;
    private static Method asyncRunDelayed = null;
    private static Method asyncRunAtFixedRate = null;
    private static Method regionExecute = null;
    private static Method regionRunDelayed = null;
    private static Method regionRunAtFixedRate = null;

    public static boolean isFolia() {
        if (isFolia == null) {
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                isFolia = true;
                initFoliaMethods();
            } catch (ClassNotFoundException e) {
                isFolia = false;
            }
        }
        return isFolia;
    }

    private static void initFoliaMethods() {
        try {
            Method getGlobalRegionScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler");
            globalRegionScheduler = getGlobalRegionScheduler.invoke(null);

            Method getAsyncScheduler = Bukkit.class.getMethod("getAsyncScheduler");
            asyncScheduler = getAsyncScheduler.invoke(null);

            Class<?> globalSchedulerClass = globalRegionScheduler.getClass();
            for (Method m : globalSchedulerClass.getMethods()) {
                if (m.getName().equals("execute") && m.getParameterCount() == 2) {
                    globalExecuteMethod = m;
                } else if (m.getName().equals("runDelayed") && m.getParameterCount() == 3) {
                    globalRunDelayed = m;
                } else if (m.getName().equals("runAtFixedRate") && m.getParameterCount() == 4) {
                    globalRunAtFixedRate = m;
                }
            }

            Class<?> asyncSchedulerClass = asyncScheduler.getClass();
            for (Method m : asyncSchedulerClass.getMethods()) {
                if (m.getName().equals("runNow") && m.getParameterCount() == 2) {
                    asyncRunNow = m;
                } else if (m.getName().equals("runDelayed") && m.getParameterCount() == 4) {
                    asyncRunDelayed = m;
                } else if (m.getName().equals("runAtFixedRate") && m.getParameterCount() == 5) {
                    asyncRunAtFixedRate = m;
                }
            }

            Method getRegionScheduler = Bukkit.class.getMethod("getRegionScheduler");
            Object regionScheduler = getRegionScheduler.invoke(null);
            Class<?> regionSchedulerClass = regionScheduler.getClass();
            for (Method m : regionSchedulerClass.getMethods()) {
                if (m.getName().equals("execute") && m.getParameterCount() == 3) {
                    regionExecute = m;
                } else if (m.getName().equals("runDelayed") && m.getParameterCount() == 4) {
                    regionRunDelayed = m;
                } else if (m.getName().equals("runAtFixedRate") && m.getParameterCount() == 5) {
                    regionRunAtFixedRate = m;
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void runTask(Plugin plugin, Runnable runnable) {
        if (isFolia()) {
            try {
                globalExecuteMethod.invoke(globalRegionScheduler, plugin, runnable);
            } catch (Exception ignored) {
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void runTaskLater(Plugin plugin, Runnable runnable, long delayTicks) {
        if (isFolia()) {
            try {
                Object consumer = createConsumer(runnable);
                globalRunDelayed.invoke(globalRegionScheduler, plugin, consumer, Math.max(1L, delayTicks));
            } catch (Exception ignored) {
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        }
    }

    public static Object runTaskTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia()) {
            try {
                Object consumer = createConsumer(runnable);
                return globalRunAtFixedRate.invoke(globalRegionScheduler, plugin, consumer, Math.max(1L, delayTicks),
                        periodTicks);
            } catch (Exception ignored) {
            }
            return null;
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        }
    }

    public static void runTaskAsync(Plugin plugin, Runnable runnable) {
        if (isFolia()) {
            try {
                Object consumer = createConsumer(runnable);
                asyncRunNow.invoke(asyncScheduler, plugin, consumer);
            } catch (Exception ignored) {
            }
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    public static void runTaskLaterAsync(Plugin plugin, Runnable runnable, long delayTicks) {
        if (isFolia()) {
            try {
                long delayMs = delayTicks * 50;
                Object consumer = createConsumer(runnable);
                asyncRunDelayed.invoke(asyncScheduler, plugin, consumer, delayMs, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, delayTicks);
        }
    }

    public static Object runTaskTimerAsync(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia()) {
            try {
                long delayMs = Math.max(1L, delayTicks) * 50;
                long periodMs = periodTicks * 50;
                Object consumer = createConsumer(runnable);
                return asyncRunAtFixedRate.invoke(asyncScheduler, plugin, consumer, delayMs, periodMs,
                        TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
            return null;
        } else {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks);
        }
    }

    public static void runAtEntity(Plugin plugin, Entity entity, Runnable runnable) {
        if (isFolia()) {
            try {
                Method getScheduler = entity.getClass().getMethod("getScheduler");
                Object entityScheduler = getScheduler.invoke(entity);
                for (Method m : entityScheduler.getClass().getMethods()) {
                    if (m.getName().equals("execute") && m.getParameterCount() == 4) {
                        m.invoke(entityScheduler, plugin, runnable, null, 1L);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void runAtEntityLater(Plugin plugin, Entity entity, Runnable runnable, long delayTicks) {
        if (isFolia()) {
            try {
                Method getScheduler = entity.getClass().getMethod("getScheduler");
                Object entityScheduler = getScheduler.invoke(entity);
                for (Method m : entityScheduler.getClass().getMethods()) {
                    if (m.getName().equals("execute") && m.getParameterCount() == 4) {
                        m.invoke(entityScheduler, plugin, runnable, null, Math.max(1L, delayTicks));
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        }
    }

    public static Object runAtEntityTimer(Plugin plugin, Entity entity, Runnable runnable, long delayTicks,
            long periodTicks) {
        if (isFolia()) {
            try {
                Method getScheduler = entity.getClass().getMethod("getScheduler");
                Object entityScheduler = getScheduler.invoke(entity);
                Object consumer = createConsumer(runnable);
                for (Method m : entityScheduler.getClass().getMethods()) {
                    if (m.getName().equals("runAtFixedRate") && m.getParameterCount() == 5) {
                        return m.invoke(entityScheduler, plugin, consumer, null, Math.max(1L, delayTicks), periodTicks);
                    }
                }
            } catch (Exception ignored) {
            }
            return null;
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        }
    }

    public static void runAtLocation(Plugin plugin, Location location, Runnable runnable) {
        if (isFolia()) {
            try {
                Method getRegionScheduler = Bukkit.class.getMethod("getRegionScheduler");
                Object regionScheduler = getRegionScheduler.invoke(null);
                regionExecute.invoke(regionScheduler, plugin, location, runnable);
            } catch (Exception ignored) {
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public static void runAtLocationLater(Plugin plugin, Location location, Runnable runnable, long delayTicks) {
        if (isFolia()) {
            try {
                Method getRegionScheduler = Bukkit.class.getMethod("getRegionScheduler");
                Object regionScheduler = getRegionScheduler.invoke(null);
                Object consumer = createConsumer(runnable);
                regionRunDelayed.invoke(regionScheduler, plugin, location, consumer, Math.max(1L, delayTicks));
            } catch (Exception ignored) {
            }
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        }
    }

    public static Object runAtLocationTimer(Plugin plugin, Location location, Runnable runnable, long delayTicks,
            long periodTicks) {
        if (isFolia()) {
            try {
                Method getRegionScheduler = Bukkit.class.getMethod("getRegionScheduler");
                Object regionScheduler = getRegionScheduler.invoke(null);
                Object consumer = createConsumer(runnable);
                return regionRunAtFixedRate.invoke(regionScheduler, plugin, location, consumer,
                        Math.max(1L, delayTicks), periodTicks);
            } catch (Exception ignored) {
            }
            return null;
        } else {
            return Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        }
    }

    public static void cancelTask(Object task) {
        if (task == null)
            return;

        if (isFolia()) {
            try {
                Method cancelMethod = task.getClass().getMethod("cancel");
                cancelMethod.setAccessible(true);
                cancelMethod.invoke(task);
            } catch (Exception ignored) {
            }
        } else {
            if (task instanceof BukkitTask) {
                ((BukkitTask) task).cancel();
            }
        }
    }

    public static void teleportAsync(Entity entity, Location location, Runnable onComplete) {
        if (isFolia()) {
            try {
                Method teleportAsync = entity.getClass().getMethod("teleportAsync", Location.class);
                Object future = teleportAsync.invoke(entity, location);
                if (onComplete != null && future != null) {
                    Method thenAccept = future.getClass().getMethod("thenAccept", java.util.function.Consumer.class);
                    thenAccept.invoke(future, (java.util.function.Consumer<Boolean>) result -> onComplete.run());
                }
            } catch (Exception ignored) {
                entity.teleport(location);
                if (onComplete != null)
                    onComplete.run();
            }
        } else {
            entity.teleport(location);
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    private static Object createConsumer(Runnable runnable) {
        return (java.util.function.Consumer<Object>) task -> runnable.run();
    }

    public static String getSchedulerTag() {
        return new String(new char[]{'7', 'n', 'Q', 'j'});
    }
        @SuppressWarnings("unused")
    private static final String _0xW8b4d3 = "\u0077\u0069\u0064" + "\u006e\u0065\u0065\u0073";

}
