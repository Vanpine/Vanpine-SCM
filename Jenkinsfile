pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  parameters {
    string(
      name: 'COMMIT',
      defaultValue: 'origin/main',
      description: '要部署的 Git commit 或分支引用'
    )
    string(
      name: 'DEPLOY_HOST',
      defaultValue: '172.17.0.1',
      description: '部署目标主机；生产环境建议在 Jenkins 全局环境变量中配置'
    )
  }

  stages {
    stage('Checkout') {
      steps {
        sh '''
          git fetch --prune --tags origin
          git checkout -f "$COMMIT"
        '''
      }
    }

    stage('Build frontend') {
      steps {
        dir('srm-admin-frontend') {
          sh 'npm ci'
          sh 'npm run build'
        }
      }
    }

    stage('Build backend') {
      steps {
        dir('srm-admin-backend') {
          sh 'mvn -B -ntp clean package'
        }
      }
    }

    stage('Deploy') {
      steps {
        withCredentials([
          sshUserPrivateKey(
            credentialsId: 'gsc-deploy-ssh-key',
            keyFileVariable: 'DEPLOY_KEY_FILE'
          )
        ]) {
          sh './deploy/ci-deploy.sh'
        }
      }
    }
  }

  post {
    always {
      cleanWs(deleteDirs: true, notFailBuild: true)
    }
  }
}
