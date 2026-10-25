package ru.avangard.qa.ibmobile.core;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** На prod запускаются только классы с @Tag("prod"); проверка на уровне класса, чтобы не было даже логина в @BeforeAll. */
public class ProdGuard implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return !"prod".equals(Config.ENV) || context.getTestClass().isEmpty() || context.getTags().contains("prod")
                ? ConditionEvaluationResult.enabled("")
                : ConditionEvaluationResult.disabled("На prod только классы с @Tag(\"prod\")");
    }
}
