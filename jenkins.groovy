pipeline{
    agent any
    tools{
        jdk 'jdk17'
    }
    environment {
        SCANNER_HOME=tool 'sonar-scanner'
    }
    stages {
        stage('clean workspace'){
            steps{
                cleanWs()
            }
        }
        stage('Checkout From Git'){
            steps{
                git branch: 'main', url: 'https://github.com/serdarbayram01/DotNet-monitoring.git'
            }
        }
        stage("Sonarqube Analysis "){
            steps{
                withSonarQubeEnv('sonar-server') {
                    sh ''' $SCANNER_HOME/bin/sonar-scanner -Dsonar.projectName=Dotnet-Webapp \
                    -Dsonar.projectKey=Dotnet-Webapp '''
                }
            }
        }
        stage("quality gate"){
           steps {
                script {
                    waitForQualityGate abortPipeline: false, credentialsId: 'sonar-cred'
                }
            }
        }
        stage("TRIVY File scan"){
            steps{
                sh "trivy fs . > trivy-fs_report.txt"
            }
        }
        
        stage("OWASP Dependency Check"){
            steps{
                dependencyCheck additionalArguments: '--scan ./ --format XML ', odcInstallation: 'DP-check'
                dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
            }
        }
        stage('Docker Build & Push') {
           steps {
               script {
                   withDockerRegistry(credentialsId: 'docker-cred', toolName: 'docker') {
                       sh "docker build -t dotnet-cicd ."
                       sh "docker tag dotnet-cicd serdarbayram/dotnet-cicd:latest"
                       sh "docker push serdarbayram/dotnet-cicd:latest"
                   }
               }
           }
       }
       stage("TRIVY"){
            steps{
                sh "trivy image serdarbayram/dotnet-cicd:latest > trivy.txt"
            }
        }
       stage('Deploy to Container') {
           steps {
               sh 'docker run -d --name dotnet-cicd -p 5000:5000 serdarbayram/dotnet-cicd:latest'
           }
       }
       stage('Deploy to k8s'){
            steps{
                dir('K8S') {
                  withKubeConfig(caCertificate: '', clusterName: '', contextName: '', credentialsId: 'k8s-cred', namespace: '', restrictKubeConfigAccess: false, serverUrl: '') {
                    sh 'kubectl apply -f deployment.yaml'
                   }
                }
            }
        }
   }
}
