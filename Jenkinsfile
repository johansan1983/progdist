// SuperChat CI/CD Pipeline
//
// Jenkins credentials to configure (Manage Jenkins → Credentials):
//   ghcr-credentials  → "Username with password"  (GitHub username + PAT with write:packages)
//   vps-ssh-key       → "SSH Username with private key"  (SSH key for your VPS)
//   vps-host          → "Secret text"  (VPS IP or hostname, e.g. 1.2.3.4)
//   vps-env-file      → "Secret file"  (.env for production — fill in from .env.example)
//
// Jenkins agent requirements:
//   - JDK 21 (Eclipse Temurin)  — configure via Global Tool Configuration → JDK
//   - Maven 3.9+                — configure via Global Tool Configuration → Maven  (name: Maven-3.9)
//   - Docker CLI (daemon running, Jenkins user in the docker group)
//   - git, ssh, scp
//
// VPS one-time setup:
//   git clone https://github.com/johansan1983/progdist.git /opt/superchat
//   docker login ghcr.io        # GitHub PAT with read:packages scope
//   docker volume create rabbitmq_data

pipeline {
    agent any

    tools {
        maven 'Maven-3.9'   // name must match Global Tool Configuration
        jdk   'JDK-21'      // name must match Global Tool Configuration
    }

    environment {
        GITHUB_OWNER = 'johansan1983'
        IMAGE_REPO   = "ghcr.io/${GITHUB_OWNER}/progdist"
        DEPLOY_USER  = 'ubuntu'
        DEPLOY_PATH  = '/opt/superchat'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
    }

    stages {

        // ── 1. Compute image tag from git commit SHA ──────────────────────────

        stage('Init') {
            steps {
                script {
                    env.IMAGE_TAG = sh(
                        script: 'git rev-parse --short HEAD',
                        returnStdout: true
                    ).trim()
                    echo "Commit: ${env.IMAGE_TAG}  Branch: ${env.BRANCH_NAME}"
                }
            }
        }

        // ── 2. Build & test all Spring Boot services in parallel ──────────────

        stage('Build & Test') {
            parallel {
                stage('config-server') {
                    steps {
                        dir('config-server') { sh 'mvn -B -ntp verify' }
                    }
                }
                stage('api-gateway') {
                    steps {
                        dir('api-gateway') { sh 'mvn -B -ntp verify' }
                    }
                }
                stage('chat-service') {
                    steps {
                        dir('chat-service') { sh 'mvn -B -ntp verify' }
                    }
                    post {
                        always {
                            junit allowEmptyResults: true,
                                  testResults: 'chat-service/target/surefire-reports/*.xml'
                        }
                    }
                }
                stage('user-service') {
                    steps {
                        dir('user-service') { sh 'mvn -B -ntp verify' }
                    }
                }
                stage('notification-service') {
                    steps {
                        dir('notification-service') { sh 'mvn -B -ntp verify' }
                    }
                }
                stage('worker-service') {
                    steps {
                        dir('worker-service') { sh 'mvn -B -ntp verify' }
                    }
                }
                stage('admin-service') {
                    steps {
                        dir('admin-service') { sh 'mvn -B -ntp verify' }
                    }
                }
                stage('moderation-service') {
                    steps {
                        dir('moderation-service') { sh 'mvn -B -ntp verify' }
                    }
                }
                stage('compliance-service') {
                    steps {
                        dir('compliance-service') { sh 'mvn -B -ntp verify' }
                    }
                }
            }
        }

        // ── 3. Build Docker images and push to GHCR in parallel ──────────────

        stage('Docker Build & Push') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'ghcr-credentials',
                    usernameVariable: 'GHCR_USER',
                    passwordVariable: 'GHCR_TOKEN'
                )]) {
                    sh 'echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin'

                    script {
                        def services = [
                            'config-server', 'api-gateway', 'chat-service', 'user-service',
                            'notification-service', 'worker-service', 'admin-service',
                            'moderation-service', 'compliance-service', 'frontend', 'admin-panel'
                        ]

                        // Build all images in parallel, then push.
                        // Each closure captures its own 'name'/'repo'/'tag' to avoid loop-variable aliasing.
                        parallel(services.collectEntries { svc ->
                            def name = svc
                            def repo = "${env.IMAGE_REPO}-${name}"
                            def tag  = "${repo}:${env.IMAGE_TAG}"
                            [(name): {
                                sh "docker build -t ${tag} ./${name}"
                                sh "docker push ${tag}"
                                if (env.BRANCH_NAME == 'main') {
                                    sh "docker tag  ${tag} ${repo}:latest"
                                    sh "docker push ${repo}:latest"
                                }
                            }]
                        })
                    }
                }
            }
            post {
                always {
                    sh 'docker logout ghcr.io || true'
                }
            }
        }

        // ── 4. Deploy to VPS (main branch only) ──────────────────────────────
        //
        // The VPS must already have the repo cloned at DEPLOY_PATH and docker
        // logged in to ghcr.io for pulls. Jenkins only needs SSH access.
        //
        // Compose stack used:
        //   docker-compose.yml          — base services + infrastructure
        //   docker-compose.prod.yml     — Caddy HTTPS overlay
        //   docker-compose.images.yml   — replaces build: with ghcr.io image: refs

        stage('Deploy') {
            when { branch 'main' }
            steps {
                withCredentials([
                    string(credentialsId: 'vps-host',     variable: 'VPS_HOST'),
                    file(credentialsId:   'vps-env-file', variable: 'ENV_FILE')
                ]) {
                    sshagent(['vps-ssh-key']) {
                        // Upload production .env (overrides any existing one on VPS)
                        sh 'scp -o StrictHostKeyChecking=no "$ENV_FILE" "$DEPLOY_USER@$VPS_HOST:$DEPLOY_PATH/.env"'

                        // Pull new images and restart containers atomically
                        sh '''
                            ssh -o StrictHostKeyChecking=no "$DEPLOY_USER@$VPS_HOST" \
                                "set -e
                                 cd $DEPLOY_PATH
                                 git pull origin main
                                 IMAGE_TAG=$IMAGE_TAG docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.images.yml pull
                                 IMAGE_TAG=$IMAGE_TAG docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.images.yml up -d --remove-orphans
                                 docker image prune -f"
                        '''
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Build ${env.IMAGE_TAG} deployed successfully."
        }
        failure {
            echo "Build ${env.IMAGE_TAG} FAILED — check stage logs above."
        }
        cleanup {
            cleanWs()
        }
    }
}
