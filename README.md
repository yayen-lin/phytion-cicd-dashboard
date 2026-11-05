# Phytion CI/CI Dashboard App

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



```

Command Cheatsheet

```bash

# frontend
npx tsc -b     # type check
npm run lint   # lint check

# docker
docker compose stop      # stop Jenkins
docker compose start     # start it again
docker compose logs -f   # watch Jenkins' output
docker compose down      # remove the container

```