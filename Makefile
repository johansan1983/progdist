COMPOSE ?= docker compose

.DEFAULT_GOAL := help

.PHONY: help bootstrap up down build rebuild restart ps logs logs-% status clean prune init-volumes wait-keycloak reseed-keycloak health urls

help: ## Mostrar esta ayuda
	@awk 'BEGIN {FS = ":.*##"; printf "\nTargets disponibles:\n"} /^[a-zA-Z_%-]+:.*?##/ { printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)

bootstrap: ## Desde WSL2 / Linux limpia: instala Docker y levanta el stack completo
	bash scripts/bootstrap.sh

init-volumes: ## Crear volumen externo de RabbitMQ (solo la primera vez)
	docker volume create rabbitmq_data || true

up: init-volumes ## Levantar todo el stack en background (compila si hace falta)
	$(COMPOSE) up -d --build

build: ## Recompilar imagenes sin levantar
	$(COMPOSE) build

rebuild: ## Recompilar sin cache y levantar
	$(COMPOSE) build --no-cache
	$(COMPOSE) up -d

down: ## Detener y eliminar contenedores (conserva volumenes)
	$(COMPOSE) down

restart: ## Reiniciar todos los contenedores
	$(COMPOSE) restart

ps: ## Listar contenedores del stack
	$(COMPOSE) ps

status: ps ## Alias de ps

logs: ## Ver logs en tiempo real de todos los servicios
	$(COMPOSE) logs -f --tail=100

logs-%: ## Ver logs de un servicio. Uso: make logs-chat-service
	$(COMPOSE) logs -f --tail=200 $*

shell-%: ## Abrir shell en un contenedor. Uso: make shell-chat-service
	$(COMPOSE) exec $* sh

restart-%: ## Reiniciar un servicio. Uso: make restart-worker-service
	$(COMPOSE) restart $*

wait-keycloak: ## Esperar a que Keycloak este listo (puede tardar 90-120s)
	@echo "Esperando a Keycloak en http://localhost:8080 ..."
	@until curl -sf http://localhost:8080/realms/superchat >/dev/null 2>&1; do sleep 3; printf "."; done
	@echo "\nKeycloak listo."

health: ## Verificar /actuator/health de los servicios Spring
	@for s in api-gateway:8090 chat-service:8082 user-service:8083 notification-service:8084 worker-service:8085 config-server:8888; do \
	  name=$${s%:*}; port=$${s#*:}; \
	  status=$$(curl -s -o /dev/null -w "%{http_code}" http://localhost:$$port/actuator/health || echo "down"); \
	  printf "%-25s %s\n" "$$name" "$$status"; \
	done

urls: ## Mostrar URLs principales del stack
	@echo "Frontend           http://localhost:3000"
	@echo "API Gateway        http://localhost:8090"
	@echo "Keycloak           http://localhost:8080  (admin/admin)"
	@echo "Config Server      http://localhost:8888"
	@echo "RabbitMQ Mgmt      http://localhost:15672 (superchat/superchat123)"
	@echo "MinIO Console      http://localhost:9001  (superchat/superchat123)"
	@echo "Prometheus         http://localhost:9090"
	@echo "Grafana            http://localhost:3001  (admin/admin)"
	@echo "Dozzle (logs)      http://localhost:9999"
	@echo "Portainer          http://localhost:9080"

reseed-keycloak: ## Recrear la base de Keycloak para forzar reimport del realm (usuarios alice/bob/...)
	@echo "Deteniendo keycloak y recreando su base de datos..."
	$(COMPOSE) stop keycloak
	$(COMPOSE) exec -T postgres psql -U superchat -d postgres -c "DROP DATABASE IF EXISTS keycloak;"
	$(COMPOSE) exec -T postgres psql -U superchat -d postgres -c "CREATE DATABASE keycloak;"
	$(COMPOSE) start keycloak
	@echo "Keycloak rearrancando — espera ~90s y luego prueba login con alice/demo123."

clean: ## Bajar el stack y borrar volumenes locales (conserva rabbitmq_data externo)
	$(COMPOSE) down -v

prune: ## Limpiar imagenes y redes Docker sin usar (cuidado)
	docker system prune -f
