package dev.dove.testjoin;

import net.fabricmc.api.ClientModInitializer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * Test helper for mcrun.py, never shipped: opens the saved world named by -Ddove.joinWorld once the
 * title screen has settled, as Quick Play does from 1.20 on. Minecraft 1.14 to 1.19 have no
 * command-line option for it. Uses intermediary names, which stay the same across these versions.
 */
public final class JoinWorld implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        String world = System.getProperty("dove.joinWorld");
        if (world == null) {
            return;
        }
        Thread thread = new Thread(() -> join(world), "Dove test join");
        thread.setDaemon(true);
        thread.start();
    }

    private static void join(String world) {
        try {
            Class<?> minecraft = Class.forName("net.minecraft.class_310");
            Object mc = minecraft.getMethod("method_1551").invoke(null);
            Field screen = minecraft.getDeclaredField("field_1755");
            Field overlay = minecraft.getDeclaredField("field_18175");
            screen.setAccessible(true);
            overlay.setAccessible(true);
            // The title screen, with the loading overlay gone, for 3 s in a row.
            int settled = 0;
            while (settled < 30) {
                Thread.sleep(100);
                settled = screen.get(mc) != null && overlay.get(mc) == null ? settled + 1 : 0;
            }
            System.out.println("[DoveTestJoin] opening world " + world);
            ((Executor) mc).execute(() -> {
                try {
                    try {
                        // 1.19: createWorldOpenFlows().loadLevel(currentScreen, String)
                        Object flows = minecraft.getMethod("method_41735").invoke(mc);
                        for (Method m : flows.getClass().getMethods()) {
                            if (m.getName().equals("method_41894")) {
                                m.invoke(flows, screen.get(mc), world);
                            }
                        }
                    } catch (NoSuchMethodException newer) {
                    try {
                        // 1.16 to 1.18: loadLevel(String)
                        minecraft.getMethod("method_29606", String.class).invoke(mc, world);
                    } catch (NoSuchMethodException e) {
                        // 1.14, 1.15: selectLevel(String folder, String name, LevelSettings) - null for a saved world
                        Method select = null;
                        for (Method m : minecraft.getMethods()) {
                            if (m.getName().equals("method_1559")) {
                                select = m;
                            }
                        }
                        select.invoke(mc, world, world, null);
                    }
                    }
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            System.out.println("[DoveTestJoin] failed: " + e);
            e.printStackTrace(System.out);
        }
    }
}
