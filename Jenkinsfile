pipeline {
    agent any

    environment {
        SONAR_HOST = "http://192.168.13.8:9000"
        SLACK_CHANNEL = '#alerts'
        SLACK_CREDENTIALS = 'devsecops-slack'
        GIT_URL = "https://github.com/takwa-laffet/calculatrice.git"
        GIT_BRANCH = "main"
        SONAR_PROJECT_KEY = "Calculatrice"
        SNYK_BINARY = "/usr/local/bin/snyk"
        APP_URL = "http://192.168.13.8:8040"
        PROMETHEUS_PUSHGATEWAY = "http://192.168.13.8:9091"
        DOCKER_IMAGE_NAME = "calculator-app"
    }

    tools {
        maven 'Maven'
        jdk 'JDK17'
    }

    options {
        timeout(time: 90, unit: 'MINUTES')
        timestamps()
    }

    stages {
        stage('Init & Cleanup') {
            steps {
                script {
                    env.TODAY = sh(script: "date +%F", returnStdout: true).trim()
                    echo "Pipeline initialized on ${env.TODAY}"
                    sh '''
                        rm -f gitleaks-report.* trivy-report.* snyk-report.* \
                               nikto-report.* zap-report.* target/site/jacoco/**/* || true
                    '''
                }
            }
        }

        stage('Checkout Code') {
            steps {
                git branch: "${GIT_BRANCH}", url: "${GIT_URL}", credentialsId: 'git-token'
            }
        }

        stage('Build Spring Boot App') {
            steps {
                sh 'mvn clean package -DskipTests'
                archiveArtifacts artifacts: 'target/*.jar', allowEmptyArchive: true
            }
        }

        stage('Security Scans (Parallel)') {
            parallel {
                stage('Gitleaks') {
                    steps {
                        sh '''
                            gitleaks detect --source . --report-format json --report-path gitleaks-report.json || true
                            zip gitleaks-report.zip gitleaks-report.json
                        '''
                        archiveArtifacts artifacts: 'gitleaks-report.zip', allowEmptyArchive: true
                    }
                }

                stage('Trivy') {
                    steps {
                        sh '''
                            trivy fs . --exit-code 0 --severity HIGH,CRITICAL \
                                --format template --template "@/usr/local/share/trivy/templates/html.tpl" \
                                --output trivy-report.html
                            zip trivy-report.zip trivy-report.html
                        '''
                        archiveArtifacts artifacts: 'trivy-report.zip', allowEmptyArchive: true
                    }
                }

                stage('Snyk') {
                    steps {
                        withCredentials([string(credentialsId: 'snyk-token', variable: 'SNYK_TOKEN')]) {
                            sh """
                                ${SNYK_BINARY} auth \$SNYK_TOKEN
                                ${SNYK_BINARY} test --json > snyk-report.json || true
                                zip snyk-report.zip snyk-report.json
                            """
                        }
                        archiveArtifacts artifacts: 'snyk-report.zip', allowEmptyArchive: true
                    }
                }

                stage('OWASP ZAP') {
                    steps {
                        sh '''
                            docker run --rm --user root --network host \
                                -v $(pwd):/zap/wrk:rw \
                                ghcr.io/zaproxy/zaproxy:stable \
                                zap-baseline.py -t ${APP_URL} -r zap-report.html || true
                            zip zap-report.zip zap-report.html || true
                        '''
                        archiveArtifacts artifacts: 'zap-report.zip', allowEmptyArchive: true
                    }
                }

                stage('Nikto') {
                    steps {
                        sh """
                            nikto -h ${APP_URL} -o nikto-report.html -Format htm || true
                            zip nikto-report.zip nikto-report.html
                        """
                        archiveArtifacts artifacts: 'nikto-report.zip', allowEmptyArchive: true
                    }
                }
            }
        }

        stage('Test & Coverage - JaCoCo') {
            steps {
                sh 'mvn test jacoco:report'
                archiveArtifacts artifacts: 'target/site/jacoco/**/*', allowEmptyArchive: true
            }
        }

        stage('SAST - SonarQube') {
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    sh """mvn sonar:sonar \
                        -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                        -Dsonar.host.url=${SONAR_HOST} \
                        -Dsonar.login=\$SONAR_TOKEN"""
                }
            }
        }

        stage('Docker Build & Deploy App') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials',
                        usernameVariable: 'DOCKERHUB_USERNAME',
                        passwordVariable: 'DOCKERHUB_PASSWORD')]) {
                        sh """
                            docker network create devsecops-net || true
                            docker build -t ${DOCKER_IMAGE_NAME}:${BUILD_NUMBER} .
                            docker tag ${DOCKER_IMAGE_NAME}:${BUILD_NUMBER} \$DOCKERHUB_USERNAME/${DOCKER_IMAGE_NAME}:${BUILD_NUMBER}
                            echo \$DOCKERHUB_PASSWORD | docker login -u \$DOCKERHUB_USERNAME --password-stdin
                            docker push \$DOCKERHUB_USERNAME/${DOCKER_IMAGE_NAME}:${BUILD_NUMBER}
                            docker rm -f calculator-app || true
                            docker run -d --name calculator-app --network devsecops-net -p 8040:8040 ${DOCKER_IMAGE_NAME}:${BUILD_NUMBER}
                        """
                    }
                }
            }
        }

        stage('Push Metrics to Prometheus') {
            steps {
                script {
                    def gitleaksCount = sh(script: "jq -r '. | length' gitleaks-report.json || echo 0", returnStdout: true).trim()
                    def snykCount = sh(script: "jq -r '.vulnerabilities | length' snyk-report.json || echo 0", returnStdout: true).trim()
                    def trivyCount = sh(script: "grep -c 'CRITICAL\\|HIGH' trivy-report.html || echo 0", returnStdout: true).trim()
                    def zapCount = sh(script: "grep -c '<High|Medium|Low>' zap-report.html || echo 0", returnStdout: true).trim()
                    def niktoCount = sh(script: "grep -c 'OSVDB' nikto-report.html || echo 0", returnStdout: true).trim()

                    sh """
                    cat <<EOF | curl --data-binary @- ${PROMETHEUS_PUSHGATEWAY}/metrics/job/${JOB_NAME}/build/${BUILD_NUMBER}
jenkins_gitleaks_issues{job="${JOB_NAME}"} ${gitleaksCount}
jenkins_snyk_vulnerabilities{job="${JOB_NAME}"} ${snykCount}
jenkins_trivy_alerts{job="${JOB_NAME}"} ${trivyCount}
jenkins_zap_alerts{job="${JOB_NAME}"} ${zapCount}
jenkins_nikto_alerts{job="${JOB_NAME}"} ${niktoCount}
EOF
                    """
                }
            }
        }
    }

    post {
        success {
            slackSend(
                channel: env.SLACK_CHANNEL,
                message: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER} - Build & Security scans complete.\nLogs: ${BUILD_URL}",
                tokenCredentialId: env.SLACK_CREDENTIALS
            )
        }
        failure {
            slackSend(
                channel: env.SLACK_CHANNEL,
                message: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}\nCheck pipeline logs: ${BUILD_URL}",
                tokenCredentialId: env.SLACK_CREDENTIALS
            )
        }
        always {
            sh '''
                docker stop calculator-app || true
                docker rm calculator-app || true
            '''
            cleanWs()
        }
    }
}
