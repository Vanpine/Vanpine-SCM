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

# 只部署核心服务,其他微服务暂停以节省服务器资源
MODULES="gsc-gateway gsc-service-platform"
# 先业务服务,最后网关,避免网关路由到未就绪实例
RESTART_ORDER="gsc-service-platform gsc-gateway"

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

echo ">>> 重启后端服务"
for svc in $RESTART_ORDER; do
  ssh_remote "systemctl restart $svc"
  ssh_remote "systemctl is-active --quiet $svc" || { echo "服务 $svc 启动失败" >&2; exit 1; }
done

echo ">>> 部署完成"
