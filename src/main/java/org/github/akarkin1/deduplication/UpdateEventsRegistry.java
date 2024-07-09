package org.github.akarkin1.deduplication;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface UpdateEventsRegistry {

  boolean hasAlreadyProcessed(Update update);

  void registerEvent(Update update);
}
