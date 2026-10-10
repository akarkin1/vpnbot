package org.github.akarkin1.startup;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.PermissionsService;
import org.github.akarkin1.deduplication.UpdateEventsRegistry;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.telegram.telegrambots.meta.api.methods.GetMe;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;

import java.util.concurrent.TimeUnit;

/**
 * Runs the DynamoDB, ECS/EC2 and Telegram request paths once at init, so that their class loading
 * and client setup end up in the SnapStart snapshot instead of slowing down the first request after
 * a restore. Results are discarded and failures are only logged: priming never breaks the init.
 */
@Log4j2
@RequiredArgsConstructor
public class SnapStartPrimer {

  /** Owner id that matches no node. */
  public static final String PRIMER_USER = "snapstart-primer";
  /** Telegram update ids are positive, so the primer's lock record never collides with one. */
  public static final int PRIMER_UPDATE_ID = -1;

  private final TailscaleNodeService nodeService;
  private final PermissionsService permissionsService;
  private final UpdateEventsRegistry eventsRegistry;
  private final AbsSender sender;

  public void prime() {
    long begin = System.nanoTime();
    runStep("permissions", permissionsService::getUserPermissions);
    runStep("nodes", () -> nodeService.listTasks(PRIMER_USER));
    runStep("updateLock", () -> eventsRegistry.register(primerUpdate()));
    runStep("telegram", () -> sender.execute(new GetMe()));
    log.info("SnapStart priming finished in {} ms",
             TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
  }

  private static Update primerUpdate() {
    Update update = new Update();
    update.setUpdateId(PRIMER_UPDATE_ID);
    return update;
  }

  private static void runStep(String name, Step step) {
    try {
      step.run();
    } catch (Exception e) {
      log.warn("Priming step {} failed", name, e);
    }
  }

  @FunctionalInterface
  private interface Step {

    void run() throws Exception;

  }

}
