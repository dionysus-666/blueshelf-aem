FROM node:20-alpine
WORKDIR /app
COPY package.json server.mjs any-parser.mjs dispatcher.any ./
RUN mkdir -p /var/cache/dispatcher
EXPOSE 8080
CMD ["node", "server.mjs"]
