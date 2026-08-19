#!/usr/bin/env bash
# One-time VM bootstrap (Ubuntu 22.04/24.04). Run as root on a fresh VM (Hetzner CX22 / Oracle A1 / EC2 t3.small+, >= 4GB RAM):
#   curl -fsSL https://raw.githubusercontent.com/<you>/<repo>/main/infra/vm-bootstrap.sh | bash
# Creates user "deploy" with docker access; GitHub Actions SSHes in as that user (secret DEPLOY_USER=deploy).
set -euo pipefail
apt-get update -y && apt-get install -y ca-certificates curl ufw
curl -fsSL https://get.docker.com | sh
id -u deploy >/dev/null 2>&1 || useradd -m -s /bin/bash deploy
usermod -aG docker deploy
mkdir -p /home/deploy/.ssh && chmod 700 /home/deploy/.ssh
# paste the PUBLIC half of the key you put in the DEPLOY_SSH_KEY secret:
touch /home/deploy/.ssh/authorized_keys && chmod 600 /home/deploy/.ssh/authorized_keys && chown -R deploy:deploy /home/deploy/.ssh
ufw allow OpenSSH && ufw allow 80/tcp && ufw --force enable     # ONLY 22 + 80 are public; author/publish stay on 127.0.0.1
echo "Done. Add your public key to /home/deploy/.ssh/authorized_keys, then run the GitHub 'deploy' workflow."
