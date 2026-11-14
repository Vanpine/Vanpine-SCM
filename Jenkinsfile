pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    // 构建与部署串行占用机器,超时避免卡死
    timeout(time: 30, unit: 'MINUTES')
  }

  parameters {
    string(
      name: 'COMMIT',
      defaultValue: 'origin/main',
      description: 'GitHub Actions 传入的提交 SHA;留空则构建 origin/main 最新提交'
    )
  }

  environment {
    WORKSPACE_ROOT = "${WORKSPACE}"
    // 服务器内存有限:1.7G,给前端与 Maven 各限 512m,避免构建期间 OOM
    NODE_OPTIONS   = '--max-old-space-size=512'
    MAVEN_OPTS     = '-Xms128m -Xmx512m'
    // 部署密钥在容器 ~/.ssh 内,与 ci-deploy.sh 默认值一致
    DEPLOY_KEY_FILE = '/var/jenkins_home/.ssh/id_ed25519'
    DEPLOY_HOST     = '172.17.0.1'
    GIT_TERMINAL_PROMPT = '0'
  }

  // 容器内无 node,使用 Jenkins 全局注册的 node22 工具
  tools {
    nodejs 'node22'
  }

  stages {
    stage('Checkout') {
      steps {
        sh '''
          set -e
          cd "$WORKSPACE_ROOT"
          git fetch --no-tags origin "+refs/heads/main:refs/remotes/origin/main"
          target="${COMMIT:-origin/main}"
          # 传入完整 SHA 时先确认该提交已拉到本地
          if ! git rev-parse --verify --quiet "$target^{commit}" >/dev/null; then
            git fetch --no-tags origin "$target"
          fi
          git checkout -f "$target"
          git rev-parse --short HEAD
        '''
      }
    }

    stage('Build frontend') {
      steps {
        dir('srm-admin-frontend') {
          sh 'node -v && npm -v'
          sh 'npm ci --registry=https://registry.npmmirror.com'
          sh 'npm run build'
        }
      }
    }

    stage('Build backend') {
      steps {
        dir('srm-admin-backend') {
          // 只构建网关与平台服务,与 ci-deploy.sh 部署范围一致
          sh 'mvn -B -ntp -pl gsc-gateway,gsc-service-platform -am clean package'
        }
      }
    }

    stage('Deploy') {
      steps {
        sh './deploy/ci-deploy.sh'
      }
    }
  }

  post {
    always {
      // 容器内未安装 workspace-cleanup 插件,手工清理工作区
      sh 'rm -rf "$WORKSPACE_ROOT"/{*,.[!.]*} 2>/dev/null || true'
    }
  }
}
