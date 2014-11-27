package com.googlecode.greysanatomy.console.command;

import com.googlecode.greysanatomy.agent.GreysAnatomyClassFileTransformer.TransformResult;
import com.googlecode.greysanatomy.console.command.annotation.RiscCmd;
import com.googlecode.greysanatomy.console.command.annotation.RiscIndexArg;
import com.googlecode.greysanatomy.console.command.annotation.RiscNamedArg;
import com.googlecode.greysanatomy.console.server.ConsoleServer;
import com.googlecode.greysanatomy.probe.Advice;
import com.googlecode.greysanatomy.probe.AdviceListenerAdapter;
import com.googlecode.greysanatomy.util.GaStringUtils;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.lang.instrument.Instrumentation;

import static com.googlecode.greysanatomy.agent.GreysAnatomyClassFileTransformer.transform;
import static com.googlecode.greysanatomy.console.server.SessionJobsHolder.registJob;
import static com.googlecode.greysanatomy.probe.ProbeJobs.activeJob;

@RiscCmd(named = "watch", sort = 4, desc = "The call context information buried point observation methods.",
        eg = {
                "watch -b org\\.apache\\.commons\\.lang\\.StringUtils isBlank p.params[0]",
                "watch -f org\\.apache\\.commons\\.lang\\.StringUtils isBlank p.returnObj",
                "watch -bf .*StringUtils isBlank p.params[0]",
                "watch .*StringUtils isBlank p.params[0]",
        })
public class WatchCommand extends Command {

    @RiscIndexArg(index = 0, name = "class-regex", description = "regex match of classpath.classname")
    private String classRegex;

    @RiscIndexArg(index = 1, name = "method-regex", description = "regex match of methodname")
    private String methodRegex;

    @RiscIndexArg(index = 2, name = "express",
            description = "expression, write by javascript. use 'p.' before express",
            description2 = ""
                    + " \n"
                    + "For example\n"
                    + "    : p.params[0]\n"
                    + "    : p.params[0]+p.params[1]\n"
                    + "    : p.returnObj\n"
                    + "    : p.throwExp\n"
                    + "    : p.target.targetThis.getClass()\n"
                    + " \n"
                    + "The structure of 'p'\n"
                    + "    p.\n"
                    + "    \\+- params[0..n] : the parameters of methods\n"
                    + "    \\+- returnObj    : the return object of methods\n"
                    + "    \\+- throwExp     : the throw exception of methods\n"
                    + "    \\+- target\n"
                    + "         \\+- targetThis  : the object entity\n"
                    + "         \\+- targetClassName : the object's class\n"
                    + "         \\+- targetBehaviorName : the constructor or method name\n"
    )

    private String expression;

    @RiscNamedArg(named = "b", description = "is watch on before")
    private boolean isBefore = true;

    @RiscNamedArg(named = "f", description = "is watch on finish")
    private boolean isFinish = false;

    @RiscNamedArg(named = "e", description = "is watch on exception")
    private boolean isException = false;

    @RiscNamedArg(named = "s", description = "is watch on success")
    private boolean isSuccess = false;

    @Override
    public Action getAction() {
        return new Action() {

            @Override
            public void action(final ConsoleServer consoleServer, Info info, final Sender sender) throws Throwable {
                final ScriptEngine jsEngine = new ScriptEngineManager().getEngineByExtension("js");

                jsEngine.eval("function printWatch(p,o){try{o.send(false, " + expression + "+'\\n');}catch(e){o.send(false, e.message+'\\n');}}");
                final Invocable invoke = (Invocable) jsEngine;

                final Instrumentation inst = info.getInst();
                final TransformResult result = transform(inst, classRegex, methodRegex, new AdviceListenerAdapter() {

                    @Override
                    public void onBefore(Advice p) {
                        if (isBefore) {
                            try {
                                invoke.invokeFunction("printWatch", p, sender);
                            } catch (Exception e) {
                            }
                        }
                    }

                    @Override
                    public void onFinish(Advice p) {
                        if (isFinish) {
                            try {
                                invoke.invokeFunction("printWatch", p, sender);
                            } catch (Exception e) {
                            }
                        }
                    }

                    @Override
                    public void onException(Advice p) {
                        if (isException) {
                            try {
                                invoke.invokeFunction("printWatch", p, sender);
                            } catch (Exception e) {
                            }
                        }
                    }

                    @Override
                    public void onSuccess(Advice p) {
                        if (isSuccess) {
                            try {
                                invoke.invokeFunction("printWatch", p, sender);
                            } catch (Exception e) {
                            }
                        }
                    }

                }, info);

                // 注册任务
                registJob(info.getSessionId(), result.getId());

                // 激活任务
                activeJob(result.getId());

                final StringBuilder message = new StringBuilder();
                message.append(GaStringUtils.LINE);
                message.append(String.format("done. probe:c-Cnt=%s,m-Cnt=%s\n",
                        result.getModifiedClasses().size(),
                        result.getModifiedBehaviors().size()));
                message.append(GaStringUtils.ABORT_MSG).append("\n");
                sender.send(false, message.toString());
            }

        };
    }

}
