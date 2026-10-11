package org.github.akarkin1.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleSetTest {

  @Test
  @DisplayName("4 AC-5: the role bits are NODE_ADMIN = 1, READ_ONLY = 2, USER_ADMIN = 4")
  void bits() {
    assertEquals(0, RoleSet.encode(Set.of()));
    assertEquals(1, RoleSet.encode(Set.of(UserRole.NODE_ADMIN)));
    assertEquals(2, RoleSet.encode(Set.of(UserRole.READ_ONLY)));
    assertEquals(4, RoleSet.encode(Set.of(UserRole.USER_ADMIN)));
    assertEquals(7, RoleSet.encode(EnumSet.allOf(UserRole.class)));
  }

  @Test
  @DisplayName("4 AC-5: decode(0) is the empty set")
  void decodeZero() {
    assertEquals(Set.of(), RoleSet.decode(0));
  }

  @Test
  @DisplayName("4 AC-5: every subset of UserRole round-trips through encode/decode")
  void everySubsetRoundTrips() {
    for (Set<UserRole> subset : subsets()) {
      int mask = RoleSet.encode(subset);

      assertEquals(subset, new HashSet<>(RoleSet.decode(mask)), "subset " + subset);
      assertEquals(mask, RoleSet.encode(RoleSet.decode(mask)), "mask " + mask);
    }
  }

  @Test
  @DisplayName("4 AC-5: the eight subsets have the eight distinct masks 0..7")
  void masksAreDistinct() {
    Set<Integer> masks = new HashSet<>();
    for (Set<UserRole> subset : subsets()) {
      masks.add(RoleSet.encode(subset));
    }

    assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7), masks);
  }

  private static List<Set<UserRole>> subsets() {
    UserRole[] roles = UserRole.values();
    List<Set<UserRole>> subsets = new ArrayList<>();
    for (int bits = 0; bits < 1 << roles.length; bits++) {
      Set<UserRole> subset = EnumSet.noneOf(UserRole.class);
      for (int i = 0; i < roles.length; i++) {
        if ((bits & 1 << i) != 0) {
          subset.add(roles[i]);
        }
      }
      subsets.add(subset);
    }
    return subsets;
  }

}
