#!groovy
node {
    stage('Checkout') {
        checkout scm
    }

    stage('Prepare') {
        sh 'make clean'
    }

    stage('Build') {
        sh 'make build'
    }

    stage('Test') {
        sh 'make test'
    }

    stage('Publish') {
        if (env.BRANCH_NAME == 'master') {
            sh 'make publish'
        }
    }
}
