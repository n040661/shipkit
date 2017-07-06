package org.shipkit.internal.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.shipkit.internal.gradle.git.CloneGitRepositoryTask
import spock.lang.Specification
import spock.lang.Subject

class CloneGitRepositoryTaskTest extends Specification {

    @Rule
    TemporaryFolder tmp = new TemporaryFolder()
    def tempFolder

    @Subject task = new ProjectBuilder().build().tasks.create("cloneGitRepositoryTask", CloneGitRepositoryTask)

    void setup(){
        tempFolder = tmp.newFolder()
    }

    def "clone a full repository"() {
        task.repositoryUrl = "url"
        task.targetDir = tempFolder

        expect:
        task.getCloneCommand() == ["git", "clone", "url", tempFolder.getAbsolutePath()]
    }

    def "clone a shallow repository"() {
        task.repositoryUrl = "url"
        task.targetDir = tempFolder
        task.numberOfCommitsToClone = 50

        expect:
        task.getCloneCommand() == ["git", "clone", "--depth", "50", "url", tempFolder.getAbsolutePath()]
    }
}
