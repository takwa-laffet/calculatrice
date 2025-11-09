pipeline {
  agent { label 'docker && linux' } // ou 'docker-agent' selon ton infra
  environment {
    // configurer ces credentials dans Jenkins (Credentials > Global credentials)
    DOCKER_REG = credentials('docker-registry-credentials-id') // username:password
    SONAR_TOKEN = credentials('sonar-token-id')
    SNYK_TOKEN  = credentials('snyk-token-id') // optional
    EMAIL_RECIPIENT = "takwa.laffet@esprit.tn"
    GITLEAKS_REPORT = "gitleaks-report.json"
  }
  options {
    timestamps()
    ansiColor('xterm')
    buildDiscarder(logRotator(daysToKeepStr:'14'))
  }
  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Pre-commit checks') {
      agent { docker { image 'python:3.11-slim' } }
      steps {
        sh '''
          pip install pre-commit
          pre-commit run --all-files || true
        '''
        // save output? pre-commit returns non-zero on failures; you may want it to fail build
      }
    }

    stage('Maven Build & Tests') {
      agent { docker { image 'maven:3.9.11-eclipse-temurin-17' } }
      steps {
        sh 'mvn -B clean test package'
        junit '**/target/surefire-reports/*.xml'
      }
      post {
        always { archiveArtifacts artifacts: 'target/*.jar', fingerprint: true }
      }
    }

    stage('SonarQube Scan') {
      when { expression { return env.SONAR_TOKEN != null } }
      agent { docker { image 'maven:3.9.11-eclipse-temurin-17' } }
      steps {
        withCredentials([string(credentialsId: 'sonar-token-id', variable: 'SONAR_TOKEN')]) {
          sh '''
            mvn -B org.sonarsource.scanner.maven:sonar-maven-plugin:3.11.1.2311:sonar \
              -Dsonar.projectKey=calculatrice \
              -Dsonar.host.url=http://SONARQUBE_URL:9000 \
              -Dsonar.login=${SONAR_TOKEN}
          '''
        }
      }
    }

    stage('Secret Scan (gitleaks)') {
      agent { docker { image 'zricethezav/gitleaks:latest' } }
      steps {
        sh """
          gitleaks detect --source . --report-path=${GITLEAKS_REPORT} || true
        """
        archiveArtifacts artifacts: "${GITLEAKS_REPORT}"
      }
    }

    stage('Build Docker Image') {
      steps {
        script {
          def imageTag = "${DOCKER_REG_USR ?: 'your-reg'}/calculatrice:${env.BUILD_NUMBER}"
          sh "docker build -t ${imageTag} ."
          withCredentials([usernamePassword(credentialsId: 'docker-registry-credentials-id', passwordVariable: 'DOCKER_PWD', usernameVariable: 'DOCKER_USER')]) {
            sh "echo ${DOCKER_PWD} | docker login -u ${DOCKER_USER} --password-stdin ${DOCKER_REG_HOST ?: 'docker.io'}"
            sh "docker push ${imageTag}"
          }
          env.IMAGE_TAG = imageTag
        }
      }
    }

    stage('Image Scan (Trivy)') {
      agent { docker { image 'aquasec/trivy:0.43.1' } }
      steps {
        sh "trivy image --format json -o trivy-report.json ${env.IMAGE_TAG} || true"
        archiveArtifacts artifacts: 'trivy-report.json'
      }
    }

    stage('DAST - OWASP ZAP') {
      agent { docker { image 'owasp/zap2docker-stable' } }
      steps {
        sh '''
          # Run app in background (if accessible) or run container and expose 8080 before DAST
          # Here we assume the app image is accessible as ${IMAGE_TAG}
          docker run -d --name app_scan -p 8081:8080 ${IMAGE_TAG}
          docker run --rm zaproxy/zap-baseline:latest -t http://host.docker.internal:8081 -r zap-report.html || true
          docker rm -f app_scan
        '''
        archiveArtifacts artifacts: 'zap-report.html'
      }
    }

    stage('Publish Reports & Email') {
      steps {
        publishHTML(target: [
          allowMissing: true,
          alwaysLinkToLastBuild: true,
          keepAll: true,
          reportDir: '.',
          reportFiles: 'zap-report.html',
          reportName: 'ZAP Report'
        ])  // optional
        emailext (
          subject: "CI Report - calculatrice - Build #${env.BUILD_NUMBER}",
          body: """Build: ${env.BUILD_URL}
                   Sonar: check SonarQube
                   Gitleaks: ${GITLEAKS_REPORT}
                   Trivy: trivy-report.json
                   ZAP: zap-report.html""",
          recipientProviders: [],
          to: "${EMAIL_RECIPIENT}",
          attachmentsPattern: "trivy-report.json, ${GITLEAKS_REPORT}, zap-report.html"
        )
      }
    }

    stage('Push Metrics to Pushgateway (optional)') {
      when { expression { return env.PUSHGATEWAY_URL != null } }
      steps {
        sh '''
          echo "ci_build_success 1" | curl --data-binary @- ${PUSHGATEWAY_URL}/metrics/job/ci_build/instance/${NODE_NAME}
        '''
      }
    }
  }

  post {
    always {
      cleanWs()
    }
    failure {
      mail to: "${EMAIL_RECIPIENT}",
           subject: "Build failed: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
           body: "See ${env.BUILD_URL} for details"
    }
  }
}
