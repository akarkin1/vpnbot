package org.github.akarkin1.config;

import org.github.akarkin1.util.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CachedS3TaskConfigServiceTest {

  private static final Duration TTL = Duration.ofSeconds(300);
  private static final List<Region> REGIONS = List.of(Region.EU_CENTRAL_1, Region.US_EAST_1);
  private static final TaskRuntimeParameters EU_PARAMS = params("eu-cluster");
  private static final TaskRuntimeParameters US_PARAMS = params("us-cluster");

  @Mock
  private TaskConfigService delegate;

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-09T10:00:00Z"));

  @Test
  @DisplayName("2a AC-8: supported regions are loaded once within the TTL")
  void regionsCachedWithinTtl() {
    when(delegate.getSupportedRegions()).thenReturn(REGIONS);
    CachedS3TaskConfigService service = new CachedS3TaskConfigService(delegate, TTL, clock);

    assertEquals(REGIONS, service.getSupportedRegions());
    clock.advance(TTL.minusMillis(1));
    assertEquals(REGIONS, service.getSupportedRegions());

    verify(delegate, times(1)).getSupportedRegions();
  }

  @Test
  @DisplayName("2a AC-8: supported regions are reloaded after the TTL")
  void regionsReloadedAfterTtl() {
    List<Region> updated = List.of(Region.EU_WEST_2);
    when(delegate.getSupportedRegions()).thenReturn(REGIONS, updated);
    CachedS3TaskConfigService service = new CachedS3TaskConfigService(delegate, TTL, clock);

    assertEquals(REGIONS, service.getSupportedRegions());
    clock.advance(TTL.plusMillis(1));
    assertEquals(updated, service.getSupportedRegions());
    assertEquals(updated, service.getSupportedRegions());

    verify(delegate, times(2)).getSupportedRegions();
  }

  @Test
  @DisplayName("2a AC-8: TTL 0 delegates every call")
  void zeroTtlDelegatesEveryCall() {
    when(delegate.getSupportedRegions()).thenReturn(REGIONS);
    when(delegate.getTaskRuntimeParameters(Region.EU_CENTRAL_1)).thenReturn(EU_PARAMS);
    CachedS3TaskConfigService service = new CachedS3TaskConfigService(delegate, Duration.ZERO, clock);

    for (int i = 0; i < 3; i++) {
      assertEquals(REGIONS, service.getSupportedRegions());
      assertSame(EU_PARAMS, service.getTaskRuntimeParameters(Region.EU_CENTRAL_1));
    }

    verify(delegate, times(3)).getSupportedRegions();
    verify(delegate, times(3)).getTaskRuntimeParameters(Region.EU_CENTRAL_1);
  }

  @Test
  @DisplayName("2a AC-8: runtime parameters are cached per region within the TTL")
  void runtimeParametersCachedPerRegion() {
    when(delegate.getTaskRuntimeParameters(Region.EU_CENTRAL_1)).thenReturn(EU_PARAMS);
    when(delegate.getTaskRuntimeParameters(Region.US_EAST_1)).thenReturn(US_PARAMS);
    CachedS3TaskConfigService service = new CachedS3TaskConfigService(delegate, TTL, clock);

    assertSame(EU_PARAMS, service.getTaskRuntimeParameters(Region.EU_CENTRAL_1));
    assertSame(US_PARAMS, service.getTaskRuntimeParameters(Region.US_EAST_1));
    clock.advance(TTL.minusMillis(1));
    assertSame(EU_PARAMS, service.getTaskRuntimeParameters(Region.EU_CENTRAL_1));
    assertSame(US_PARAMS, service.getTaskRuntimeParameters(Region.US_EAST_1));

    verify(delegate, times(1)).getTaskRuntimeParameters(Region.EU_CENTRAL_1);
    verify(delegate, times(1)).getTaskRuntimeParameters(Region.US_EAST_1);
  }

  @Test
  @DisplayName("2a AC-8: runtime parameters are reloaded after the TTL")
  void runtimeParametersReloadedAfterTtl() {
    TaskRuntimeParameters updated = params("eu-cluster-2");
    when(delegate.getTaskRuntimeParameters(Region.EU_CENTRAL_1)).thenReturn(EU_PARAMS, updated);
    CachedS3TaskConfigService service = new CachedS3TaskConfigService(delegate, TTL, clock);

    assertSame(EU_PARAMS, service.getTaskRuntimeParameters(Region.EU_CENTRAL_1));
    clock.advance(TTL.plusMillis(1));
    assertSame(updated, service.getTaskRuntimeParameters(Region.EU_CENTRAL_1));

    verify(delegate, times(2)).getTaskRuntimeParameters(Region.EU_CENTRAL_1);
  }

  private static TaskRuntimeParameters params(String cluster) {
    return TaskRuntimeParameters.builder()
        .ecsClusterName(cluster)
        .ecsTaskDefinition("task-def")
        .subnetId("subnet-1")
        .securityGroupId("sg-1")
        .build();
  }

}
