package org.mockito.release.internal.gradle;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.mockito.release.gradle.ReleaseConfiguration;
import org.mockito.release.gradle.contributors.ConfigureContributorsTask;
import org.mockito.release.internal.gradle.util.Specs;
import org.mockito.release.internal.gradle.util.TaskMaker;
import org.mockito.release.notes.contributors.Contributors;

import java.io.File;

import static org.mockito.release.internal.gradle.configuration.DeferredConfiguration.deferredConfiguration;

/**
 * Adds and configures tasks for getting contributor git user to GitHub user mappings.
 * Useful for release notes and pom.xml generation. Adds tasks:
 * <ul>
 *     <li>fetchRecentContributors - {@link RecentContributorsFetcherTask}</li>
 *     <li>fetchAllContributors - {@link AllContributorsFetcherTask}</li>
 *     <li>configureContributors - {@link AllContributorsFetcherTask}</li>
 * </ul>
 */
public class ContributorsPlugin implements Plugin<Project> {

    public final static String FETCH_ALL_CONTRIBUTORS_TASK = "fetchAllContributors";
    public final static String FETCH_RECENT_CONTRIBUTORS_TASK = "fetchRecentContributors";
    public final static String CONFIGURE_CONTRIBUTORS_TASK = "configureContributors";

    public void apply(final Project project) {
        final ReleaseConfiguration conf = project.getPlugins().apply(ReleaseConfigurationPlugin.class).getConfiguration();

        fetchRecentTask(project, conf);
        fetchAllTask(project, conf);
    }

    private void fetchRecentTask(final Project project, final ReleaseConfiguration conf) {
        project.getTasks().create(FETCH_RECENT_CONTRIBUTORS_TASK, RecentContributorsFetcherTask.class, new Action<RecentContributorsFetcherTask>() {
            @Override
            public void execute(final RecentContributorsFetcherTask task) {
                task.setGroup(TaskMaker.TASK_GROUP);
                task.setDescription("Fetch info about last contributors from GitHub and store it in file");

                final String toRevision = "HEAD";
                task.setToRevision(toRevision);

                deferredConfiguration(project, new Runnable() {
                    public void run() {
                        String fromRevision = "v" + conf.getPreviousReleaseVersion();
                        File contributorsFile = lastContributorsFile(project, fromRevision, toRevision);

                        task.setReadOnlyAuthToken(conf.getGitHub().getReadOnlyAuthToken());
                        task.setRepository(conf.getGitHub().getRepository());
                        task.setFromRevision(fromRevision);
                        task.setOutputFile(contributorsFile);
                    }
                });
            }
        });
    }

    private void fetchAllTask(final Project project, final ReleaseConfiguration conf) {
        final AllContributorsFetcherTask fetcher = project.getTasks().create(FETCH_ALL_CONTRIBUTORS_TASK, AllContributorsFetcherTask.class, new Action<AllContributorsFetcherTask>() {
            @Override
            public void execute(final AllContributorsFetcherTask task) {
                task.setGroup(TaskMaker.TASK_GROUP);
                task.setDescription("Fetch info about all project contributors from GitHub and store it in file");
                task.setOutputFile(new File(project.getBuildDir(), "release-tools/project-contributors.json"));

                deferredConfiguration(project, new Runnable() {
                    @Override
                    public void run() {
                        task.setReadOnlyAuthToken(conf.getGitHub().getReadOnlyAuthToken());
                        task.setRepository(conf.getGitHub().getRepository());
                        task.setEnabled(conf.getTeam().getContributors().isEmpty());
                    }
                });
            }
        });

        TaskMaker.task(project, CONFIGURE_CONTRIBUTORS_TASK, ConfigureContributorsTask.class, new Action<ConfigureContributorsTask>() {
            public void execute(ConfigureContributorsTask t) {
                t.setDescription("Sets contributors to 'releasing.team.contributors' based on" +
                        " the serialized contributors data fetched earlier by " + FETCH_ALL_CONTRIBUTORS_TASK);
                t.dependsOn(fetcher);
                t.setContributorsData(fetcher.getOutputFile());
                t.setReleaseConfiguration(conf);
                t.onlyIf(Specs.fileExists(fetcher.getOutputFile()));
            }
        });
    }

    private File lastContributorsFile(Project project, String fromRevision, String toRevision) {
        String contributorsFileName = Contributors.getLastContributorsFileName(
                project.getBuildDir().getAbsolutePath(), fromRevision, toRevision);
        return new File(contributorsFileName);
    }
}


