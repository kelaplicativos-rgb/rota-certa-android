FROM node:22-alpine
WORKDIR /app
COPY tools/xml-online-reader/server.mjs ./server.mjs
ENV NODE_ENV=production
EXPOSE 8080
CMD ["node", "server.mjs"]
