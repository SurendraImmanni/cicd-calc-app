pipeline {
    agent any

    environment {
        ACCOUNT_ID = '623592712233'
		AWS_REGION = 'ap-south-1'            // Change your AWS region if needed
        REPO_NAME = 'myapp/repo'             // Your ECR repository name
        IMAGE_TAG = 'latest'
    }

    stages {
        stage('Fetch Code from GitHub') {
            steps {
                echo "Cloning code from GitHub..."
                git branch: 'main', url: 'https://github.com/SurendraImmanni/cicd-calc-app.git'
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    echo "Building Docker image..."
                    sh "sudo usermod -aG docker jenkins || true"
                    sh "newgrp docker || true"
                    sh "docker build -t ${REPO_NAME}:${IMAGE_TAG} ."
                }
            }
        }

        stage('Login to AWS ECR') {
            steps {
                script {
                    echo "Logging in to AWS ECR..."
                    sh "aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin $(aws sts get-caller-identity --query Account --output text).dkr.ecr.${AWS_REGION}.amazonaws.com"
                }
            }
        }

        stage('Push Docker Image to ECR') {
            steps {
                script {
                    echo "Pushing image to ECR..."
                    ACCOUNT_ID = sh(script: "aws sts get-caller-identity --query Account --output text", returnStdout: true).trim()
                    ECR_URL = "${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${REPO_NAME}"

                    sh "docker tag ${REPO_NAME}:${IMAGE_TAG} ${ECR_URL}:${IMAGE_TAG}"
                    sh "docker push ${ECR_URL}:${IMAGE_TAG}"
                }
            }
        }

        stage('Deploy Container in EC2') {
            steps {
                script {
                    echo "Deploying container on EC2..."
                    ACCOUNT_ID = sh(script: "aws sts get-caller-identity --query Account --output text", returnStdout: true).trim()
                    ECR_URL = "${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${REPO_NAME}"

                    sh "docker pull ${ECR_URL}:${IMAGE_TAG}"

                    // Stop old container if exists
                    sh "docker ps -q --filter 'name=myapp-container' | grep -q . && docker stop myapp-container && docker rm myapp-container || true"

                    // Run new container
                    sh "docker run -d --name myapp-container -p 8080:8080 ${ECR_URL}:${IMAGE_TAG}"
                }
            }
        }
    }

    post {
        success {
            echo '✅ Deployment successful!'
        }
        failure {
            echo '❌ Deployment failed!'
        }
    }
}
