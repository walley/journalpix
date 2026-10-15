# Build stage
FROM rust:1.70-slim as builder

WORKDIR /app

# Copy cargo files first for caching
COPY Cargo.toml Cargo.lock ./
RUN cargo build --release

# Copy source code
COPY src ./src
COPY .env.example .env

# Final build
RUN cargo build --release

# Runtime stage
FROM debian:bullseye-slim

WORKDIR /app

# Install dependencies
RUN apt-get update && apt-get install -y \
    libpq5 \
    && rm -rf /var/lib/apt/lists/*

# Copy built binary
COPY --from=builder /app/target/release/journalpix-backend .

# Copy environment file
COPY .env .

# Create upload directories
RUN mkdir -p uploads thumbnails

# Expose port
EXPOSE 8080

# Run the application
CMD ["./journalpix-backend"]
