package com.zhongbai233.bench.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Reads the plugin's own Maven coordinates from the resource expanded at build time. */
final class ModBenchVersion {
    private ModBenchVersion() {}

    /** Returns the plugin version, or an empty string on a raw-classes classpath without the resource. */
    static String read() {
        return readProperty("version");
    }

    /** Returns the plugin group, including the JitPack repository suffix when built by JitPack. */
    static String readGroup() {
        return readProperty("group");
    }

    private static String readProperty(String name) {
        try (InputStream stream = ModBenchVersion.class.getResourceAsStream("modbench.properties")) {
            if (stream == null) return "";
            Properties properties = new Properties();
            properties.load(stream);
            String value = properties.getProperty(name, "");
            // An unexpanded template or an unset project version must disable automatic
            // dependencies instead of producing unresolvable coordinates.
            return value.contains("${") || value.equals("unspecified") ? "" : value;
        } catch (IOException exception) {
            return "";
        }
    }
}
