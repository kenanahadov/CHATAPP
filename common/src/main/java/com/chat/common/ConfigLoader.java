
package com.chat.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


// a helper class to read .properties files that we need to run the server
public class ConfigLoader {
    private static final Properties props = new Properties();
    
    static {
        try (InputStream in = ConfigLoader.class.getResourceAsStream("/application.properties")) {
            if (in == null)
                throw new IllegalStateException("application.properties not found");
            props.load(in);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }

    public static int getInt(String key) {
        return Integer.parseInt(get(key));
    }
}
