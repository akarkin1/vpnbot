package org.github.akarkin1.deduplication;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface UpdateEventsRegistry {

  /** Records the update; false if it was already recorded (a re-delivery). */
  boolean register(Update update);

}
