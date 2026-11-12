#!/usr/bin/env bash
# Jenkins 部署脚本:在 Jenkins 容器内执行,通过 SSH 部署到宿主机。
# 由 Jenkins "Vanpine SRM" job 在构建后调用。
set -euo pipefail

HOST="${DEPLOY_HOST:-172.17.0.1}"
KEY_FILE="${DEPLOY_KEY_FILE:-/var/jenkins_home/.ssh/id_ed25519}"

ssh_remote() {
  ssh \
    -i "$KEY_FILE" \
    -o IdentitiesOnly=yes \
    -o StrictHostKeyChecking=no \
    -o BatchMode=yes \
    "root@${HOST}" "$@"
}
VERSION="0.0.1-SNAPSHOT"

WORKSPACE="${WORKSPACE:?WORKSPACE 未设置}"
BACKEND="$WORKSPACE/srm-admin-backend"
FRONTEND="$WORKSPACE/srm-admin-frontend"
AI="$WORKSPACE/srm-ai-service"

MODULES="gsc-gateway gsc-service-platform gsc-service-supplier gsc-service-trade gsc-service-delivery gsc-service-ai"
# 先业务服务,最后网关,避免网关路由到未就绪实例
RESTART_ORDER="gsc-service-platform gsc-service-supplier gsc-service-trade gsc-service-delivery gsc-service-ai gsc-gateway"

echo ">>> 部署后端 jar 到 /opt/gsc"
cd "$BACKEND"
FILES=""
for m in $MODULES; do
  FILES="$FILES $m/target/$m-$VERSION.jar"
done
tar czf - $FILES | ssh_remote "tar xzf - -C /opt/gsc --strip-components=2"

echo ">>> 部署前端 dist 到 /var/www/gsc"
cd "$FRONTEND"
tar czf - dist | ssh_remote "rm -rf /var/www/gsc/* && tar xzf - -C /var/www/gsc --strip-components=1"

echo ">>> 部署 AI 服务代码到 /opt/srm-ai-service"
cd "$AI"
tar czf - app requirements.txt | ssh_remote "tar xzf - -C /opt/srm-ai-service"

echo ">>> 准备 AI 依赖"
ssh_remote "cd /opt/srm-ai-service && { test -d .venv || python3 -m venv .venv; } && .venv/bin/pip install -q -r requirements.txt"

echo ">>> 重启后端服务"
for svc in $RESTART_ORDER; do
  ssh_remote "systemctl restart $svc"
  ssh_remote "systemctl is-active --quiet $svc" || { echo "服务 $svc 启动失败" >&2; exit 1; }
done

echo ">>> 重启 AI 服务"
ssh_remote "systemctl restart srm-ai-service"
ssh_remote "systemctl is-active --quiet srm-ai-service" || { echo "srm-ai-service 启动失败" >&2; exit 1; }

echo ">>> 部署完成"
