package g_earth.protocol.hostreplacer;

import g_earth.misc.OSValidator;

/**
 * Created by Jonas on 04/04/18.
 */
public class HostReplacerFactory {

    public static HostReplacer get() {

        if (OSValidator.isUnix() || OSValidator.isMac()) return new UnixHostReplacer();
        if (OSValidator.isWindows()) return new WindowsHostReplacer();

        return new UnixHostReplacer();
    }

}
