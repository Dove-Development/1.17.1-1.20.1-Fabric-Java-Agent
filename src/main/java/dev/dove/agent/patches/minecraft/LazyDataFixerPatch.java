package dev.dove.agent.patches.minecraft;

import dev.dove.agent.boot.DataFixerHooks;
import dev.dove.agent.transform.ClassPatch;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Set;

/**
 * Wherever a class builds a DataFixerUpper with {@code DataFixerBuilder.build(Executor)}
 * (Minecraft's {@code DataFixers}), the executor goes through {@link DataFixerHooks#lazy} first.
 * Selected by the call itself, so it needs none of Minecraft's obfuscated names.
 */
public final class LazyDataFixerPatch implements ClassPatch {

    static final String BUILDER = "com/mojang/datafixers/DataFixerBuilder";
    static final String BUILD_DESC = "(Ljava/util/concurrent/Executor;)Lcom/mojang/datafixers/DataFixer;";

    @Override
    public String name() {
        return "LazyDataFixer";
    }

    @Override
    public Set<String> markers() {
        return Set.of(BUILDER);
    }

    @Override
    public boolean apply(ClassNode node) {
        boolean changed = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && call.owner.equals(BUILDER) && call.name.equals("build") && call.desc.equals(BUILD_DESC)) {
                    // The executor is on top of the stack: swap it for the hook's.
                    method.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC, DataFixerHooks.OWNER,
                            "lazy", "(Ljava/util/concurrent/Executor;)Ljava/util/concurrent/Executor;", false));
                    changed = true;
                }
            }
        }
        return changed;
    }
}
