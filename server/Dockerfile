FROM golang:1.23-alpine AS build
WORKDIR /src
COPY go.mod go.sum* ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/relay .

FROM gcr.io/distroless/static-debian12
COPY --from=build /out/relay /relay
EXPOSE 8080
USER nonroot:nonroot
ENTRYPOINT ["/relay"]
