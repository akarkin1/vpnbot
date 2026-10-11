package org.github.akarkin1.ui.screen;

import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;

import java.util.List;

/**
 * A known user on the Users screen.
 *
 * @param editable false for a root user and for the caller: their roles are not changed from the UI
 */
public record UserRow(TgUser user, List<Permission> permissions, boolean editable) {

  public UserRow {
    permissions = List.copyOf(permissions);
  }

}
