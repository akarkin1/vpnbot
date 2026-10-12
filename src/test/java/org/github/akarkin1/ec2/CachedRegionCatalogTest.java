package org.github.akarkin1.ec2;

import org.github.akarkin1.util.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The cases of {@code CachedTaskConfigServiceTest} for the region catalog (same decorator). */
@ExtendWith(MockitoExtension.class)
class CachedRegionCatalogTest {

  private static final Duration TTL = Duration.ofSeconds(300);
  private static final Set<String> REGIONS = Set.of("eu-central-1", "us-east-1");

  @Mock
  private RegionCatalog delegate;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-12T10:00:00Z"));

  @Test
  @DisplayName("5 §4.5: constructing the decorator does not call the delegate")
  void noCallOnConstruction() {
    new CachedRegionCatalog(delegate, TTL, clock);

    verifyNoInteractions(delegate);
  }

  @Test
  @DisplayName("5 AC-6: the enabled regions are loaded once within the TTL")
  void cachedWithinTtl() {
    when(delegate.enabledRegions()).thenReturn(REGIONS);
    CachedRegionCatalog catalog = new CachedRegionCatalog(delegate, TTL, clock);

    assertEquals(REGIONS, catalog.enabledRegions());
    clock.advance(TTL.minusMillis(1));
    assertEquals(REGIONS, catalog.enabledRegions());

    verify(delegate, times(1)).enabledRegions();
  }

  @Test
  @DisplayName("5 AC-6: the enabled regions are reloaded after the TTL and then cached again")
  void reloadedAfterTtl() {
    Set<String> updated = Set.of("eu-central-1", "us-east-1", "ap-southeast-2");
    when(delegate.enabledRegions()).thenReturn(REGIONS, updated);
    CachedRegionCatalog catalog = new CachedRegionCatalog(delegate, TTL, clock);

    assertEquals(REGIONS, catalog.enabledRegions());
    clock.advance(TTL.plusMillis(1));
    assertEquals(updated, catalog.enabledRegions());
    assertEquals(updated, catalog.enabledRegions());

    verify(delegate, times(2)).enabledRegions();
  }

  @Test
  @DisplayName("5 AC-6: TTL 0 delegates every call")
  void zeroTtlDelegatesEveryCall() {
    when(delegate.enabledRegions()).thenReturn(REGIONS);
    CachedRegionCatalog catalog = new CachedRegionCatalog(delegate, Duration.ZERO, clock);

    for (int i = 0; i < 3; i++) {
      assertEquals(REGIONS, catalog.enabledRegions());
    }

    verify(delegate, times(3)).enabledRegions();
  }

  @Test
  @DisplayName("5 §4.5: a failure of the delegate propagates and is not cached – the next call asks again")
  void failureNotCached() {
    IllegalStateException failure = new IllegalStateException("ec2 down");
    when(delegate.enabledRegions()).thenThrow(failure).thenReturn(REGIONS);
    CachedRegionCatalog catalog = new CachedRegionCatalog(delegate, TTL, clock);

    assertEquals(failure, assertThrows(IllegalStateException.class, catalog::enabledRegions));
    assertEquals(REGIONS, catalog.enabledRegions());
    assertEquals(REGIONS, catalog.enabledRegions());

    verify(delegate, times(2)).enabledRegions();
  }

}
