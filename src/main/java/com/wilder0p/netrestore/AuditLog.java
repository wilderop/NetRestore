package com.wilder0p.netrestore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

final class AuditLog {

    private final NetRestore plugin;
    private final Path file;

    AuditLog(NetRestore plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("audit.log");
    }

    void log(String line) {
        String entry = Instant.now() + " " + line;
        plugin.getLogger().info(entry);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogger().warning("audit write failed: " + ex.getMessage());
        }
    }
}
