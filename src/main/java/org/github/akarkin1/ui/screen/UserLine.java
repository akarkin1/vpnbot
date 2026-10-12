package org.github.akarkin1.ui.screen;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static org.github.akarkin1.ui.screen.NodeFormat.orDash;

/**
 * The requester line shared by the access and region request messages (D-8):
 * {@code <b>First Last</b> (@username, id)}; without a last name just {@code <b>First</b>},
 * without a username {@code <b>First Last</b> (id)}. The name, username and id are params.
 */
record UserLine(String template, List<Object> params) {

  static UserLine of(long userId, String username, String firstName, String lastName) {
    if (StringUtils.isBlank(username)) {
      return new UserLine("<b>%s</b> (%s)", List.of(fullName(firstName, lastName), userId));
    }
    return new UserLine("<b>%s</b> (@%s, %s)",
                        List.of(fullName(firstName, lastName), username, userId));
  }

  private static String fullName(String firstName, String lastName) {
    String name = StringUtils.isBlank(lastName) ? firstName : firstName + " " + lastName;
    return orDash(name);
  }

}
