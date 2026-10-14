package com.defiagent.service;

import com.defiagent.cli.DefiCliClient;
import com.defiagent.cli.DefiCliException;
import com.defiagent.cli.model.ActionStep;
import com.defiagent.cli.model.ExecutionAction;
import com.defiagent.domain.ActionStatus;
import com.defiagent.domain.AgentAction;
import com.defiagent.domain.IllegalActionStateException;
import com.defiagent.guardrails.GuardrailResult;
import com.defiagent.guardrails.GuardrailService;
import com.defiagent.intent.Intent;
import com.defiagent.intent.IntentParser;
import com.defiagent.kafka.ExecutionProducer;
import com.defiagent.repo.AgentActionRepository;
import com.defiagent.web.dto.ChatResponse;
import com.defiagent.yield.YieldOpportunity;
import com.defiagent.yield.YieldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates a single chat turn:
 * parse intent -> (plan | confirm | status | help).
 *
 * <p>This is the rule-based fallback agent used when {@code OPENAI_API_KEY} is
 * not set (see {@code ChatController}); the Spring AI path goes through
 * {@code AgentChatService}/{@code DefiToolService} instead. Both paths funnel
 * into the same {@link AgentAction} state machine and {@link ExecutionService}.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final IntentParser intentParser;
    private final YieldService yieldService;
    private final AgentActionRepository repository;
    private final ExecutionProducer executionProducer;
    private final DefiCliClient cli;
    private final GuardrailService guardrails;

    public AgentService(IntentParser intentParser,
                        YieldService yieldService,
                        AgentActionRepository repository,
                        ExecutionProducer executionProducer,
                        DefiCliClient cli,
                        GuardrailService guardrails) {
        this.intentParser = intentParser;
        this.yieldService = yieldService;
        this.repository = repository;
        this.executionProducer = executionProducer;
        this.cli = cli;
        this.guardrails = guardrails;
    }

    public ChatResponse handle(String sessionId, String message) {
        String session = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        Intent intent = intentParser.parse(message);
        log.info("session={} intent={} message={}", session, intent.type(), message);

        return switch (intent.type()) {
            case DEPOSIT -> plan(session, intent);
            case CONFIRM -> confirm(session);
            case STATUS -> status(session);
            case HELP -> ChatResponse.text(helpText());
        };
    }

    private ChatResponse plan(String session, Intent intent) {
        List<YieldOpportunity> top = yieldService.topOpportunities(intent.asset(), intent.chain(), 3);
        if (top.isEmpty()) {
            return ChatResponse.text("No yield opportunities found for " + intent.asset() + " on " + intent.chain() + ".");
        }
        YieldOpportunity best = top.get(0);

        AgentAction action = new AgentAction();
        action.setActionId(UUID.randomUUID().toString());
        action.setSessionId(session);
        action.setIntent("DEPOSIT");
        action.setProvider(best.provider());
        action.setChain(best.chain());
        action.setAsset(best.asset());
        action.setAmount(intent.amount());
        action.setApy(best.apy());
        action.setTvlUsd(best.tvlUsd());
        action.setStatus(ActionStatus.PLANNED);

        StringBuilder sb = new StringBuilder();
        sb.append("Compared ").append(top.size()).append(" protocols. Suggested option: ")
                .append(best.provider()).append(" (").append(best.chain()).append("):\n");
        for (YieldOpportunity o : top) {
            sb.append(String.format("  - %-10s APY %.2f%%  TVL $%,.0f%n", o.provider(), o.apy(), o.tvlUsd()));
        }
        sb.append(String.format("Plan: deposit %s %s into %s.%n", intent.amount(), best.asset(), best.provider()));

        // Best-effort: if the user gave a wallet address, also create a REAL defi-cli
        // plan (real action_id + on-chain call steps) so /api/actions/{id}/estimate has
        // something real to estimate. Never blocks the paper plan if this fails.
        if (intent.address() != null) {
            tryCreateRealPlan(action, intent, best, sb);
        }

        repository.save(action);
        sb.append("For on-chain execution, choose Review in wallet and sign in MetaMask. Reply confirm to run a paper simulation.");

        return new ChatResponse(sb.toString(), action.getActionId(), ActionStatus.PLANNED.name(), top);
    }

    private void tryCreateRealPlan(AgentAction action, Intent intent, YieldOpportunity best, StringBuilder sb) {
        GuardrailResult check = guardrails.checkProtocolAllowed(best.provider());
        if (!check.allowed()) {
            log.info("skipping real defi-cli plan, blocked by guardrail: {}", check.reason());
            return;
        }
        try {
            String baseUnits = AssetUnits.toBaseUnits(intent.amount(), best.asset());
            ExecutionAction planned = cli.yieldDepositPlan(
                    best.provider(), best.chain(), best.asset(), baseUnits, intent.address(), null, null);
            if (planned != null && planned.actionId() != null) {
                action.setDefiActionId(planned.actionId());
                action.setStepsSummary(summarizeSteps(planned));
                sb.append("Created a CLI transaction plan with action_id=").append(planned.actionId())
                        .append(" (not broadcast; use GET /api/actions/").append(action.getActionId())
                        .append("/estimate to request a gas estimate).\n");
            }
        } catch (DefiCliException e) {
            log.info("real defi-cli plan not created (code={} type={}): {}", e.getCode(), e.getType(), e.getMessage());
        } catch (Exception e) {
            log.warn("real defi-cli plan failed unexpectedly: {}", e.getMessage());
        }
    }

    private String summarizeSteps(ExecutionAction action) {
        if (action.steps() == null || action.steps().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (ActionStep step : action.steps()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(step.type()).append(":").append(step.status());
        }
        return sb.length() > 2000 ? sb.substring(0, 2000) : sb.toString();
    }

    private ChatResponse confirm(String session) {
        AgentAction pending = repository
                .findFirstBySessionIdAndStatusOrderByCreatedAtDesc(session, ActionStatus.PLANNED)
                .orElse(null);
        if (pending == null) {
            return ChatResponse.text("No action is awaiting confirmation. Generate a plan first, for example: Deposit 1000 USDC into a yield pool on Ethereum.");
        }
        try {
            pending.transitionTo(ActionStatus.QUEUED);
        } catch (IllegalActionStateException e) {
            // Racing confirm (e.g. double-click) or the action already moved on; don't
            // publish a second execution command for the same actionId.
            log.warn("confirm rejected: {}", e.getMessage());
            return ChatResponse.text("This action is already processing or complete. Reply status to check the result.");
        }
        pending = repository.save(pending);
        executionProducer.publish(pending.getActionId());

        return new ChatResponse(
                "Queued for paper execution. This does not open MetaMask or move real funds. Choose Review in wallet for on-chain execution.",
                pending.getActionId(),
                ActionStatus.QUEUED.name(),
                null);
    }

    private ChatResponse status(String session) {
        List<AgentAction> recent = repository.findTop20BySessionIdOrderByCreatedAtDesc(session);
        if (recent.isEmpty()) {
            return ChatResponse.text("There are no actions in this conversation yet.");
        }
        AgentAction latest = recent.get(0);
        String reply = switch (latest.getStatus()) {
            case CONFIRMED -> "✅ Action recorded as confirmed. txHash=" + latest.getTxHash();
            case EXECUTING, QUEUED -> "⏳ Execution is in progress. Check again shortly.";
            case PLANNED -> "📝 Plan created, awaiting confirmation. Reply confirm to run a paper simulation.";
            case FAILED -> "❌ Execution failed.";
            case CANCELLED -> "Cancelled.";
        };
        return new ChatResponse(reply, latest.getActionId(), latest.getStatus().name(), latest);
    }

    private String helpText() {
        return """
                I am your DeFi AI Agent. Try:
                  - Deposit 1000 USDC into a yield pool on Ethereum (create a plan)
                  - Confirm (paper execution)
                  - Status (check the result)
                """;
    }
}
