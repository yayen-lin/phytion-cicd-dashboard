# Phytion CI/CI Dashboard App

### Start the App

```bash

# start the service, frontend
cd frontend
npm install
npm run dev # http://localhost:5173

# start the service, backend
cd backend
./gradlew bootRun # http://localhost:8080

# start the image 
cd jenkins
docker compose up -d # http://localhost:8081

# check the app status
curl localhost:8090/actuator/health/readiness
curl localhost:8090/actuator/health/liveness
curl localhost:8091/actuator/health/readiness
curl localhost:8091/actuator/health/liveness

```

### Command Cheatsheet

```bash

# frontend
npx tsc -b     # type check
npm run lint   # lint check

# docker
docker compose stop      # stop Jenkins
docker compose start     # start it again
docker compose logs -f   # watch Jenkins' output
docker compose down      # remove the container

# kube control
kubectl create namespace dev
kubectl create namespace staging
kubectl create namespace prod

# check namespace
kubectl get namespaces

# check whats running
kubectl get pods -n dev -w

```

### Connecting Pieces
Kubernetes & Helm
- Kubernetes runs containers.
- `kubectl` is how you talk to Kubernetes from the terminal (create things, see what's running, read logs, delete things, etc.)
- Helm tells Kubernetes what should run and where, and Kubernetes pulls images from the registry and starts one or more 
  containers from it in the chosen namespace.
- Helm deploys an app to Kubernetes.
  - An app needs several Kubernetes config files:
    - A Deployment for the containers
    - A Service for the network address
  - Helm bundles them into a chart with placeholders, fills them in from a values file and applies them.
- A cluster is one kubernetes installation, which is the machine that it runs on (in our case docker desktop)
- Environments are namespaces in a cluster

Docker
- Image is a release version of an app
- Container is a running copy of an image
- Images live in either registry (where Jenkins pushes them on release), or Docker's local image cache
- Registry is storage that holds versions (e.g., 1.0.1, 1.0.2, 1.0.3)

Jenkins
- Build: Jenkins runs `docker build`, and Docker (in our case Docker Desktop) builds the image.
- Push: Jenkins runs `docker push`, and Docker uploads the image to the registry.
- Deploy: Jenkins run `helm upgrade`, and Helm tells Kubernetes what to run.