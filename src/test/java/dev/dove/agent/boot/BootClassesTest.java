package dev.dove.agent.boot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BootClassesTest {

    @TempDir
    Path dir;

    @Test
    void writesTheHooksIntoAJarNamedAfterThem() throws Exception {
        Path jar = BootClasses.write(dir);

        try (JarFile file = new JarFile(jar.toFile())) {
            assertNotNull(file.getEntry(DataFixerHooks.OWNER + ".class"));
            assertEquals(BootClasses.CLASSES.size(), file.size());
        }
        // A second launch reuses the same jar instead of piling up copies.
        assertEquals(jar, BootClasses.write(dir));
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void theHooksNeedNothingButTheJdk() throws Exception {
        // Everything DataFixerHooks refers to must come from java.*: on the bootstrap class path it
        // cannot see the rest of the agent.
        try (var in = DataFixerHooks.class.getResourceAsStream("DataFixerHooks.class")) {
            var reader = new org.objectweb.asm.ClassReader(in.readAllBytes());
            char[] buffer = new char[reader.getMaxStringLength()];
            for (int i = 1; i < reader.getItemCount(); i++) {
                int offset = reader.getItem(i);
                if (offset > 0 && reader.readByte(offset - 1) == 7) { // CONSTANT_Class
                    String name = reader.readUTF8(offset, buffer);
                    if (!name.startsWith("java/") && !name.startsWith("[")) {
                        assertEquals(DataFixerHooks.OWNER, name, "DataFixerHooks refers to " + name);
                    }
                }
            }
        }
    }
}
