#!/usr/bin/env groovy

pipeline {

        options { buildDiscarder(logRotator(numToKeepStr: '10')) }
        
        agent {
                label 'map-jenkins'
        }

        stages {
                stage('Init') {
                        steps {
                                echo "NODE_NAME = ${env.NODE_NAME}"
                                echo "Maven repo is " + mavenRepo()
                        }
                }
                
                stage('Build & Test') {
                        steps {
                                timestamps {
                                   timeout(time: 3, unit: 'HOURS') {
                                     sh "./gradlew -Dtest.ignoreFailures=true --continue --no-daemon --gradle-user-home " + gradleRepo() + " -Dmaven.repo.local=" + mavenRepo() + " clean build check -x :MAP-Agent:test -x :P2Protelis:test"
                                   }
                               }
                        }
                }

                stage('Gather tool results') {
                        // any post build steps that can fail need to be here to ensure that the email is sent out in the end
                        steps {
                          // ignore spotbugs and checkstyle issues in some of the simulation applications
                          recordIssues \
                              filters: [excludePackage('com.bbn.map.hifi.apps.filestore.*'), \
                                        excludePackage('com.bbn.map.FaceRecognition.*'),
                                        excludePackage('com.bbn.map.TestingHarness.*')], \
                              qualityGates: [[threshold: 1, type: 'TOTAL', unstable: true]], \
                              tools: [spotBugs(pattern: '**/build/reports/spotbugs/*.xml'), checkStyle(pattern: '**/build/reports/checkstyle/*.xml')]

                          junit testResults: "**/build/test-results/**/*.xml", keepLongStdio: true

                          recordIssues tool: taskScanner(excludePattern: 'gradle-repo/**,maven-repo/**', includePattern: '**/*.java,**/*.sh,**/*.py', highTags: 'FIXME,HACK', normalTags: 'TODO')
      
                          recordIssues tool: java()

                        }
                }


        } // stages
                
        post {
                always {
                        emailext recipientProviders: [[$class: 'DevelopersRecipientProvider'], [$class: 'CulpritsRecipientProvider']], 
                                        to: 'FILL-IN-EMAIL-ADDRESS',
                                        subject: '$DEFAULT_SUBJECT', 
                                        body: '''${PROJECT_NAME} - Build # ${BUILD_NUMBER} - ${BUILD_STATUS}

Changes:
${CHANGES}

Failed Tests:
${FAILED_TESTS, onlyRegressions=false}

Check console output at ${BUILD_URL} to view the full results.

Tail of Log:
${BUILD_LOG, maxLines=50}

'''

                } // always
        } // post

} // pipeline

def gradleRepo() {
 "${WORKSPACE}/gradle-repo"
}

def mavenRepo() {
 "${WORKSPACE}/maven-repo"
}
