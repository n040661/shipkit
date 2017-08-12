package org.shipkit.internal.gradle.release;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.shipkit.gradle.exec.ShipkitExecTask;
import org.shipkit.internal.gradle.exec.ExecCommandFactory;
import org.shipkit.internal.gradle.git.GitSetupPlugin;
import org.shipkit.internal.gradle.util.TaskMaker;
import org.shipkit.internal.gradle.util.TaskSuccessfulMessage;

import static java.util.Arrays.asList;
import static org.shipkit.internal.gradle.exec.ExecCommandFactory.execCommand;

/**
 * Releasing from continuous integration (CI) builds.
 * Intended for root project.
 * <p>
 * Applies:
 * <ul>
 *     <li>{@link ReleasePlugin}</li>
 *     <li>{@link GitSetupPlugin}</li>
 * </ul>
 * Adds tasks:
 * <ul>
 *     <li>ciPerformRelease ({@link ShipkitExecTask})
 *     - convenience task to execute release using a single Gradle task in ci build</li>
 * </ul>
 */
public class CiReleasePlugin implements Plugin<Project> {

    private final static Logger LOG = Logging.getLogger(CiReleasePlugin.class);

    @Override
    public void apply(final Project project) {
        project.getPlugins().apply(ReleasePlugin.class);
        project.getPlugins().apply(GitSetupPlugin.class);

        /*
        Gradle task model does not make it easy to model releasing scenarios
          therefore we are forking invocations of Gradle tasks from within Gradle task.
        More details:
          We need to stop executing the release if it is not needed. Modelling this using standard task dependencies is not viable.
          We would have to make all tasks depend on 'release needed', which would cause this task to be executed every time we run any task.
          That does not make sense: you run "./gradlew clean" and Gradle would trigger 'releaseNeeded' task.
          Also, when release is not needed, we don't have clean Gradle API to stop the build, without failing it.
          Hence, we are pragmatic. We are forking Gradle from Gradle which seems hacky but we have no other viable choice.
        */
        TaskMaker.task(project, "ciPerformRelease", ShipkitExecTask.class, new Action<ShipkitExecTask>() {
            @Override
            public void execute(ShipkitExecTask task) {
                task.setDescription("Checks if release is needed. If so it will prepare for ci release and perform release.");
                task.getExecCommands().add(execCommand(
                        "Checking if release is needed", asList("./gradlew", ReleaseNeededPlugin.ASSERT_RELEASE_NEEDED_TASK), ExecCommandFactory.stopExecution()));
                task.getExecCommands().add(execCommand(
                        "Preparing working copy for the release", asList("./gradlew", GitSetupPlugin.CI_RELEASE_PREPARE_TASK)));
                task.getExecCommands().add(execCommand(
                        "Performing the release", asList("./gradlew", ReleasePlugin.PERFORM_RELEASE_TASK)));

                TaskSuccessfulMessage.logOnSuccess(task, "  Release " + project.getVersion() + " was shipped! Thank you for using Shipkit!");
            }
        });
    }
}
