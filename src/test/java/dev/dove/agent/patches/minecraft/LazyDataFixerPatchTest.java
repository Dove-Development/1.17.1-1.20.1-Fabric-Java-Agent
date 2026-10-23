package dev.dove.agent.patches.minecraft;

import com.mojang.datafixers.DataFixerBuilder;
import dev.dove.agent.boot.DataFixerHooks;
import dev.dove.agent.transform.DoveTransformer;
import dev.dove.agent.transform.PatchRegistry;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LazyDataFixerPatchTest {

    private static final String NAME = "net/minecraft/class_3551";
    private static final AtomicInteger RAN = new AtomicInteger();

    /** Minecraft's executor in the test: counts the tasks it is given. */
    public static final Executor GAME_EXECUTOR = task -> RAN.incrementAndGet();

    @Test
    void buildsWithoutWarmingUpTheCache() throws Exception {
        PatchRegistry registry = new PatchRegistry();
        registry.register(new LazyDataFixerPatch());
        byte[] original = dataFixers();
        byte[] patched = new DoveTransformer(registry).transform(getClass().getClassLoader(), NAME, null, null, original);
        assertNotNull(patched, "a class that builds a DataFixerUpper is patched");

        int before = DataFixerHooks.skipped();
        assertNotNull(load(patched).get());

        assertTrue(DataFixerBuilder.lastExecutor != GAME_EXECUTOR, "the game's executor was swapped");
        assertEquals(0, RAN.get(), "no warm-up task reached the game's executor");
        assertEquals(before + 1, DataFixerHooks.skipped());
    }

    @Test
    void leavesOtherClassesAlone() {
        PatchRegistry registry = new PatchRegistry();
        registry.register(new LazyDataFixerPatch());
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "net/minecraft/class_1", null, "java/lang/Object", null);
        writer.visitEnd();

        assertNull(new DoveTransformer(registry).transform(getClass().getClassLoader(), "net/minecraft/class_1", null, null, writer.toByteArray()));
    }

    /** Like Minecraft's DataFixers: {@code get()} returns {@code new DataFixerBuilder().build(GAME_EXECUTOR)}. */
    private static byte[] dataFixers() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, NAME, null, "java/lang/Object", new String[]{"java/util/function/Supplier"});
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
        MethodVisitor get = writer.visitMethod(Opcodes.ACC_PUBLIC, "get", "()Ljava/lang/Object;", null, null);
        get.visitCode();
        get.visitTypeInsn(Opcodes.NEW, LazyDataFixerPatch.BUILDER);
        get.visitInsn(Opcodes.DUP);
        get.visitMethodInsn(Opcodes.INVOKESPECIAL, LazyDataFixerPatch.BUILDER, "<init>", "()V", false);
        get.visitFieldInsn(Opcodes.GETSTATIC, "dev/dove/agent/patches/minecraft/LazyDataFixerPatchTest", "GAME_EXECUTOR", "Ljava/util/concurrent/Executor;");
        get.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LazyDataFixerPatch.BUILDER, "build", LazyDataFixerPatch.BUILD_DESC, false);
        get.visitInsn(Opcodes.ARETURN);
        get.visitMaxs(0, 0);
        get.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Supplier<Object> load(byte[] bytes) throws ReflectiveOperationException {
        return (Supplier<Object>) new Definer().define(bytes).getDeclaredConstructor().newInstance();
    }

    private static final class Definer extends ClassLoader {
        Definer() {
            super(LazyDataFixerPatchTest.class.getClassLoader());
        }

        Class<?> define(byte[] bytes) {
            return defineClass(NAME.replace('/', '.'), bytes, 0, bytes.length);
        }
    }
}
