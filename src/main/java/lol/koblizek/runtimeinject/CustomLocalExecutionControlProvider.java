package lol.koblizek.runtimeinject;

import jdk.jshell.execution.LoaderDelegate;
import jdk.jshell.execution.LocalExecutionControl;
import jdk.jshell.execution.LocalExecutionControlProvider;
import jdk.jshell.spi.ExecutionControl;
import jdk.jshell.spi.ExecutionEnv;

import java.util.Map;

public class CustomLocalExecutionControlProvider extends LocalExecutionControlProvider {
    @Override
    public ExecutionControl generate(ExecutionEnv env, Map<String, String> parameters) {
        return new CustomLocalExecutionControl(new NewDefaultLoaderDelegate());
    }

    public static class CustomLocalExecutionControl extends LocalExecutionControl {
        public CustomLocalExecutionControl(LoaderDelegate loaderDelegate) {
            super(loaderDelegate);
        }

        @Override
        public void addToClasspath(String cp) throws EngineTerminationException, InternalException {
            if (!cp.contains("libraries"))
                super.addToClasspath(cp);
        }
    }
}
