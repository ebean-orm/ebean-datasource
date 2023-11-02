package io.ebean.datasource;

import java.util.Properties;

/**
 * Helper used to read Properties.
 */
final class ConfigPropertiesHelper {

  private final Properties properties;
  private final String prefix;
  private final String poolName;

  /**
   * Construct with a prefix, serverName and properties.
   */
  ConfigPropertiesHelper(String prefix, String poolName, Properties properties) {
    this.prefix = prefix;
    this.poolName = poolName;
    this.properties = properties;
  }

  /**
   * Get a property with no default value.
   */
  private String read(String key) {
    String val = properties.getProperty(key.toLowerCase());
    if (val == null) {
      return properties.getProperty(key);
    }
    return val;
  }

  /**
   * Get a property with a default value.
   * <p>
   * This performs a search using the prefix and server name (if supplied) to search for the property
   * value in order based on:
   * <pre>{@code
   *
   *   prefix.serverName.key
   *   prefix.key
   *   key
   *
   * }</pre>
   * </p>
   */
  String get(String key, String defaultValue) {
    String value = null;
    if (poolName != null && prefix != null) {
      value = read(prefix + "." + poolName + "." + key);
    }
    if (value == null && prefix != null) {
      value = read(prefix + "." + key);
    }
    if (value == null) {
      value = read(key);
    }
    return value == null ? defaultValue : value;
  }

  /**
   * Return an int property value.
   */
  int getInt(String key, int defaultValue) {
    String value = get(key, String.valueOf(defaultValue));
    return Integer.parseInt(value);
  }

  /**
   * Return a boolean property value.
   */
  boolean getBoolean(String key, boolean defaultValue) {
    String value = get(key, String.valueOf(defaultValue));
    return Boolean.parseBoolean(value);
  }

}
