package org.github.akarkin1.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class UserNameUtil {

  public static String normalizeUserName(String value) {
    String username = value.trim();
    if (username.startsWith("@")) {
      username = username.substring(1);
    }

    return username;
  }

}
