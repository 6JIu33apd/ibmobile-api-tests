package ru.avangard.qa.ibmobile.core;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.Optional;

/** На prod запускаются только тесты с @Tag("prod") на методе или классе. Подключается автоматически. */
public class ProdGuard implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (!Config.isProd() || context.getTestMethod().isEmpty()) {
            return ConditionEvaluationResult.enabled("не prod");
        }
        for (Optional<ExtensionContext> c = Optional.of(context); c.isPresent(); c = c.get().getParent()) {
            if (c.get().getTags().contains("prod")) {
                return ConditionEvaluationResult.enabled("@Tag(\"prod\")");
            }
        }
        return ConditionEvaluationResult.disabled("На prod запускаются только тесты с @Tag(\"prod\")");
    }
}
