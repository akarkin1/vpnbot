package org.github.akarkin1.dispatcher;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.s3.EntitlementsService;
import org.github.akarkin1.dispatcher.command.*;
import org.github.akarkin1.service.NodeService;
import org.github.akarkin1.tg.BotCommunicator;
import org.github.akarkin1.config.ConfigManager;

import javax.inject.Singleton;

@Singleton
public class GuiceCommandDispatcherProvider implements Provider<CommandDispatcher> {
    private final BotCommunicator communicator;
    private final Authorizer authorizer;
    private final NodeService nodeService;
    private final EntitlementsService entitlementsService;
    private final ConfigManager configManager;

    @Inject
    public GuiceCommandDispatcherProvider(BotCommunicator communicator,
                                          Authorizer authorizer,
                                          NodeService nodeService,
                                          EntitlementsService entitlementsService,
                                          ConfigManager configManager) {
        this.communicator = communicator;
        this.authorizer = authorizer;
        this.nodeService = nodeService;
        this.entitlementsService = entitlementsService;
        this.configManager = configManager;
    }

    @Override
    public CommandDispatcher get() {
        CommandDispatcher dispatcher = new CommandDispatcher(communicator, authorizer);
        dispatcher.registerCommand("/version", new VersionCommand(configManager));
        dispatcher.registerCommand("/listRunningNodes", new ListNodesCommand(nodeService, authorizer));
        dispatcher.registerCommand("/runNode", new RunNodeCommand(nodeService, authorizer, communicator::sendMessageToTheBot));
        dispatcher.registerCommand("/supportedRegions", new SupportedRegionCommand(nodeService, authorizer));
        dispatcher.registerCommand("/listServices", new ListServicesCommand(authorizer));
        dispatcher.registerCommand("/assignRoles", new AssignRolesCommand(entitlementsService));
        dispatcher.registerCommand("/describeRoles", new DescribeRolesCommand(entitlementsService));
        dispatcher.registerCommand("/deleteUsers", new DeleteUsersCommand(entitlementsService, communicator::sendMessageToTheBot));
        dispatcher.registerCommand("/listRegisteredUsers", new ListUsersCommand(entitlementsService));
        return dispatcher;
    }
}

