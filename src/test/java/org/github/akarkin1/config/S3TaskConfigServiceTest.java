package org.github.akarkin1.config;

import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.s3.S3ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3TaskConfigServiceTest {

  private static final String REGIONS_KEY = "supported-regions.txt";

  @Mock
  private S3ConfigManager s3ConfigManager;

  private S3TaskConfigService service;

  @BeforeEach
  void setUp() {
    S3Configuration config = new S3Configuration();
    config.setRegionsKey(REGIONS_KEY);
    service = new S3TaskConfigService(s3ConfigManager, config);
  }

  @Test
  void parsesOneRegionPerLine() {
    when(s3ConfigManager.downloadConfigFromS3(REGIONS_KEY)).thenReturn("eu-west-1\nus-east-1\n");

    assertEquals(List.of(Region.EU_WEST_1, Region.US_EAST_1), service.getSupportedRegions());
  }

  @Test
  void emptyFileMeansNoRegions() {
    when(s3ConfigManager.downloadConfigFromS3(REGIONS_KEY)).thenReturn("");

    assertTrue(service.getSupportedRegions().isEmpty());
  }

  @Test
  void skipsBlankLinesAndUnknownRegions() {
    when(s3ConfigManager.downloadConfigFromS3(REGIONS_KEY))
        .thenReturn("\n eu-west-1 \r\n\nmars-north-1\n");

    assertEquals(List.of(Region.EU_WEST_1), service.getSupportedRegions());
  }
}
