FROM mcr.microsoft.com/playwright:v1.54.2-noble
WORKDIR /app
COPY tools/blablacar-public-search/package.json ./tools/blablacar-public-search/package.json
RUN cd tools/blablacar-public-search && npm install --omit=dev --no-audit --no-fund
COPY tools/blablacar-public-search ./tools/blablacar-public-search
COPY collector/places ./collector/places
ENV NODE_ENV=production
ENV PORT=8080
CMD ["node", "tools/blablacar-public-search/profile-month-service.mjs"]
