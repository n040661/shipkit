package org.shipkit.internal.gradle.release.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.shipkit.gradle.release.ReleaseNeededTask;
import org.shipkit.internal.util.ArgumentValidation;
import org.shipkit.internal.util.EnvVariables;

public class ReleaseNeeded {

    private final static Logger LOG = Logging.getLogger(ReleaseNeededTask.class);

    //We are using environment variable instead of system property or Gradle project property here
    //It's easier to configure Travis CI matrix builds using env variables
    //For reference, see the ".travis.yml" of Mockito project
    private final static String SKIP_RELEASE_ENV = "SKIP_RELEASE";
    private final static String SKIP_RELEASE_KEYWORD = "[ci skip-release]";
    private static final String SKIP_COMPARE_PUBLICATIONS = "[ci skip-compare-publications]";

    public boolean releaseNeeded(ReleaseNeededTask task) {
        return releaseNeeded(task, new EnvVariables());
    }

    boolean releaseNeeded(ReleaseNeededTask task, EnvVariables envVariables) {
        ReleaseNeed releaseNeed = releaseNeed(task, envVariables);

        boolean releaseNeeded = releaseNeed.needed;
        String message = releaseNeed.explanation;

        if (!releaseNeeded && task.isExplosive()) {
            throw new GradleException(message);
        } else {
            LOG.lifecycle(message);
        }
        return releaseNeeded;
    }

    private ReleaseNeed releaseNeed(ReleaseNeededTask task, EnvVariables envVariables) {
        boolean skipEnvVariable = envVariables.getNonEmptyEnv(SKIP_RELEASE_ENV) != null;
        LOG.lifecycle("  Environment variable {} present: {}", SKIP_RELEASE_ENV, skipEnvVariable);

        boolean commitMessageEmpty = task.getCommitMessage() == null || task.getCommitMessage().trim().isEmpty();
        boolean skippedByCommitMessage = !commitMessageEmpty && task.getCommitMessage().contains(SKIP_RELEASE_KEYWORD);
        boolean skipComparePublications = !commitMessageEmpty && task.getCommitMessage().contains(SKIP_COMPARE_PUBLICATIONS);
        LOG.lifecycle("  Commit message to inspect for keywords '{}' and '{}': {}",
            SKIP_RELEASE_KEYWORD, SKIP_COMPARE_PUBLICATIONS,
            commitMessageEmpty ? "<unknown commit message>" : "\n" + task.getCommitMessage());

        boolean releasableBranch = task.getBranch() != null && task.getBranch().matches(task.getReleasableBranchRegex());
        LOG.lifecycle("  Current branch '{}' matches '{}': {}", task.getBranch(), task.getReleasableBranchRegex(), releasableBranch);

        LOG.lifecycle("  Determine if we need a release based on: " +
            "\n    - skip by env variable: " + skipEnvVariable +
            "\n    - skip by commit message: " + skippedByCommitMessage +
            "\n    - is pull request build: " + task.isPullRequest() +
            "\n    - is releasable branch: " + releasableBranch);

        if (releasableBranch) {
            if (skippedByCommitMessage) {
                return ReleaseNeed.of(false, " Skipping release due to skip release keyword in commit message.");
            } else if (skipEnvVariable) {
                return ReleaseNeed.of(false, " Skipping release due to skip release env variable.");
            } else if (task.isPullRequest()) {
                return ReleaseNeed.of(false, " Skipping release due to is PR.");
            } else if (skipComparePublications) {
                return ReleaseNeed.of(true, " Releasing due to '" + SKIP_COMPARE_PUBLICATIONS + "' keyword in commit message.");
            } else {
                ComparisonResults results = new ComparisonResults(task.getComparisonResults());
                boolean publicationsIdentical = results.areResultsIdentical();
                LOG.lifecycle(results.getDescription());

                if (publicationsIdentical) {
                    return ReleaseNeed.of(false, " Skipping release because publications are identical.");
                }
                return ReleaseNeed.of(true, " Releasing because publication changed.");
            }
        } else {
            return ReleaseNeed.of(false, " Skipping release because we are not on a releasable branch.");
        }
    }

    static class ReleaseNeed {
        private final boolean needed;
        private final String explanation;

        private ReleaseNeed(boolean needed, String explanation) {
            ArgumentValidation.notNull( explanation, "explanation");
            this.needed = needed;
            this.explanation = explanation;
        }

        static ReleaseNeed of(boolean needed, String explanation) {
            return new ReleaseNeed(needed, explanation);
        }

        @Override
        public String toString() {
            return "ReleaseNeed{needed=" + needed + ", explanation='" + explanation + '\'' + '}';
        }
    }
}
