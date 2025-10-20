PROJECT_ID=reverseproxy-474222
REGION=us-central1
REGISTRY=$(REGION)-docker.pkg.dev/$(PROJECT_ID)/reverseproxy

build:
	./gradlew :app:fatJar :testserver:fatJar

proxy:
	docker compose -f docker/local/compose.yml up --build reverse-proxy

dev:
	docker compose -f docker/local/compose.yml up --build

down:
	docker compose -f docker/local/compose.yml down

rebuild:
	./gradlew clean :app:fatJar :testserver:fatJar

logs:
	docker logs -f reverse-proxy

cloud_deploy:
	docker buildx build --platform linux/amd64 -t $(REGISTRY)/proxy:latest -f docker/deploy/Dockerfile.proxy --push .
	docker buildx build --platform linux/amd64 -t $(REGISTRY)/upstream:latest -f docker/deploy/Dockerfile.upstream --push .
	docker buildx build --platform linux/amd64 -t $(REGISTRY)/locust-master:latest -f docker/deploy/Dockerfile.locust-master --push .
	docker buildx build --platform linux/amd64 -t $(REGISTRY)/locust-worker:latest -f docker/deploy/Dockerfile.locust-worker --push .