package dev.dove.agent.boot;

import dev.dove.agent.util.AgentLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

/**
 * Puts the hooks that Minecraft's own classes call on the bootstrap class path.
 *
 * <p>Fabric's Knot class loader, which loads Minecraft and mods, hides the application class path
 * (where the agent jar is) from them: only classes the JDK's own loaders can also see get through.
 * So the hooks are copied into a small jar of their own and appended to the bootstrap search, the
 * way Java agents are meant to share classes with every class loader. They use nothing but the JDK,
 * and the agent jar carries them under {@code dove-boot/} rather than as classes (see build.gradle.kts).
 */
public final class BootClasses {

    /** Every class that goes on the bootstrap class path, as internal names. */
    static final List<String> CLASSES = List.of(DataFixerHooks.OWNER);

    private BootClasses() {
    }

    /** Writes the boot jar into {@code dir} and appends it to the bootstrap search; false if that failed. */
    public static boolean install(Instrumentation inst, Path dir) {
        try {
            Path jar = write(dir);
            inst.appendToBootstrapClassLoaderSearch(new JarFile(jar.toFile()));
            return true;
        } catch (IOException | RuntimeException e) {
            AgentLog.warn("Cannot put the game hooks on the bootstrap class path; leaving those changes out: " + e);
            return false;
        }
    }

    /**
     * The boot jar, named after its contents: a running game keeps it open (Windows can't delete it
     * then), so each agent version writes it once and every launch after that reuses it. Written
     * to a temporary file and renamed into place, so a half-written jar is never used.
     */
    static Path write(Path dir) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream zip = new JarOutputStream(bytes)) {
            for (String name : CLASSES) {
                try (InputStream in = classFile(name)) {
                    if (in == null) {
                        throw new IOException("missing " + name);
                    }
                    ZipEntry entry = new ZipEntry(name + ".class");
                    entry.setTime(0); // the same bytes every time, so the same name
                    zip.putNextEntry(entry);
                    in.transferTo(zip);
                }
            }
        }
        byte[] content = bytes.toByteArray();
        Files.createDirectories(dir);
        Path jar = dir.resolve("dove-agent-boot-" + sha256(content).substring(0, 16) + ".jar");
        if (!Files.exists(jar) || !java.util.Arrays.equals(Files.readAllBytes(jar), content)) {
            Path temp = Files.createTempFile(dir, "dove-agent-boot", ".tmp");
            Files.write(temp, content);
            Files.move(temp, jar, StandardCopyOption.REPLACE_EXISTING);
        }
        return jar;
    }

    /** In the agent jar under dove-boot/; on the class path itself when testing. */
    private static InputStream classFile(String name) {
        InputStream in = BootClasses.class.getResourceAsStream("/dove-boot/" + name + ".class");
        return in != null ? in : BootClasses.class.getResourceAsStream("/" + name + ".class");
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
