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


# Stage 2: runtime — alpine + wkhtmltopdf untuk generator PDF SLIK.
# wkhtmltopdf butuh libstdc++, fontconfig, ttf untuk render font, dan
# libxrender/libxext untuk X11 dummy yang dipakai Qt 4 di balik wkhtmltopdf.
FROM alpine:3.20

WORKDIR /app

RUN apk add --no-cache \
    ca-certificates \
    wkhtmltopdf \
    ttf-dejavu \
    fontconfig \
    libstdc++ \
    libgcc \
 && update-ca-certificates

COPY --from=build /out/cekpelunasan /app/cekpelunasan
COPY web/static /app/web/static

EXPOSE 8080

# Non-root supaya container tidak punya hak tulis ke filesystem image.
RUN adduser -D -u 1000 app && chown -R app:app /app
USER app

ENTRYPOINT ["/app/cekpelunasan"]
