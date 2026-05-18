# Stage 1: build static binary
FROM golang:1.23-alpine AS build

WORKDIR /src

# Cache modules.
COPY go.mod go.sum ./
RUN go mod download

COPY cmd ./cmd
COPY internal ./internal

# Build dengan CGO=0 supaya hasilnya benar-benar static — tidak butuh libc di
# runtime image. -trimpath + -ldflags=-s -w supaya binary lebih kecil dan
# tidak bocorin path build host.
ENV CGO_ENABLED=0 GOOS=linux GOARCH=amd64
RUN go build -trimpath -ldflags="-s -w" -o /out/cekpelunasan ./cmd/cekpelunasan


# Stage 2: runtime — distroless static, no shell, no libc.
# wkhtmltopdf BELUM dipasang di sini karena modul SLIK belum diport.
# Saat SLIK PDF siap, ganti base image ke alpine + apk add wkhtmltopdf.
FROM gcr.io/distroless/static-debian12:nonroot

WORKDIR /app

COPY --from=build /out/cekpelunasan /app/cekpelunasan
COPY web/static /app/web/static

EXPOSE 8080

USER nonroot:nonroot

ENTRYPOINT ["/app/cekpelunasan"]
