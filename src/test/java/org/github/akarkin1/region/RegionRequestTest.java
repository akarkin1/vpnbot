package org.github.akarkin1.region;

import org.github.akarkin1.auth.TgUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** TgUser equality looks at the id only, so the username is checked separately. */
class RegionRequestTest {

  @Test
  @DisplayName("5 §7: user() is the requester as a TgUser (id and username)")
  void user() {
    RegionRequest request = new RegionRequest("eu-west-3", 42L, "Some_User", "Alex", "Smith", "en",
                                              1_760_097_600_123L);

    TgUser user = request.user();

    assertEquals(42L, user.id());
    assertEquals("Some_User", user.username());
  }

  @Test
  @DisplayName("5 §7: user() of a requester without a username has a null username")
  void userWithoutUsername() {
    RegionRequest request = new RegionRequest("eu-west-3", 42L, null, "Alex", null, "en",
                                              1_760_097_600_123L);

    TgUser user = request.user();

    assertEquals(42L, user.id());
    assertNull(user.username());
  }

}
