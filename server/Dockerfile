FROM golang:1.23-alpine AS build

WORKDIR /src
COPY go.mod ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /out/planner-server ./cmd/server
RUN CGO_ENABLED=0 go build -trimpath -ldflags='-s -w' -o /out/planner-backup ./cmd/backup

FROM alpine:3.21
RUN adduser -D -u 10001 planner
WORKDIR /app
COPY --from=build /out/planner-server /app/planner-server
COPY --from=build /out/planner-backup /app/planner-backup
RUN mkdir -p /data /backups && chown -R planner:planner /data /backups /app
USER planner
EXPOSE 8080
ENTRYPOINT ["/app/planner-server"]
