package org.github.akarkin1.ui.screen;

import java.util.List;

public record UsersModel(List<UserRow> users) {

  public UsersModel {
    users = List.copyOf(users);
  }

}
