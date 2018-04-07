#!/usr/bin/groovy

/**
    this section of the pipeline executes on the master, which has a lot of useful variables that we can leverage to configure our pipeline
**/
node (''){
    env.DEV_PROJECT = env.OPENSHIFT_BUILD_NAMESPACE.replace('ci-cd','dev')
    env.DEMO_PROJECT = env.OPENSHIFT_BUILD_NAMESPACE.replace('ci-cd','demo')
    
    env.CI_CD_PROJECT = env.OPENSHIFT_BUILD_NAMESPACE
    

    // this value should be set to the root directory of your source code within the git repository.
    // if the root of the source is the root of the repo, leave this value as ""
    env.SOURCE_CONTEXT_DIR = ""
    // this value is relative to env.SOURCE_CONTEXT_DIR, and should be set to location where mvn will build the uber-jar
    env.UBER_JAR_CONTEXT_DIR = "target/"

    // this value will be passed to the mvn command - it should include switches like -D and -P
    env.MVN_COMMAND = "clean deploy -D hsql"

     /**
    these are used to configure which repository maven deploys
    the ci-cd starter will create a nexus that has this repos available
    **/
    env.MVN_SNAPSHOT_DEPLOYMENT_REPOSITORY = "nexus::default::http://nexus:8081/repository/maven-snapshots"
    env.MVN_RELEASE_DEPLOYMENT_REPOSITORY = "nexus::default::http://nexus:8081/repository/maven-releases"

    /**
    this value assumes the following convention, which is enforced by our default templates:
    - there are two build configs: one for s2i, one for this pipeline
    - the buildconfig for this pipeline is called my-app-name-pipeline
    - both buildconfigs are in the same project
    **/
    env.APP_NAME = "${env.JOB_NAME}".replaceAll(/-?${env.PROJECT_NAME}-?/, '').replaceAll(/-?pipeline-?/, '').replaceAll('/','')

    // these are defaults that will help run openshift automation
    env.OCP_API_SERVER = "${env.OPENSHIFT_API_URL}"
    env.OCP_TOKEN = readFile('/var/run/secrets/kubernetes.io/serviceaccount/token').trim()
}


/**
    this section of the pipeline executes on a custom mvn build slave.
    you should not need to change anything below unless you need new stages or new integrations (e.g. Cucumber Reports or Sonar)
**/
node('jenkins-slave-mvn') {

  stage('Checkout from GitHub') {
    checkout scm
      
  }

  dir ("${env.SOURCE_CONTEXT_DIR}") {
    stage('Connect to Local Nexus Secure Binary Repository '){
      // verify nexus is up or the build will fail with a strange error
      openshiftVerifyDeployment ( 
        apiURL: "${env.OCP_API_SERVER}", 
        authToken: "${env.OCP_TOKEN}", 
        depCfg: 'nexus', 
        namespace: "${env.CI_CD_PROJECT}", 
        verifyReplicaCount: true,
        waitTime: '3', 
        waitUnit: 'min'
      ) 
    }
      
    stage('Static Analysis Security Test') {
        withSonarQubeEnv('SAST') {
            sh 'mvn clean compile -DskipTests sonar:sonar'
        } // SonarQube taskId is automatically attached to the pipeline context
    }
    
    stage('Manual Promotion Gate') {
        slackSend color: 'warning', message: 'OpenShift Jenkins Pipeline needs you to approve SAST results from http://sonarqube-labs-ci-cd.34.217.23.58.nip.io/dashboard?id=com.rhc%3Aautomation-api'
        input "Build Application?" 
    }

    /**
    stage("Promotion Gate"){
        timeout(time: 2, unit: 'MINUTES') { 
            def qg = waitForQualityGate() 
            if (qg.status != 'OK') {
                error "Pipeline aborted due to quality gate failure: ${qg.status}"
            }
        }
    } 
    **/
      
    stage('Generate Artifacts in Nexus') {        
      // TODO - introduce a variable here
      sh "mvn ${env.MVN_COMMAND} -D hsql -DaltDeploymentRepository=${MVN_SNAPSHOT_DEPLOYMENT_REPOSITORY}"
    }

    // assumes uber jar is created
    stage('Build Container Image') {
      sh "oc start-build ${env.APP_NAME} --from-dir=${env.UBER_JAR_CONTEXT_DIR} --follow"
    }
  }
    
  stage('Scan Container Image') {
    slackSend color: 'warning', message: 'OpenShift Jenkins Pipeline needs you to approve SCAN results from NEED_LINK_INCLUDED'
    input "Promote Image for Deployment to Dev?"
  }
    
  // no user changes should be needed below this point
  stage ('Deploy to Dev') {

    openshiftTag (apiURL: "${env.OCP_API_SERVER}", authToken: "${env.OCP_TOKEN}", destStream: "${env.APP_NAME}", destTag: 'latest', destinationAuthToken: "${env.OCP_TOKEN}", destinationNamespace: "${env.DEV_PROJECT}", namespace: "${env.CI_CD_PROJECT}", srcStream: "${env.APP_NAME}", srcTag: 'latest')

    openshiftVerifyDeployment (apiURL: "${env.OCP_API_SERVER}", authToken: "${env.OCP_TOKEN}", depCfg: "${env.APP_NAME}", namespace: "${env.DEV_PROJECT}", verifyReplicaCount: true)
      
    slackSend color: 'good', message: 'OpenShift Jenkins Pipeline needs you to approve promotion of build at https://ec2-34-217-23-58.us-west-2.compute.amazonaws.com:8443/console/project/labs-ci-cd/browse/pipelines/java-app-pipeline?tab=history'
  }
    
  stage('Create Vulnerability Assessment Pod') {
    node('jenkins-slave-zap') {
        stage('Scan Web Application') {
            dir('/zap') {
                def retVal = sh returnStatus: true, script: '/zap/zap-baseline.py -r baseline.html -t http://java-app-labs-dev.34.217.23.58.nip.io/'
                publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: true, reportDir: '/zap/wrk', reportFiles: 'baseline.html', reportName: 'ZAP Baseline Scan', reportTitles: 'ZAP Baseline Scan'])
                echo "Return value is: ${retVal}"
                slackSend color: 'warning', message: 'OpenShift Jenkins Pipeline needs you to approve VA results from NEED_LINK_INCLUDED'
              }
          }
      }
  }
    
  stage ('Deploy to Demo') {


    openshiftTag (apiURL: "${env.OCP_API_SERVER}", authToken: "${env.OCP_TOKEN}", destStream: "${env.APP_NAME}", destTag: 'latest', destinationAuthToken: "${env.OCP_TOKEN}", destinationNamespace: "${env.DEMO_PROJECT}", namespace: "${env.DEV_PROJECT}", srcStream: "${env.APP_NAME}", srcTag: 'latest')

    openshiftVerifyDeployment (apiURL: "${env.OCP_API_SERVER}", authToken: "${env.OCP_TOKEN}", depCfg: "${env.APP_NAME}", namespace: "${env.DEMO_PROJECT}", verifyReplicaCount: true)


  }

}
