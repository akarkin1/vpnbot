package org.github.akarkin1.ui.messenger;

public final class Html {

  private Html() {
  }

  public static String escape(String value) {
    if (value == null) {
      return "";
    }

    return value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

}
