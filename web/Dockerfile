FROM node:20-alpine AS builder
WORKDIR /app

# Install pnpm
RUN npm install -g pnpm@10

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm build

FROM node:20-alpine
WORKDIR /app

ENV NODE_ENV=production

# Install all dependencies needed for production
RUN npm install -g pnpm@10 && \
    pnpm install --frozen-lockfile --prod=false

COPY --from=builder /app/.output .output
COPY --from=builder /app/package.json ./

EXPOSE 3000
CMD ["node", ".output/server/index.mjs"]
