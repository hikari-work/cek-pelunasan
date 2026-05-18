.PHONY: build run test lint tidy clean

BIN := bin/cekpelunasan
PKG := ./cmd/cekpelunasan

build:
	@mkdir -p bin
	go build -o $(BIN) $(PKG)

run:
	go run $(PKG)

test:
	go test ./...

lint:
	golangci-lint run

tidy:
	go mod tidy

clean:
	rm -rf bin
