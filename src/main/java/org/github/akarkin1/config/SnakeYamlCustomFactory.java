package org.github.akarkin1.config;

import lombok.experimental.UtilityClass;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

@UtilityClass
public class SnakeYamlCustomFactory {

  public static Yaml createYaml() {
    Constructor constructor = new Constructor(YamlApplicationConfiguration.class);
    constructor.setPropertyUtils(new PropertyUtils() {
      @Override
      public Property getProperty(Class<? extends Object> type, String name) {
        if (name.indexOf('-') > -1) {
          name = toCamelCase(name);
        }
        return super.getProperty(type, name);
      }
    });
    return new Yaml(constructor);
  }

  private static String toCamelCase(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }

    StringBuilder camelCaseString = new StringBuilder();
    boolean capitalizeNext = false;

    for (char ch : str.toCharArray()) {
      if (ch == '_' || ch == '-') {
        capitalizeNext = true; // Set the flag to capitalize the next character
      } else {
        if (capitalizeNext) {
          camelCaseString.append(Character.toUpperCase(ch)); // Capitalize the character
          capitalizeNext = false; // Reset the flag
        } else {
          camelCaseString.append(Character.toLowerCase(ch)); // Keep it lowercase
        }
      }
    }

    return camelCaseString.toString();
  }
}
