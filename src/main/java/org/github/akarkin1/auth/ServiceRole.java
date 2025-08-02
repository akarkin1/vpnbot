package org.github.akarkin1.auth;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServiceRole {

  private String serviceName;
  private Role role;

  @Getter
  @RequiredArgsConstructor
  public enum Role {
    NODE_ADMIN(true),
    USER_ADMIN(false),
    READ_ONLY(true);

    private final boolean boundToService;

  }
}