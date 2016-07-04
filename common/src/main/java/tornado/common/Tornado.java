package tornado.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public final class Tornado {

    private static Properties settings = System.getProperties();

    static {
        tryLoadSettings();
    }

    public static String getProperty(String key, String defaultValue) {
        return settings.getProperty(key, defaultValue);
    }

    public static final boolean USE_OPENCL_SCHEDULING = Boolean.parseBoolean(settings.getProperty("tornado.opencl.schedule","False"));
    
    public static final boolean ENABLE_EXCEPTIONS = Boolean
            .parseBoolean(settings.getProperty("tornado.exceptions.enable",
                    "False"));
    public static final boolean ENABLE_OOO_EXECUTION = Boolean
            .parseBoolean(settings.getProperty("tornado.ooo-execution.enable",
                    "False"));
    public static final boolean FORCE_BLOCKING_API_CALLS = Boolean
            .parseBoolean(settings.getProperty("tornado.opencl.blocking",
                    "False"));

    public static final boolean ENABLE_VECTORS = Boolean.parseBoolean(settings
            .getProperty("tornado.vectors.enable", "True"));
    public static final boolean TORNADO_ENABLE_BIFS = Boolean
            .parseBoolean(settings.getProperty("tornado.bifs.enable", "False"));

    public static final boolean DEBUG = Boolean.parseBoolean(settings
            .getProperty("tornado.debug", "False"));

    public static final boolean ENABLE_MEM_CHECKS = Boolean
            .parseBoolean(settings.getProperty("tornado.memory.check", "False"));

    public static final boolean LOG_EVENTS = Boolean.parseBoolean(settings
            .getProperty("tornado.events.log", "False"));
    
    public static final boolean DUMP_PROFILES = Boolean.parseBoolean(settings.getProperty("tornado.profiles.print", "false"));

    public static final boolean DUMP_BINARIES = Boolean.parseBoolean(settings
            .getProperty("tornado.opencl.binaries", "False"));

    public static final String OPENCL_CFLAGS = settings.getProperty(
            "tornado.opencl.cflags", "-w");

    public static final int OPENCL_GPU_BLOCK_X = Integer.parseInt(settings
            .getProperty("tornado.opencl.gpu.block.x", "256"));

    public static final int OPENCL_GPU_BLOCK_2D_X = Integer.parseInt(settings
            .getProperty("tornado.opencl.gpu.block2d.x", "4"));
    public static final int OPENCL_GPU_BLOCK_2D_Y = Integer.parseInt(settings
            .getProperty("tornado.opencl.gpu.block2d.y", "4"));

    public static final TornadoLogger log = new TornadoLogger(Tornado.class);

    public static final void debug(final String msg) {
        log.debug(msg);
    }

    private static void tryLoadSettings() {
        final File localSettings = new File("etc/tornado.properties");
        if (localSettings.exists())
            try {
                settings.load(new FileInputStream(localSettings));
            } catch (IOException e) {
                warn("Unable to load settings from %s",
                        localSettings.getAbsolutePath());
            }

    }

    public static final void debug(final String pattern, final Object... args) {
        debug(String.format(pattern, args));
    }

    public static final void error(final String msg) {
        log.error(msg);
    }

    public static final void error(final String pattern, final Object... args) {
        error(String.format(pattern, args));
    }

    public static final void fatal(final String msg) {
        log.fatal(msg);
    }

    public static final void fatal(final String pattern, final Object... args) {
        fatal(String.format(pattern, args));
    }

    public static final void info(final String msg) {
        log.info(msg);
    }

    public static final void info(final String pattern, final Object... args) {
        info(String.format(pattern, args));
    }

    public static final void trace(final String msg) {
        log.trace(msg);
    }

    public static final void trace(final String pattern, final Object... args) {
        trace(String.format(pattern, args));
    }

    public static final void warn(final String msg) {
        log.warn(msg);
    }

    public static final void warn(final String pattern, final Object... args) {
        warn(String.format(pattern, args));
    }

}