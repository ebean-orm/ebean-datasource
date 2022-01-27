package io.ebean.datasource;

import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


public class DataSourceConfigTest {

  @Test
  public void addProperty() {

    DataSourceConfig config = new DataSourceConfig();
    assertNull(config.getCustomProperties());

    config.addProperty("useSSL", false);
    final Map<String, String> extra = config.getCustomProperties();
    assertThat(extra).hasSize(1);
    assertThat(extra.get("useSSL")).isEqualTo("false");

    config.addProperty("foo", 42);
    assertThat(extra.get("foo")).isEqualTo("42");

    config.addProperty("bar", "silly");
    assertThat(extra.get("bar")).isEqualTo("silly");
    assertThat(extra).hasSize(3);
  }

  @Test
  public void parseCustom() {

    DataSourceConfig config = new DataSourceConfig();
    Map<String, String> map = config.parseCustom("a=1;b=2;c=3");

    assertThat(map).hasSize(3);
    assertThat(map.get("a")).isEqualTo("1");
    assertThat(map.get("b")).isEqualTo("2");
    assertThat(map.get("c")).isEqualTo("3");
  }

  @Test
  public void isEmpty() {
    DataSourceConfig config = new DataSourceConfig();
    AssertionsForClassTypes.assertThat(config.isEmpty()).isTrue();

    config.setUrl("foo");
    AssertionsForClassTypes.assertThat(config.isEmpty()).isFalse();

    config = new DataSourceConfig();
    config.setUsername("foo");
    AssertionsForClassTypes.assertThat(config.isEmpty()).isFalse();

    config = new DataSourceConfig();
    config.setPassword("foo");
    AssertionsForClassTypes.assertThat(config.isEmpty()).isFalse();

    config = new DataSourceConfig();
    config.setDriver("foo");
    AssertionsForClassTypes.assertThat(config.isEmpty()).isFalse();
  }

  @Test
  public void copy() {

    DataSourceConfig source = new DataSourceConfig();
    source.setMinConnections(42);
    source.setMaxConnections(45);
    source.setUsername("un");
    source.setPassword("pw");
    source.setUrl("url");
    source.setReadOnlyUrl("readOnlyUrl");
    source.setSchema("sch");

    Map<String,String> customSource = new LinkedHashMap<>();
    customSource.put("a","a");
    customSource.put("b","b");
    source.setCustomProperties(customSource);


    DataSourceConfig copy = source.copy();
    assertEquals("un", copy.getUsername());
    assertEquals("pw", copy.getPassword());
    assertEquals("url", copy.getUrl());
    assertEquals("readOnlyUrl", copy.getReadOnlyUrl());
    assertEquals("sch", copy.getSchema());
    assertEquals(42, copy.getMinConnections());
    assertEquals(45, copy.getMaxConnections());

    customSource.put("a","modifiedA");
    customSource.put("c","newC");

    assertEquals("a", copy.getCustomProperties().get("a"));
    assertEquals("b", copy.getCustomProperties().get("b"));
    assertNull(copy.getCustomProperties().get("c"));
  }

  @Test
  public void defaults() {

    DataSourceConfig config = create();

    DataSourceConfig readOnly = new DataSourceConfig();
    readOnly.setDefaults(config);

    assertThat(readOnly.getDriver()).isEqualTo(config.getDriver());
    assertThat(readOnly.getUrl()).isEqualTo(config.getUrl());
    assertThat(readOnly.getUsername()).isEqualTo(config.getUsername());
    assertThat(readOnly.getPassword()).isEqualTo(config.getPassword());
    assertThat(readOnly.getSchema()).isEqualTo(config.getSchema());
    assertThat(readOnly.getCustomProperties()).containsKeys("useSSL");
  }

  @Test
  public void defaults_someOverride() {

    DataSourceConfig readOnly = new DataSourceConfig();
    readOnly.setUsername("foo2");
    readOnly.setUrl("jdbc:postgresql://127.0.0.2:5432/unit");

    DataSourceConfig config = create();
    readOnly.setDefaults(config);

    assertThat(readOnly.getPassword()).isEqualTo(config.getPassword());
    assertThat(readOnly.getDriver()).isEqualTo(config.getDriver());
    assertThat(readOnly.getUrl()).isEqualTo("jdbc:postgresql://127.0.0.2:5432/unit");
    assertThat(readOnly.getUsername()).isEqualTo("foo2");

  }

  private DataSourceConfig create() {
    DataSourceConfig config = new DataSourceConfig();
    config.setDriver("org.postgresql.Driver");
    config.setUrl("jdbc:postgresql://127.0.0.1:5432/unit");
    config.setUsername("foo");
    config.setPassword("bar");
    config.addProperty("useSSL", false);
    return config;
  }

  @Test
  public void loadSettings() throws IOException {

    DataSourceConfig config = new DataSourceConfig();

    Properties props = new Properties();
    props.load(getClass().getResourceAsStream("/example.properties"));
    config.loadSettings(props, "foo");

    assertThat(config.getReadOnlyUrl()).isEqualTo("myReadOnlyUrl");
    assertThat(config.getUrl()).isEqualTo("myUrl");
    assertThat(config.getUsername()).isEqualTo("myusername");
    assertThat(config.getPassword()).isEqualTo("mypassword");
    assertThat(config.getSchema()).isEqualTo("myschema");
  }
}
