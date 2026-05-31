# Deploying SuperChat

SuperChat runs entirely in Docker containers, so it deploys **the same way on every OS**.
The host needs only **Docker** — there is nothing to install per-service, no JDK, no Maven, no databases.

> **TL;DR** — install Docker, then run one command:
>
> | OS | Command (from the project folder) |
> |---|---|
> | **Windows** (PowerShell) | `.\deploy.ps1` |
> | **Linux / macOS** | `./deploy.sh` |
>
> First boot builds the images and starts everything. Wait ~90–120 s for Keycloak, then open <http://localhost:3000>.

---

## Requirements

| | Minimum | Comfortable |
|---|---|---|
| RAM | 8 GB | 16 GB |
| Disk | 10 GB free | 20 GB |
| CPU | 4 cores | 8 cores |
| Software | Docker Engine + Compose v2 | same |

The stack runs ~20 containers (9 Java services + Keycloak, Postgres, RabbitMQ, Redis, MinIO, Prometheus, Grafana, …). Each Java service is capped at 1 GB.

---

## Step 1 — Install Docker

### Windows 10/11
1. Download **Docker Desktop**: <https://www.docker.com/products/docker-desktop/>
2. Install, reboot if asked, and launch it (it enables the WSL 2 backend automatically).
3. Confirm it works — open **PowerShell** and run:
   ```powershell
   docker version
   docker compose version
   ```

### Ubuntu / Debian **and** RHEL / Fedora / CentOS / Rocky / AlmaLinux
The official Docker convenience script supports all of these distros:
```bash
curl -fsSL https://get.docker.com | sh
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"     # run docker without sudo
newgrp docker                        # apply the group now (or just log out/in)
docker version
```

> macOS: install Docker Desktop from the same link as Windows.

---

## Step 2 — Get the code

```bash
git clone https://github.com/johansan1983/progdist.git
cd progdist
```
(Or copy the project folder onto the machine and `cd` into it.)

---

## Step 3 — Deploy (one command)

**Windows (PowerShell):**

```powershell
.\deploy.ps1
```

If you see *"la ejecución de scripts está deshabilitada"* / *"running scripts is disabled on this system"*, PowerShell's default policy is blocking local scripts. Either run it once without changing anything:

```powershell
powershell -ExecutionPolicy Bypass -File .\deploy.ps1
```

or allow your own local scripts permanently (no admin required), then run `.\deploy.ps1`:

```powershell
Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy RemoteSigned
```

**Linux / macOS:**
```bash
chmod +x deploy.sh        # first time only
./deploy.sh
```

The script automatically:
1. Verifies Docker is running.
2. **Generates strong secrets** (`AES_ENCRYPTION_KEY`, `INTERNAL_API_TOKEN`) into a private `.env` — only on the first run, never overwritten.
3. Renders the Keycloak realm for your host.
4. Creates the `rabbitmq_data` volume.
5. Builds the images and starts the stack.

First build downloads dependencies and can take several minutes. Subsequent starts are seconds.

---

## Step 4 — First login

1. Wait for Keycloak (first boot ~90–120 s). Watch it:
   ```bash
   docker compose logs -f keycloak     # Ctrl-C to stop watching
   ```
2. Open the **Keycloak admin console**: <http://localhost:8080> — log in `admin` / `admin`.
3. Switch to the **`superchat`** realm (top-left dropdown) → **Users** → **Add user**.
   - Set a username, save, then **Credentials** → set a password, turn **Temporary** off.
   - (For an admin user, **Role mapping** → assign `org_admin` or `platform_admin`.)
4. Open the app: <http://localhost:3000> and log in with that user.

---

## What you get

| URL | Service |
|---|---|
| <http://localhost:3000> | Chat frontend |
| <http://localhost:3002> | Admin panel |
| <http://localhost:8080> | Keycloak (admin / admin) |
| <http://localhost:3001> | Grafana dashboards (admin / admin) |
| <http://localhost:9090> | Prometheus |
| <http://localhost:15672> | RabbitMQ management (superchat / superchat123) |
| <http://localhost:8090> | API Gateway |

---

## Access from other machines (LAN)

By default the stack only answers on `localhost`. To reach it from phones/other PCs, pass the host's LAN IP so Keycloak issues correct login redirects:

```bash
./deploy.sh --host 10.0.0.50          # Linux/macOS
.\deploy.ps1 -PublicHost 10.0.0.50    # Windows
```

Then browse to `http://10.0.0.50:3000`. Open ports 3000, 3002, 8080, 8090 on the host firewall.

---

## Production with a domain + HTTPS

If the machine has a public domain pointing at it, the production profile adds a **Caddy** reverse proxy that auto-issues Let's Encrypt TLS certificates:

```bash
# in .env:  PUBLIC_DOMAIN=chat.example.com   and   ACME_EMAIL=admin@example.com
make up-prod        # Linux with make
# or directly:
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

DNS for the domain must resolve to the host before Caddy can issue the certificate.

---

## Day-2 operations

| Task | Command |
|---|---|
| See status | `docker compose ps` |
| Tail logs | `docker compose logs -f` (or `… -f chat-service`) |
| Stop (keep data) | `docker compose down` |
| Start again | `docker compose up -d` |
| Update to new code | `git pull` then re-run the deploy script |
| Rebuild one service | `docker compose up -d --build chat-service` |
| Wipe everything (incl. data) | `docker compose down -v` then `docker volume rm rabbitmq_data` |

**Backups** — application data lives in Docker volumes (`progdist_postgres_data`, `rabbitmq_data`, `minio_data`). Back them up with `docker run --rm -v progdist_postgres_data:/data -v "$PWD":/backup alpine tar czf /backup/postgres.tgz /data`.

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `running scripts is disabled` / `ejecución de scripts está deshabilitada` (Windows) | PowerShell policy blocks local scripts. Run `powershell -ExecutionPolicy Bypass -File .\deploy.ps1`, or `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` once. |
| `docker: command not found` | Docker isn't installed / not on PATH — redo Step 1. |
| `permission denied … docker.sock` (Linux) | Run `sudo usermod -aG docker $USER` then log out/in. |
| `port is already allocated` | Another process uses 3000/8080/5432/… — stop it, or change the published port in `docker-compose.yml`. |
| Login fails / redirect to `localhost` from another PC | You didn't pass `--host <LAN-IP>`. Re-run the deploy script with it. |
| Keycloak won't start / login 500s | It's still booting — wait up to 2 min: `docker compose logs -f keycloak`. |
| A Java service is `unhealthy` | Low RAM. Close other apps or raise the host's memory; each service is capped at 1 GB. |
| Build fails “insufficient memory” | The host ran out of RAM/paging during the image build — free memory and retry. |

Everything else is documented in [CLAUDE.md](CLAUDE.md) (architecture) and [ENTERPRISE.md](ENTERPRISE.md) (multi-tenant, RBAC, compliance, operations runbook).
