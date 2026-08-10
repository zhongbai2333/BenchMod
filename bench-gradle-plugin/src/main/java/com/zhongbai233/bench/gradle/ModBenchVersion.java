package com.zhongbai233.bench.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Reads the plugin's own version from the resource expanded at build time. */
final class ModBenchVersion {
    private ModBenchVersion() {}

    /** Returns the plugin version, or an empty string on a raw-classes classpath without the resource. */
    static String read() {
        try (InputStream stream = ModBenchVersion.class.getResourceAsStream("modbench.properties")) {
            if (stream == null) return "";
            Properties properties = new Properties();
            properties.load(stream);
            String version = properties.getProperty("version", "");
            // An unexpanded template or an unset project version must disable automatic
            // dependencies instead of producing unresolvable coordinates.
            return version.contains("${") || version.equals("unspecified") ? "" : version;
        } catch (IOException exception) {
            return "";
        }
    }
}
