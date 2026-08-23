FROM clojure:temurin-21-tools-deps-alpine AS base

RUN apk add --no-cache nodejs npm

WORKDIR /app
COPY deps.edn build.clj ./
RUN clojure -P && clojure -P -A:dev:build

COPY package.json package-lock.json* ./
RUN npm ci

COPY shadow-cljs.edn tailwind.config.js postcss.config.js ./
COPY src/ src/
COPY resources/ resources/

RUN npx shadow-cljs release app
RUN npx tailwindcss -i resources/public/css/input.css -o resources/public/css/output.css --minify
RUN clojure -T:build uber

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=base /app/target/ecommerce-0.1.0-standalone.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
