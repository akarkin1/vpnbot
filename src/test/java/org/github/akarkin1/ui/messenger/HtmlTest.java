package org.github.akarkin1.ui.messenger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HtmlTest {

  @Test
  @DisplayName("AC-13: escape replaces &, <, > and \"")
  void escapesSpecialCharacters() {
    assertEquals("&lt;b&gt;Tom &amp; &quot;Jerry&quot;&lt;/b&gt;", Html.escape("<b>Tom & \"Jerry\"</b>"));
  }

  @Test
  @DisplayName("AC-13: escape does not double-escape the entities it produces")
  void noDoubleEscaping() {
    assertEquals("&lt;&amp;&gt;", Html.escape("<&>"));
  }

  @Test
  @DisplayName("AC-13: escape keeps plain text and turns null into an empty string")
  void plainTextAndNull() {
    assertEquals("node-1 · 🇩🇪 Frankfurt", Html.escape("node-1 · 🇩🇪 Frankfurt"));
    assertEquals("", Html.escape(null));
  }

}
